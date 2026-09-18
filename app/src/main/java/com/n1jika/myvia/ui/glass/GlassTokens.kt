package com.n1jika.myvia.ui.glass

import androidx.compose.ui.unit.dp

/**
 * 液态玻璃的全部视觉参数。
 *
 * 术语与取值对照 Cocos 帖子《液态玻璃原理及其高性能实现》(forum.cocos.org/raw/171941)：
 *  - 位移向量场：RG = 128 + Normal*127，中心位移归零、边缘最大
 *  - 折射：offsetUV = offsetNormal * strengthUV（原文默认 0.03）
 *  - 色散：R/B 通道沿 offsetUV 左右各偏移 dispersionStrength（原文默认 0.03）
 *  - 轮廓光：位移矢量长度 len > 1 - highlightStrength 才出现，
 *    再用位移矢量 y 分量做线性渐变（原文左半亮心 G=191/255、右半 G=64/255、跨度 ±50/255），
 *    最后 mix(颜色, 白, opacity) —— **纯 mix，没有加法，所以不会自发光/泛光**
 *
 * 观感调整基本只改这个文件。
 */
object GlassTokens {

    // ---------- 形状 ----------
    val searchBarHeight = 52.dp
    val searchBarCorner = 26.dp

    val addressBarHeight = 42.dp
    val addressBarCorner = 21.dp

    val menuButtonSize = 44.dp

    val menuWidth = 220.dp
    val menuItemHeight = 52.dp
    val menuCorner = 24.dp
    /** 菜单可视区固定显示 4 项，超出滚动 */
    const val menuVisibleItems = 4

    // ---------- 折射与色散（帖子公式） ----------
    /** 折射带宽度：占玻璃短边的比例（带宽内位移从 0 增长到最大） */
    const val refractionBandFraction = 0.34f

    /** strengthUV：位移强度，占玻璃短边的比例（原帖是画布的 0.03） */
    const val strengthUV = 0.13f

    /** dispersionStrength：R/B 通道的额外偏移比例（原帖默认 0.03） */
    const val dispersionStrength = 0.045f

    // ---------- 轮廓光（帖子 3.3） ----------
    /** 位移长度阈值：len > 1 - highlightStrength 的像素才可能亮（原帖默认 0.1） */
    const val highlightStrength = 0.10f
    /** 左半轮廓光亮心（原帖 191/255） */
    const val highlightLeftCenter = 0.749f
    /** 右半轮廓光亮心（原帖 64/255） */
    const val highlightRightCenter = 0.251f
    /** 轮廓光渐变跨度（原帖 50/255） */
    const val highlightSpan = 0.196f
    /** 轮廓光最大不透明度 */
    const val highlightOpacity = 0.85f

    // ---------- 玻璃本体（霜化，不是发光） ----------
    /**
     * 玻璃本体的霜化程度：背景亮时用低值（黑字清晰、折射明显），
     * 背景暗时提高，避免黑字看不清。由 [com.n1jika.myvia.ui.glass.BackdropSource.luminance] 插值。
     */
    const val frostOnLight = 0.16f
    const val frostOnDark = 0.55f

    /** 菜单面板的最小霜化：条目背后压着网页正文，需要更实的底才读得清 */
    const val menuMinFrost = 0.34f

    /**
     * 按背景明度取霜化程度：背景越暗，玻璃越"厚"（越白），
     * 这样黑色文字/图标在任何背景图上都能看清。
     */
    fun frostFor(luminance: Float): Float {
        val t = luminance.coerceIn(0f, 1f)
        return frostOnDark + (frostOnLight - frostOnDark) * t
    }

    /** 边缘描边：1px，方向性（上亮下弱） */
    const val borderTopAlpha = 0.55f
    const val borderBottomAlpha = 0.10f

    // ---------- 流光（加载指示） ----------
    const val rimPeriodMs = 1600
    /** 尾部锐度，越小越锐 */
    const val rimTail = 10f
    /** 流光不透明度（中性白，与轮廓光同级，不额外泛光） */
    const val rimOpacity = 0.9f

    // ---------- 背景 ----------
    /** 玻璃采样用的背景降采样倍率 */
    const val backdropDownscale = 4f
    /** 背景模糊半径（屏幕像素；折射负责细节，模糊只负责霜感） */
    const val backdropBlurRadius = 5f
    /** 搜索框聚焦时全屏暗化 */
    const val focusDim = 0.10f
    /** 搜索框聚焦时背景额外模糊倍率 */
    const val focusExtraBlur = 1.5f
}
