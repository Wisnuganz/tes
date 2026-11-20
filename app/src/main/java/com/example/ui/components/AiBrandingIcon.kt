package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.StealthMode
import com.example.ui.theme.AiCardBorder
import com.example.ui.theme.AiEmerald
import com.example.ui.theme.AiEmeraldBright
import com.example.ui.theme.AiPanelBg
import com.example.ui.theme.AiTextPrimary
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun AiKnotOrbIcon(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    tint: Color = Color.White
) {
    Canvas(modifier = modifier.size(size)) {
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f
        val outerR = this.size.minDimension * 0.25f
        val ringRadius = this.size.minDimension * 0.16f
        val strokeWidth = this.size.minDimension * 0.085f

        for (i in 0 until 6) {
            val angle = (i * 60.0) * PI / 180.0
            val px = cx + (outerR * cos(angle)).toFloat()
            val py = cy + (outerR * sin(angle)).toFloat()
            drawCircle(
                color = tint,
                radius = ringRadius,
                center = Offset(px, py),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
        drawCircle(
            color = tint,
            radius = strokeWidth * 0.7f,
            center = Offset(cx, cy)
        )
    }
}

@Composable
fun DraggableFloatingAiBubble(
    stealthMode: StealthMode,
    isPanelOpen: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val targetAlpha = when {
        isPanelOpen -> 0f
        else -> stealthMode.bubbleAlpha
    }
    val animatedAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = spring(),
        label = "bubbleAlpha"
    )

    if (isPanelOpen) return

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        val density = LocalDensity.current
        val bubbleSize = 58.dp
        val bubblePx = with(density) { bubbleSize.toPx() }
        val maxWidthPx = with(density) { maxWidth.toPx() } - bubblePx
        val maxHeightPx = with(density) { maxHeight.toPx() } - bubblePx

        var initialized by remember { mutableStateOf(false) }
        var offsetX by remember { mutableFloatStateOf(0f) }
        var offsetY by remember { mutableFloatStateOf(0f) }

        if (!initialized && maxWidthPx > 0f && maxHeightPx > 0f) {
            offsetX = maxWidthPx
            offsetY = (maxHeightPx * 0.78f).coerceAtLeast(0f)
            initialized = true
        }

        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        offsetX.coerceIn(0f, maxWidthPx.coerceAtLeast(0f)).roundToInt(),
                        offsetY.coerceIn(0f, maxHeightPx.coerceAtLeast(0f)).roundToInt()
                    )
                }
                .size(bubbleSize)
                .minimumInteractiveComponentSize()
                .alpha(animatedAlpha)
                .then(
                    if (stealthMode == StealthMode.NORMAL) {
                        Modifier.shadow(
                            elevation = 10.dp,
                            shape = CircleShape,
                            ambientColor = AiEmerald,
                            spotColor = AiEmerald
                        )
                    } else {
                        Modifier
                    }
                )
                .clip(CircleShape)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(AiEmeraldBright, AiEmerald)
                    )
                )
                .then(
                    if (stealthMode != StealthMode.INVISIBLE) {
                        Modifier.border(1.5.dp, Color.White.copy(alpha = 0.28f), CircleShape)
                    } else {
                        Modifier
                    }
                )
                .pointerInput(maxWidthPx, maxHeightPx) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        offsetX = (offsetX + dragAmount.x).coerceIn(0f, maxWidthPx.coerceAtLeast(0f))
                        offsetY = (offsetY + dragAmount.y).coerceIn(0f, maxHeightPx.coerceAtLeast(0f))
                    }
                }
                .clickable(onClick = onClick)
                .testTag("floating_ai_bubble")
                .semantics { contentDescription = "Buka Asisten Cerdas AI" },
            contentAlignment = Alignment.Center
        ) {
            AiKnotOrbIcon(size = 30.dp, tint = Color.White)
        }
    }
}

@Composable
fun AiFloatingToastBanner(
    message: String?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(top = 64.dp, start = 20.dp, end = 20.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedVisibility(
            visible = !message.isNullOrBlank(),
            enter = fadeIn() + slideInVertically { -it / 2 },
            exit = fadeOut() + slideOutVertically { -it / 2 }
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = AiPanelBg,
                shadowElevation = 8.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, AiCardBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(AiEmerald),
                        contentAlignment = Alignment.Center
                    ) {
                        AiKnotOrbIcon(size = 12.dp, tint = Color.White)
                    }
                    Text(
                        text = message.orEmpty(),
                        color = AiTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
