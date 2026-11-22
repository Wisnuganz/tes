package com.example.overlay

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.session.AiAssistantSession
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.lang.ref.WeakReference
import kotlin.coroutines.resume

private const val TAG = "CerdasAccessibilitySvc"

data class UiTargetNode(
    val id: Int,
    val label: String,
    val optionLetter: String?,
    val centerX: Float,
    val centerY: Float,
    val bounds: Rect,
    val isNextButton: Boolean = false
)

data class ScreenQuizSnapshot(
    val screenWidth: Int,
    val screenHeight: Int,
    val combinedText: String,
    val questionFingerprint: String,
    val questionNumberLabel: String,
    val optionNodes: List<UiTargetNode>,
    val nextButtonNode: UiTargetNode?
)

class CerdasAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        private var instanceRef: WeakReference<CerdasAccessibilityService>? = null

        fun isActive(): Boolean = instanceRef?.get() != null

        fun isServiceEnabledInSettings(context: Context): Boolean {
            if (isActive()) return true
            return try {
                val expectedComponent = ComponentName(context, CerdasAccessibilityService::class.java)
                val enabledServices = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ) ?: return false
                val splitter = TextUtils.SimpleStringSplitter(':')
                splitter.setString(enabledServices)
                while (splitter.hasNext()) {
                    val componentStr = splitter.next()
                    val enabledComponent = ComponentName.unflattenFromString(componentStr)
                    if (enabledComponent == expectedComponent) {
                        return true
                    }
                }
                false
            } catch (_: Exception) {
                false
            }
        }

        fun captureSnapshot(): ScreenQuizSnapshot? = instanceRef?.get()?.extractQuizSnapshot()

        suspend fun tapPoint(x: Float, y: Float): Boolean {
            val svc = instanceRef?.get() ?: return false
            return svc.dispatchSingleTap(x, y)
        }

        suspend fun tapOptionOrPoint(
            optionNode: UiTargetNode?,
            fallbackX: Float?,
            fallbackY: Float?
        ): Boolean {
            val svc = instanceRef?.get() ?: return false
            if (optionNode != null) {
                if (svc.dispatchSingleTap(optionNode.centerX, optionNode.centerY)) {
                    return true
                }
                if (svc.tryNodeClick(optionNode)) {
                    return true
                }
            }
            if (fallbackX != null && fallbackY != null) {
                return svc.dispatchSingleTap(fallbackX, fallbackY)
            }
            return false
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instanceRef = WeakReference(this)
        AiAssistantSession.setAccessibilityEnabled(true)
        Log.i(TAG, "CerdasAccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Passive listener; snapshots are pulled cleanly on-demand when Jawab 1 Soal / Auto runs.
    }

    override fun onInterrupt() {
        Log.w(TAG, "CerdasAccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instanceRef?.get() === this) {
            instanceRef = null
        }
        AiAssistantSession.setAccessibilityEnabled(false)
    }

    private fun extractQuizSnapshot(): ScreenQuizSnapshot? {
        val metrics = resources.displayMetrics
        val screenW = metrics.widthPixels.coerceAtLeast(1)
        val screenH = metrics.heightPixels.coerceAtLeast(1)

        val candidateRoots = mutableListOf<AccessibilityNodeInfo>()
        try {
            val windowList = windows
            for (win in windowList) {
                val root = win.root ?: continue
                val pkg = root.packageName?.toString().orEmpty()
                if (pkg == packageName || pkg == "com.android.systemui") {
                    continue
                }
                candidateRoots.add(root)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed iterating windows, falling back to rootInActiveWindow", e)
        }

        if (candidateRoots.isEmpty()) {
            val activeRoot = rootInActiveWindow
            if (activeRoot != null && activeRoot.packageName?.toString() != packageName) {
                candidateRoots.add(activeRoot)
            }
        }

        if (candidateRoots.isEmpty()) return null

        val textLines = linkedSetOf<String>()
        val optionCandidates = mutableListOf<UiTargetNode>()
        var nextBtnCandidate: UiTargetNode? = null
        var nextId = 1

        val seenBounds = hashSetOf<String>()

        fun traverse(node: AccessibilityNodeInfo?, parentClickable: Boolean, depth: Int) {
            if (node == null || depth > 45 || !node.isVisibleToUser) return

            val ownText = node.text?.toString()?.trim().orEmpty()
            val descText = node.contentDescription?.toString()?.trim().orEmpty()
            val text = ownText.ifBlank { descText }

            val rect = Rect()
            node.getBoundsInScreen(rect)
            val validBounds = rect.width() > 20 && rect.height() > 20 &&
                rect.centerX() in 1 until screenW &&
                rect.centerY() in 1 until screenH

            if (text.isNotBlank() && validBounds) {
                textLines.add(text)
                val normalized = text.lowercase()
                val boundsKey = "${rect.left}:${rect.top}:${rect.right}:${rect.bottom}"

                val isClickableHere = node.isClickable || parentClickable ||
                    node.className?.toString()?.contains("RadioButton", ignoreCase = true) == true ||
                    node.className?.toString()?.contains("Button", ignoreCase = true) == true ||
                    node.className?.toString()?.contains("CompoundButton", ignoreCase = true) == true

                val isNextNav = isLikelyNextButton(normalized) &&
                    !isDangerousFinishButton(normalized) &&
                    isClickableHere

                if (isNextNav && nextBtnCandidate == null) {
                    nextBtnCandidate = UiTargetNode(
                        id = nextId++,
                        label = text,
                        optionLetter = null,
                        centerX = rect.exactCenterX(),
                        centerY = rect.exactCenterY(),
                        bounds = rect,
                        isNextButton = true
                    )
                } else if (!isDangerousFinishButton(normalized) && !isLikelyPreviousButton(normalized)) {
                    val letter = parseOptionLetter(text)
                    val looksLikeOptionRow = letter != null ||
                        (isClickableHere && text.length in 1..180 && rect.centerY() > screenH * 0.22f && rect.centerY() < screenH * 0.86f)
                    if (looksLikeOptionRow && seenBounds.add(boundsKey)) {
                        optionCandidates.add(
                            UiTargetNode(
                                id = nextId++,
                                label = text,
                                optionLetter = letter,
                                centerX = rect.exactCenterX(),
                                centerY = rect.exactCenterY(),
                                bounds = rect
                            )
                        )
                    }
                }
            }

            val passClickable = node.isClickable || parentClickable
            for (i in 0 until node.childCount) {
                traverse(node.getChild(i), passClickable, depth + 1)
            }
        }

        for (root in candidateRoots) {
            traverse(root, parentClickable = false, depth = 0)
        }

        val combined = textLines.joinToString("\n")
        if (combined.isBlank() && optionCandidates.isEmpty()) {
            return null
        }

        val questionNumber = detectQuestionNumberLabel(textLines)
        val fingerprint = (questionNumber + "|" + combined.take(220)).hashCode().toString()

        // Prefer nodes with explicit A-E letters if present, otherwise keep top vertical candidates
        val prioritizedOptions = optionCandidates
            .sortedBy { it.centerY }
            .distinctBy { "${it.optionLetter ?: ""}_${it.label}" }
            .take(8)

        return ScreenQuizSnapshot(
            screenWidth = screenW,
            screenHeight = screenH,
            combinedText = combined,
            questionFingerprint = fingerprint,
            questionNumberLabel = questionNumber,
            optionNodes = prioritizedOptions,
            nextButtonNode = nextBtnCandidate
        )
    }

    private fun parseOptionLetter(raw: String): String? {
        val trimmed = raw.trim()
        val match = Regex("^([A-Ea-e])[\\.)\\]:\\s]+(.+)?$").find(trimmed)
        if (match != null) {
            return match.groupValues[1].uppercase()
        }
        if (trimmed.length == 1 && trimmed[0].uppercaseChar() in 'A'..'E') {
            return trimmed.uppercase()
        }
        return null
    }

    private fun isLikelyNextButton(lower: String): Boolean {
        return lower.contains("soal selanjutnya") ||
            lower.contains("selanjutnya") ||
            lower.contains("berikutnya") ||
            lower == "next" ||
            lower.startsWith("next ") ||
            lower.endsWith(" next") ||
            lower == "lanjut"
    }

    private fun isLikelyPreviousButton(lower: String): Boolean {
        return lower.contains("sebelumnya") || lower == "prev" || lower == "previous" || lower == "kembali"
    }

    private fun isDangerousFinishButton(lower: String): Boolean {
        return lower.contains("selesai") ||
            lower.contains("kumpulkan") ||
            lower.contains("submit") ||
            lower.contains("finish") ||
            lower.contains("keluar")
    }

    private fun detectQuestionNumberLabel(lines: Collection<String>): String {
        for (line in lines) {
            val m1 = Regex("(?i)(?:soal\\s*(?:nomor|no\\.?)|nomor|no\\.?)\\s*(\\d{1,3})").find(line)
            if (m1 != null) {
                return "Soal No. ${m1.groupValues[1]}"
            }
            val m2 = Regex("(?i)terjawab\\s*[:：]?\\s*(\\d+/\\d+)").find(line)
            if (m2 != null) {
                return "Progress ${m2.groupValues[1]}"
            }
        }
        return ""
    }

    private suspend fun dispatchSingleTap(x: Float, y: Float): Boolean {
        val safeX = x.coerceIn(2f, (resources.displayMetrics.widthPixels - 2).toFloat())
        val safeY = y.coerceIn(2f, (resources.displayMetrics.heightPixels - 2).toFloat())
        val path = Path().apply { moveTo(safeX, safeY) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 65L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        return withTimeoutOrNull(1800L) {
            suspendCancellableCoroutine { cont ->
                val dispatched = dispatchGesture(
                    gesture,
                    object : GestureResultCallback() {
                        override fun onCompleted(gestureDescription: GestureDescription?) {
                            if (cont.isActive) cont.resume(true)
                        }

                        override fun onCancelled(gestureDescription: GestureDescription?) {
                            if (cont.isActive) cont.resume(false)
                        }
                    },
                    null
                )
                if (!dispatched && cont.isActive) {
                    cont.resume(false)
                }
            }
        } ?: false
    }

    private fun tryNodeClick(target: UiTargetNode): Boolean {
        val root = rootInActiveWindow ?: return false
        fun searchAndClick(node: AccessibilityNodeInfo?, depth: Int): Boolean {
            if (node == null || depth > 40) return false
            val rect = Rect()
            node.getBoundsInScreen(rect)
            if (rect == target.bounds && node.isClickable) {
                return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            for (i in 0 until node.childCount) {
                if (searchAndClick(node.getChild(i), depth + 1)) return true
            }
            return false
        }
        return searchAndClick(root, 0)
    }
}
