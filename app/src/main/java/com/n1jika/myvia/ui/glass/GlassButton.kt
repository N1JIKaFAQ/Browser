package com.n1jika.myvia.ui.glass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import com.n1jika.myvia.ui.motion.Haptics
import com.n1jika.myvia.ui.motion.Kind
import com.n1jika.myvia.ui.motion.Springs
import com.n1jika.myvia.ui.motion.rememberHaptics

/**
 * 液态玻璃按钮。
 *
 * 按下缩到 [pressScale]，松手用低阻尼弹簧回到原位——弹簧过冲带来的
 * 轻微"弹一下"正是要的手感，不需要额外写过冲关键帧。
 */
@Composable
fun GlassButton(
    onClick: () -> Unit,
    backdrop: BackdropSource?,
    modifier: Modifier = Modifier,
    size: Dp = GlassTokens.menuButtonSize,
    pressScale: Float = 0.9f,
    rimProgress: Float? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptic = rememberHaptics()

    val scale by animateFloatAsState(
        targetValue = if (pressed) pressScale else 1f,
        animationSpec = Springs.bouncy,
        label = "glassButtonScale",
    )

    LaunchedEffect(pressed) {
        if (pressed) haptic(Kind.Tick)
    }

    GlassSurface(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin.Center
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        backdrop = backdrop,
        cornerRadius = size / 2,
        rimProgress = rimProgress,
    ) {
        Box(content = content)
    }
}

/** 三条横线（汉堡）图标，纯手绘以便后续做形变动画。黑色、不发光。 */
@Composable
fun HamburgerIcon(
    modifier: Modifier = Modifier,
    lineWidthFraction: Float = 0.42f,
    color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color(0xFF15171C),
) {
    androidx.compose.foundation.Canvas(modifier = modifier.size(GlassTokens.menuButtonSize)) {
        val w = size.width * lineWidthFraction
        val thickness = size.width * 0.055f
        val gap = size.height * 0.105f
        val cx = size.width / 2f
        val cy = size.height / 2f
        val left = cx - w / 2f
        val right = cx + w / 2f
        for (i in -1..1) {
            val y = cy + i * gap
            drawLine(
                color = color,
                start = androidx.compose.ui.geometry.Offset(left, y),
                end = androidx.compose.ui.geometry.Offset(right, y),
                strokeWidth = thickness,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
        }
    }
}
