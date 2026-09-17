package com.n1jika.myvia.ui.glass

import android.os.Build

/**
 * 玻璃效果能力分级：业务代码只问"能不能"，不关心版本号。
 *
 * - [FULL]  Android 13+ (API 33)：AGSL 真折射 + 边缘高光 + 流光
 * - [BLUR]  Android 12  (API 31)：只能做背景模糊玻璃，无折射
 * - [FROST] Android 11 及以下：半透明磨砂 + 描边
 */
enum class GlassTier { FULL, BLUR, FROST }

object GlassCapability {

    val tier: GlassTier by lazy {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> GlassTier.FULL
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> GlassTier.BLUR
            else -> GlassTier.FROST
        }
    }

    /** 是否能使用 AGSL 着色器（折射与光效的真实来源）。 */
    val supportsShader: Boolean get() = tier == GlassTier.FULL

    /** 是否支持背景模糊（Android 12+ 的 RenderEffect / 我们自己实现的快速模糊都能用）。 */
    val supportsBlur: Boolean get() = tier != GlassTier.FROST
}
