package com.n1jika.myvia.ui.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.n1jika.myvia.ui.glass.BackdropSource
import com.n1jika.myvia.ui.glass.FastBlur

/**
 * 主页背景。
 *
 * 默认是一张程序生成的"子夜青"渐变（不用内置图片资源，装完就有好看的底），
 * 后续在"定制 → 背景"里可以换成用户自己的图。
 *
 * 同一个位图两用：放大铺满作为可见背景，另存一份模糊的小图给玻璃做透视源，
 * 所以玻璃里透出来的正是它背后这块背景，颜色和明暗天然对得上。
 */
object HomeBackground {

    /** 背景位图相对屏幕的降采样倍率 */
    private const val SCALE = 1f / 4f

    @Composable
    fun rememberBackgroundBitmap(): Bitmap {
        val density = LocalDensity.current
        val context = LocalContext.current
        val widthPx = context.resources.displayMetrics.widthPixels
        val heightPx = context.resources.displayMetrics.heightPixels
        return remember(widthPx, heightPx, density.density) {
            generateGradient(
                (widthPx * SCALE).toInt().coerceAtLeast(2),
                (heightPx * SCALE).toInt().coerceAtLeast(2),
            )
        }
    }

    @Composable
    fun rememberBackdrop(bitmap: Bitmap, extraBlur: Float = 1f): BackdropSource =
        remember(bitmap, extraBlur) {
            BackdropSource.fromScaledBitmap(bitmap, SCALE, extraBlur = extraBlur)
        }

    /**
     * 可见背景的"高斯模糊版"。
     * 搜索框聚焦时用它叠一层淡入，做出"整屏背景糊掉、把注意力交给搜索框"的效果。
     * 位图本来就只有 1/4 分辨率，再糊一次几乎不花时间。
     */
    @Composable
    fun rememberBlurredBackground(bitmap: Bitmap, scale: Float = 3.5f): Bitmap =
        remember(bitmap, scale) {
            FastBlur.blur(bitmap, scale.toInt().coerceAtLeast(1))
        }

    /** 程序生成的渐变底：深海蓝 → 青绿 → 靛紫，带两个柔光斑。 */
    private fun generateGradient(w: Int, h: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.shader = LinearGradient(
            0f, 0f, w * 0.35f, h.toFloat(),
            intArrayOf(0xFF0A1B2E.toInt(), 0xFF0E3A44.toInt(), 0xFF121F38.toInt()),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)

        // 左上的青绿柔光
        paint.shader = RadialGradient(
            w * 0.24f, h * 0.28f, w * 0.72f,
            intArrayOf(0x8C1F7A6B.toInt(), 0x001F7A6B),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)

        // 右下的靛紫柔光
        paint.shader = RadialGradient(
            w * 0.86f, h * 0.74f, w * 0.66f,
            intArrayOf(0x7A3B4C8A.toInt(), 0x003B4C8A),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)

        paint.shader = null
        return bitmap
    }
}

/** 铺满全屏的背景层。 */
@Composable
fun HomeBackgroundLayer(bitmap: Bitmap, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawImage(
                image = bitmap.asImageBitmap(),
                srcSize = androidx.compose.ui.unit.IntSize(bitmap.width, bitmap.height),
                dstSize = androidx.compose.ui.unit.IntSize(
                    size.width.toInt().coerceAtLeast(1),
                    size.height.toInt().coerceAtLeast(1),
                ),
                filterQuality = FilterQuality.Low,
            )
        }
    }
}
