package com.n1jika.myvia.ui.glass

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * 玻璃"透视"的背景来源。
 *
 * [bitmap] 覆盖屏幕上的一个矩形区域（全屏，或仅顶部条带）；
 * [originScreen] 是该区域左上角的**屏幕坐标**；
 * [scale] = 位图像素 / 屏幕像素。
 * 于是屏幕坐标到位图坐标：`toBitmapX(x) = (x - originScreen.x) * scale`。
 */
@Immutable
class BackdropSource(
    val bitmap: Bitmap,
    /** 位图像素 / 屏幕像素 */
    val scale: Float,
    /** 位图覆盖区域的左上角屏幕坐标 */
    val originScreen: Offset = Offset.Zero,
) {
    val image: ImageBitmap by lazy { bitmap.asImageBitmap() }

    fun toBitmapX(viewX: Float): Float = (viewX - originScreen.x) * scale
    fun toBitmapY(viewY: Float): Float = (viewY - originScreen.y) * scale

    /**
     * 背景平均明度（0=全黑，1=全白）。
     * 用于：① 玻璃霜化程度；② 文字/图标在黑↔白之间自适应切换。
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

    /** 裁掉边缘一圈再算明度：边缘常被轮廓光提亮，会误导明度判断。 */
    val contentLuminance: Float by lazy {
        val cx = bitmap.width / 2f
        val cy = bitmap.height / 2f
        val pixels = IntArray(bitmap.width * bitmap.height).also {
            bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        }
        var sum = 0.0
        var count = 0
        val inset = minOf(bitmap.width, bitmap.height) * 0.28f
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                if (x >= inset && x <= bitmap.width - inset &&
                    y >= inset && y <= bitmap.height - inset
                ) {
                    val c = pixels[y * bitmap.width + x]
                    sum += 0.2126f * (c shr 16 and 0xFF) / 255f +
                        0.7152f * (c shr 8 and 0xFF) / 255f +
                        0.0722f * (c and 0xFF) / 255f
                    count++
                }
                x += 4
            }
            y += 4
        }
        if (count == 0) luminance else (sum / count).toFloat().coerceIn(0f, 1f)
    }

    companion object {
        val EMPTY = BackdropSource(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888), 1f)

        /** 从一块**已按区域裁好**的位图生成（供实时抓帧用）。 */
        fun fromRegion(
            region: Bitmap,
            originScreen: Offset,
            downscale: Float = GlassTokens.backdropDownscale,
            blurRadius: Float = GlassTokens.backdropBlurRadius,
        ): BackdropSource {
            val step = downscale.coerceAtLeast(1f)
            val w = (region.width / step).toInt().coerceAtLeast(1)
            val h = (region.height / step).toInt().coerceAtLeast(1)
            val small = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            Canvas(small).drawBitmap(
                region,
                Rect(0, 0, region.width, region.height),
                Rect(0, 0, w, h),
                Paint(Paint.FILTER_BITMAP_FLAG),
            )
            val blurred = FastBlur.blur(small, (blurRadius / step).toInt().coerceAtLeast(1))
            return BackdropSource(blurred, 1f / step, originScreen)
        }

        /**
         * 从一张背景图（整屏或缩略）生成玻璃采样源：降采样 → 模糊。
         * @param inputScale 输入位图相对**屏幕**的缩放比（生成小图传 1/4，整屏截图传 1）
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
                Paint(Paint.FILTER_BITMAP_FLAG),
            )
            val blurred = FastBlur.blur(
                small,
                (blurRadius * extraBlur / step).toInt().coerceAtLeast(1),
            )
            return BackdropSource(blurred, inputScale / step, Offset.Zero)
        }
    }
}
