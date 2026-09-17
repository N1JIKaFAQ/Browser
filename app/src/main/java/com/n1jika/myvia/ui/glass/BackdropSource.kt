package com.n1jika.myvia.ui.glass

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * 玻璃所"透视"的背景来源。
 *
 * 为了性能，背景一律先降采样再模糊：位图尺寸只有屏幕的 1/[scale] 分之一。
 * [scale] = 位图像素 / 屏幕像素，玻璃按自身在屏幕上的位置换算采样坐标。
 */
@Immutable
class BackdropSource(
    val bitmap: Bitmap,
    /** 位图像素 / 屏幕像素 */
    val scale: Float,
) {
    val image: ImageBitmap by lazy { bitmap.asImageBitmap() }

    /** 把屏幕坐标换算成位图坐标。 */
    fun toBitmapX(viewX: Float): Float = viewX * scale
    fun toBitmapY(viewY: Float): Float = viewY * scale

    companion object {
        val EMPTY = BackdropSource(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888), 1f)

        /** 从一张已经是"降采样尺寸"的位图生成背景源（[scale] = 位图像素/屏幕像素）。 */
        fun fromScaledBitmap(
            bitmap: Bitmap,
            scale: Float,
            blurRadius: Float = GlassTokens.backdropBlurRadius,
            extraBlur: Float = 1f,
        ): BackdropSource {
            val blurred = FastBlur.blur(
                bitmap,
                (blurRadius * extraBlur).toInt().coerceAtLeast(1),
            )
            return BackdropSource(blurred, scale)
        }

        /**
         * 从一张屏幕尺寸的图生成可用的背景源：降采样 → 模糊。
         * [extraBlur] 用于"聚焦时背景更糊"的场合。
         */
        fun fromScreen(
            screen: Bitmap,
            downscale: Float = GlassTokens.backdropDownscale,
            blurRadius: Float = GlassTokens.backdropBlurRadius,
            extraBlur: Float = 1f,
        ): BackdropSource {
            val step = downscale.coerceAtLeast(1f)
            val w = (screen.width / step).toInt().coerceAtLeast(1)
            val h = (screen.height / step).toInt().coerceAtLeast(1)
            val small = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            Canvas(small).drawBitmap(
                screen,
                Rect(0, 0, screen.width, screen.height),
                Rect(0, 0, w, h),
                android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG),
            )
            val blurred = FastBlur.blur(small, (blurRadius * extraBlur / step).toInt().coerceAtLeast(1))
            return BackdropSource(blurred, 1f / step)
        }
    }
}
