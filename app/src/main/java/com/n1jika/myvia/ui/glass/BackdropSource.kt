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

    /**
     * 背景平均明度（0=全黑，1=全白）。
     * 用于自适应玻璃霜化程度：背景越暗，玻璃越白，保证黑字可读。
     * 采样步长取 4，几万个像素的遍历在毫秒级。
     */
    val luminance: Float by lazy {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return@lazy 1f
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        var sum = 0.0
        var count = 0
        var i = 0
        while (i < pixels.size) {
            val c = pixels[i]
            val r = (c shr 16 and 0xFF) / 255f
            val g = (c shr 8 and 0xFF) / 255f
            val b = (c and 0xFF) / 255f
            sum += 0.2126f * r + 0.7152f * g + 0.0722f * b
            count++
            i += 4
        }
        if (count == 0) 1f else (sum / count).toFloat().coerceIn(0f, 1f)
    }

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
         * 从一张背景图生成玻璃可用的采样源：降采样 → 模糊。
         *
         * @param inputScale 输入位图相对**屏幕**的缩放比（生成的小图传 1/4，整屏截图传 1）
         * @param extraBlur 聚焦时背景更糊的倍率
         */
        fun fromScreen(
            screen: Bitmap,
            inputScale: Float = 1f,
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
            // 位图像素 / 屏幕像素：输入自身的缩放比再除以这次降采样
            return BackdropSource(blurred, inputScale / step)
        }
    }
}
