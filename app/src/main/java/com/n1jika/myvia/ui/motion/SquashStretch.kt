package com.n1jika.myvia.ui.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 碰撞形变（squash & stretch）。
 *
 * 两个元素"碰上"的瞬间，横向挤压、纵向拉伸，再用低阻尼弹簧回弹归位——
 * 这是让动画有"重量"的关键。数值很小才自然：横向 2~3%、纵向约 1~2%。
 */
@Stable
class ImpactState internal constructor(
    private val scope: CoroutineScope,
) {
    internal val amount = Animatable(0f)

    /** 触发一次碰撞形变。[strength] 建议 0.02~0.035。 */
    fun impact(strength: Float = 0.028f) {
        scope.launch {
            amount.snapTo(strength)
            amount.animateTo(0f, Springs.bouncy)
        }
    }
}

@Composable
fun rememberImpact(): ImpactState {
    val scope = rememberCoroutineScope()
    return remember(scope) { ImpactState(scope) }
}

/**
 * 把形变量作用到布局上：横向放大 [squash]，纵向按泊松比收缩，
 * 体积近似守恒，形变看起来才是"软的"而不是"被拉伸的图"。
 */
fun Modifier.squashStretch(
    squash: Float,
    pivot: TransformOrigin = TransformOrigin.Center,
): Modifier = graphicsLayer {
    scaleX = 1f + squash
    scaleY = 1f - squash * PoissonRatio
    transformOrigin = pivot
}

private const val PoissonRatio = 0.62f

/** 便捷：把 [ImpactState] 直接接到 Modifier 上。 */
@Composable
fun Modifier.impactEffect(
    state: ImpactState,
    pivot: TransformOrigin = TransformOrigin.Center,
): Modifier {
    val squash by state.amount.asState()
    return this.squashStretch(squash, pivot)
}
