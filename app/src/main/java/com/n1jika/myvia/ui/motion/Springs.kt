package com.n1jika.myvia.ui.motion

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset

/**
 * 全应用统一的弹簧参数。要改"手感"只改这里。
 *
 * - [gentle]  日常位移，收得住、不颤
 * - [snappy]  点击/展开，快而干脆
 * - [bouncy]  弹跳，用于按钮按下与元素碰撞回弹
 * - [settle]  临界阻尼，用于需要"稳稳停住"的场景
 */
object Springs {
    val gentle: SpringSpec<Float> = spring(dampingRatio = 0.86f, stiffness = 260f)
    val snappy: SpringSpec<Float> = spring(dampingRatio = 0.72f, stiffness = 700f)
    val bouncy: SpringSpec<Float> = spring(dampingRatio = 0.5f, stiffness = 680f)
    val settle: SpringSpec<Float> = spring(dampingRatio = 1f, stiffness = 240f)

    val gentleDp: SpringSpec<Dp> = spring(dampingRatio = 0.86f, stiffness = 260f)
    val bouncyDp: SpringSpec<Dp> = spring(dampingRatio = 0.5f, stiffness = 520f)
    val snappyDp: SpringSpec<Dp> = spring(dampingRatio = 0.72f, stiffness = 700f)

    val gentleOffset: SpringSpec<IntOffset> = spring(dampingRatio = 0.86f, stiffness = 260f)
    val snappyOffset: SpringSpec<IntOffset> = spring(dampingRatio = 0.72f, stiffness = 700f)

    /** 地址栏/菜单键在主页与浏览态之间飞行：带一点回弹，更灵动 */
    val flight: SpringSpec<Rect> = spring(dampingRatio = 0.7f, stiffness = 440f)
}
