package com.n1jika.myvia.ui.glass

import androidx.compose.ui.unit.dp

/**
 * 液态玻璃的全部视觉参数集中在这里，方便快速调参。
 * 观感调整基本只改这个文件，不用翻业务代码。
 */
object GlassTokens {
    // ---------- 形状 ----------
    /** 搜索框圆角（全圆角：高度的一半，视觉上是胶囊） */
    val searchBarHeight = 52.dp
    val searchBarCorner = 26.dp

    /** 浏览态地址栏 */
    val addressBarHeight = 42.dp
    val addressBarCorner = 21.dp

    /** 圆形菜单按钮 */
    val menuButtonSize = 44.dp

    /** 下拉菜单 */
    val menuWidth = 220.dp
    val menuItemHeight = 52.dp
    val menuCorner = 24.dp
    /** 菜单可视区固定显示 4 项，超出滚动 */
    const val menuVisibleItems = 4

    // ---------- 折射与高光 ----------
    /** 边缘折射带宽（dp）：只有这么宽的一条边参与弯折 */
    val refractionBand = 6.dp
    /** 折射强度：边缘采样点向外偏移的像素量 */
    const val refractionAmount = 9f
    /** 内部额外模糊抽样半径（px，作用于已模糊的背景图） */
    const val innerBlur = 1.6f

    /** 玻璃本体色调与透明度 */
    val tint = listOf(1f, 1f, 1f)
    const val tintAlpha = 0.10f

    /** 边缘高光（受光面） */
    val highlightColor = listOf(1f, 1f, 1f)
    const val highlightAlpha = 0.85f

    /** 描边：极细的玻璃边界 */
    const val borderAlpha = 0.22f
    const val borderWidthDp = 0.8f

    // ---------- 阴影 ----------
    const val shadowAlpha = 0.18f
    val shadowRadius = 18.dp
    val shadowOffsetY = 8.dp

    // ---------- 流光 ----------
    /** 流光绕行一周的时长 */
    const val rimPeriodMs = 1600
    /** 流光颜色，默认取品牌青绿偏白 */
    val rimColor = listOf(0.72f, 1f, 0.96f)
    /** 流光尾部长度（越小越锐利） */
    const val rimTail = 12f

    // ---------- 背景 ----------
    /** 背景模糊的降采样倍率：越大越快越糊 */
    const val backdropDownscale = 8f
    /** 高斯模糊半径（作用在降采样图上） */
    const val backdropBlurRadius = 14f
    /** 搜索框聚焦时全屏背景的暗化程度 */
    const val focusDim = 0.12f
    /** 搜索框聚焦时背景的额外模糊倍率 */
    const val focusExtraBlur = 1.6f
}
