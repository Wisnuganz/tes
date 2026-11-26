package com.example.overlay

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
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

        suspend fun scrollDownSlightly(): Boolean {
            val svc = instanceRef?.get() ?: return false
            return svc.dispatchVerticalScroll()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instanceRef = WeakReference(this)
        AiAssistantSession.setAccessibilityEnabled(true)
        Log.i(TAG, "CerdasAccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Snapshots are pulled on demand so background UI changes consume zero battery.
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

    private fun resolveRealScreenSize(): Pair<Int, Int> {
        return try {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bounds = wm.maximumWindowMetrics.bounds
                bounds.width().coerceAtLeast(1) to bounds.height().coerceAtLeast(1)
            } else {
                val metrics = DisplayMetrics()
                @Suppress("DEPRECATION")
                wm.defaultDisplay.getRealMetrics(metrics)
                metrics.widthPixels.coerceAtLeast(1) to metrics.heightPixels.coerceAtLeast(1)
            }
        } catch (_: Exception) {
            val dm = resources.displayMetrics
            dm.widthPixels.coerceAtLeast(1) to dm.heightPixels.coerceAtLeast(1)
        }
    }

    private fun collectTargetWindowRoots(): List<AccessibilityNodeInfo> {
        val roots = mutableListOf<AccessibilityNodeInfo>()
        val seenHashes = hashSetOf<Int>()

        fun addIfValid(node: AccessibilityNodeInfo?) {
            if (node == null) return
            val pkg = node.packageName?.toString().orEmpty()
            if (pkg == packageName || pkg == "com.android.systemui" || pkg == "com.android.launcher3") {
                return
            }
            val idHash = System.identityHashCode(node)
            if (seenHashes.add(idHash)) {
                roots.add(node)
            }
        }

        try {
            val appWindows = windows
                .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
                .sortedByDescending { (if (it.isActive) 2 else 0) + (if (it.isFocused) 1 else 0) }
            for (win in appWindows) {
                addIfValid(win.root)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed iterating windows, falling back to rootInActiveWindow", e)
        }

        if (roots.isEmpty()) {
            addIfValid(rootInActiveWindow)
        }
        return roots
    }

    private fun extractQuizSnapshot(): ScreenQuizSnapshot? {
        val (screenW, screenH) = resolveRealScreenSize()
        val candidateRoots = collectTargetWindowRoots()
        if (candidateRoots.isEmpty()) return null

        val textLines = linkedSetOf<String>()
        val optionCandidates = mutableListOf<UiTargetNode>()
        var nextBtnCandidate: UiTargetNode? = null
        var nextId = 1
        val seenBounds = hashSetOf<String>()

        fun normalizeWhitespace(raw: String): String =
            raw.replace(Regex("\\s+"), " ").trim()

        fun nodeTextOf(node: AccessibilityNodeInfo): String {
            val own = node.text?.toString()?.let(::normalizeWhitespace).orEmpty()
            if (own.isNotBlank()) return own
            val desc = node.contentDescription?.toString()?.let(::normalizeWhitespace).orEmpty()
            if (desc.isNotBlank()) return desc
            return ""
        }

        fun collectLeafTexts(node: AccessibilityNodeInfo?, depth: Int, out: MutableList<String>) {
            if (node == null || depth > 10 || out.size >= 10) return
            val txt = nodeTextOf(node)
            if (txt.isNotBlank() && txt !in out) {
                out.add(txt)
            }
            for (i in 0 until node.childCount) {
                collectLeafTexts(node.getChild(i), depth + 1, out)
            }
        }

        var visitedNodes = 0
        val maxVisitedNodes = 650

        fun traverse(node: AccessibilityNodeInfo?, parentClickable: Boolean, depth: Int) {
            if (node == null || depth > 110 || visitedNodes >= maxVisitedNodes || !node.isVisibleToUser) return
            visitedNodes++

            val rect = Rect()
            node.getBoundsInScreen(rect)
            val validBounds = rect.width() > 20 && rect.height() > 20 &&
                rect.centerX() in 1 until screenW &&
                rect.centerY() in 1 until screenH

            val directText = nodeTextOf(node)
            val className = node.className?.toString().orEmpty()
            val isInteractiveElement = node.isClickable ||
                className.contains("RadioButton", ignoreCase = true) ||
                className.contains("Button", ignoreCase = true) ||
                className.contains("CompoundButton", ignoreCase = true)

            // Only aggregate a clickable container as a single card/button if it is card/button-sized
            // (never swallow a whole WebView/RecyclerView wrapper!).
            val isButtonOrCardSized = validBounds &&
                rect.height() in 32..(screenH * 0.30f).toInt() &&
                rect.width() <= (screenW * 0.99f).toInt()

            if (isButtonOrCardSized && isInteractiveElement && directText.isBlank() && node.childCount in 1..12) {
                val childTexts = mutableListOf<String>()
                collectLeafTexts(node, 0, childTexts)
                val merged = childTexts.joinToString(" ").let(::normalizeWhitespace)
                if (merged.isNotBlank() && merged.length <= 220) {
                    val lowerMerged = merged.lowercase()
                    val boundsKey = "${rect.left}:${rect.top}:${rect.right}:${rect.bottom}"
                    textLines.add(merged)

                    if (isLikelyNextButton(lowerMerged) && !isDangerousFinishButton(lowerMerged)) {
                        nextBtnCandidate = UiTargetNode(
                            id = nextId++,
                            label = merged,
                            optionLetter = null,
                            centerX = rect.exactCenterX(),
                            centerY = rect.exactCenterY(),
                            bounds = rect,
                            isNextButton = true
                        )
                        return
                    } else if (!isDangerousFinishButton(lowerMerged) && !isLikelyPreviousButton(lowerMerged)) {
                        val letter = parseOptionLetter(merged)
                        val looksLikeOptionCard = letter != null ||
                            (rect.centerY() > screenH * 0.15f && rect.centerY() < screenH * 0.96f)
                        if (looksLikeOptionCard && seenBounds.add(boundsKey)) {
                            optionCandidates.add(
                                UiTargetNode(
                                    id = nextId++,
                                    label = merged,
                                    optionLetter = letter,
                                    centerX = rect.exactCenterX(),
                                    centerY = rect.exactCenterY(),
                                    bounds = rect
                                )
                            )
                            return
                        }
                    }
                }
            }

            if (directText.isNotBlank() && validBounds) {
                textLines.add(directText)
                val normalized = directText.lowercase()
                val boundsKey = "${rect.left}:${rect.top}:${rect.right}:${rect.bottom}"
                val isClickableHere = isInteractiveElement || parentClickable

                val isNextNav = isLikelyNextButton(normalized) &&
                    !isDangerousFinishButton(normalized) &&
                    !isLikelyPreviousButton(normalized)

                if (isNextNav) {
                    if (nextBtnCandidate == null || isClickableHere || rect.centerY() > (nextBtnCandidate?.centerY ?: 0f)) {
                        nextBtnCandidate = UiTargetNode(
                            id = nextId++,
                            label = directText,
                            optionLetter = null,
                            centerX = rect.exactCenterX(),
                            centerY = rect.exactCenterY(),
                            bounds = rect,
                            isNextButton = true
                        )
                    }
                } else if (!isDangerousFinishButton(normalized) && !isLikelyPreviousButton(normalized)) {
                    val letter = parseOptionLetter(directText)
                    val looksLikeOptionRow = letter != null ||
                        (isClickableHere &&
                            rect.height() <= (screenH * 0.30f).toInt() &&
                            directText.length in 1..220 &&
                            rect.centerY() > screenH * 0.15f &&
                            rect.centerY() < screenH * 0.96f)
                    if (looksLikeOptionRow && seenBounds.add(boundsKey)) {
                        optionCandidates.add(
                            UiTargetNode(
                                id = nextId++,
                                label = directText,
                                optionLetter = letter,
                                centerX = rect.exactCenterX(),
                                centerY = rect.exactCenterY(),
                                bounds = rect
                            )
                        )
                    }
                }
            }

            // Do not propagate parentClickable from giant root/WebView containers
            val passClickable = (isInteractiveElement && rect.height() < screenH * 0.35f) || parentClickable
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
        val fingerprint = (questionNumber + "|" + combined.take(260)).hashCode().toString()

        val prioritizedOptions = optionCandidates
            .sortedWith(compareBy<UiTargetNode> { it.optionLetter == null }.thenBy { it.centerY }.thenBy { it.centerX })
            .distinctBy { "${it.optionLetter ?: ""}_${it.label}" }
            .take(10)

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
        if (lower.length > 48) return false
        return lower.contains("soal selanjutnya") ||
            lower.contains("soal berikutnya") ||
            lower.contains("pertanyaan berikutnya") ||
            lower.contains("selanjutnya") ||
            lower.contains("berikutnya") ||
            lower.contains("lanjutkan") ||
            lower == "next" ||
            lower == "next question" ||
            lower.startsWith("next ") ||
            lower.endsWith(" next") ||
            lower == "lanjut" ||
            lower.startsWith("lanjut ") ||
            lower == "berikut"
    }

    private fun isLikelyPreviousButton(lower: String): Boolean {
        return lower.contains("sebelumnya") || lower == "prev" || lower == "previous" || lower == "kembali"
    }

    private fun isDangerousFinishButton(lower: String): Boolean {
        return lower.contains("selesai") ||
            lower.contains("kumpulkan") ||
            lower.contains("submit") ||
            lower.contains("finish") ||
            lower.contains("akhiri") ||
            lower.contains("keluar")
    }

    private fun detectQuestionNumberLabel(lines: Collection<String>): String {
        for (line in lines) {
            val m1 = Regex("(?i)(?:soal\\s*(?:nomor|no\\.?)|nomor|no\\.?)\\s*(\\d{1,3})").find(line)
            if (m1 != null) {
                return "Soal No. ${m1.groupValues[1]}"
            }
            val m2 = Regex("(?i)(?:terjawab|question)\\s*[:：]?\\s*(\\d+/\\d+)").find(line)
            if (m2 != null) {
                return "Progress ${m2.groupValues[1]}"
            }
        }
        return ""
    }

    private suspend fun dispatchSingleTap(x: Float, y: Float): Boolean {
        val (realW, realH) = resolveRealScreenSize()
        val safeX = x.coerceIn(2f, (realW - 2).toFloat())
        val safeY = y.coerceIn(2f, (realH - 2).toFloat())
        val path = Path().apply { moveTo(safeX, safeY) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 70L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        return withTimeoutOrNull(1500L) {
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

    private suspend fun dispatchVerticalScroll(): Boolean {
        val (realW, realH) = resolveRealScreenSize()
        val cx = realW * 0.5f
        val startY = realH * 0.68f
        val endY = realH * 0.36f
        val path = Path().apply {
            moveTo(cx, startY)
            lineTo(cx, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 220L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        return withTimeoutOrNull(1500L) {
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
        val roots = collectTargetWindowRoots()
        var visited = 0
        fun searchAndClick(node: AccessibilityNodeInfo?, depth: Int): Boolean {
            if (node == null || depth > 110 || visited >= 650) return false
            visited++
            val rect = Rect()
            node.getBoundsInScreen(rect)
            if (rect == target.bounds) {
                var cur: AccessibilityNodeInfo? = node
                var hops = 0
                while (cur != null && hops < 4) {
                    if (cur.isClickable && cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        return true
                    }
                    cur = cur.parent
                    hops++
                }
            }
            for (i in 0 until node.childCount) {
                if (searchAndClick(node.getChild(i), depth + 1)) return true
            }
            return false
        }
        return roots.any { searchAndClick(it, 0) }
    }
}
