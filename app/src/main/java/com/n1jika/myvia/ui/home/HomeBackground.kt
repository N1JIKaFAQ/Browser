package com.n1jika.myvia.ui.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.net.Uri
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import com.n1jika.myvia.Prefs
import com.n1jika.myvia.ui.glass.BackdropSource
import com.n1jika.myvia.ui.glass.FastBlur
import kotlin.random.Random

/** 主页背景位图 + 它相对屏幕的缩放比（位图像素 / 屏幕像素）。 */
class HomeBackgroundImage(
    val bitmap: Bitmap,
    /** 位图像素 / 屏幕像素 */
    val scale: Float,
)

/**
 * 主页背景。
 *
 * - 默认：浅色中性渐变 + 细密噪点纹理。**纹理很重要**——液态玻璃的折射只有在有细节的
 *   背景上才看得出来，纯色/纯渐变背景上折射是不可见的。
 * - 用户可在下拉菜单里选自己的照片，选完即成为背景，同时也成为玻璃的折射来源。
 * - 浅色打底还有个前提意义：地址栏与菜单的文字/图标是黑色，需要亮底才读得清；
 *   万一用户选了暗照片，玻璃会自动加厚霜化（见 GlassTokens.frostFor）。
 */
object HomeBackground {

    /** 生成/解码出来的背景相对屏幕的缩放比 */
    private const val GENERATED_SCALE = 1f / 4f

    @Composable
    fun rememberBackground(version: Int): HomeBackgroundImage {
        val context = LocalContext.current
        val metrics = context.resources.displayMetrics
        val w = metrics.widthPixels
        val h = metrics.heightPixels
        return remember(version, w, h) {
            val uri = Prefs.backgroundUri(context)
            val user = uri?.let { decodeUserImage(context, it, w, h) }
            user ?: HomeBackgroundImage(
                bitmap = generateDefault(
                    (w * GENERATED_SCALE).toInt().coerceAtLeast(2),
                    (h * GENERATED_SCALE).toInt().coerceAtLeast(2),
                ),
                scale = GENERATED_SCALE,
            )
        }
    }

    @Composable
    fun rememberBackdrop(image: HomeBackgroundImage, extraBlur: Float = 1f): BackdropSource =
        remember(image, extraBlur) {
            BackdropSource.fromScreen(
                image.bitmap,
                inputScale = image.scale,
                extraBlur = extraBlur,
            )
        }

    /** 可见背景的模糊版：聚焦搜索框时叠一层淡入，做出"整屏糊掉"的效果。 */
    @Composable
    fun rememberBlurredBackground(image: HomeBackgroundImage, radius: Float = 4f): Bitmap =
        remember(image, radius) {
            FastBlur.blur(image.bitmap, radius.toInt().coerceAtLeast(1))
        }

    /** 解码用户选的照片，按屏幕尺寸做 2 倍降采样，兼顾清晰度与内存。 */
    private fun decodeUserImage(context: Context, uri: Uri, screenW: Int, screenH: Int): HomeBackgroundImage? =
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sample = 1
            // 目标：解码尺寸 >= 屏幕（裁切后我们已导出到屏幕分辨率，通常 sample=1，不降采样）
            while (bounds.outWidth / (sample * 2) >= screenW &&
                bounds.outHeight / (sample * 2) >= screenH
            ) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return null

            HomeBackgroundImage(bitmap, bitmap.width.toFloat() / screenW.toFloat())
        }.getOrNull()

    /**
     * 默认背景：浅灰蓝底 + 斜向柔带 + 若干光斑 + 颗粒。
     *
     * **纹理尺度是被刻意放大的**：液态玻璃靠"背景被弯折"来表现自己，
     * 背景越平滑，玻璃就越像不存在。这里的斜带与光斑在 1/4 缩放的位图上
     * 仍有数个像素宽，降采样 + 模糊后依然留得住，边缘折射因此可见。
     * 用固定随机种子，保证每次生成一致（不会闪）。
     */
    private fun generateDefault(w: Int, h: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val random = Random(20260918)

        // 底色
        paint.shader = LinearGradient(
            0f, 0f, w * 0.45f, h.toFloat(),
            intArrayOf(0xFFF6F8FB.toInt(), 0xFFE7ECF3.toInt(), 0xFFDCE3EC.toInt()),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = null

        // 斜向柔带：给边缘折射提供长条形的高频参照
        val bandCount = 7
        repeat(bandCount) { i ->
            val t = (i + 1f) / (bandCount + 1f)
            val x = w * (t * 1.3f - 0.15f)
            val halfWidth = w * (0.03f + random.nextFloat() * 0.05f)
            paint.color = if (i % 2 == 0) 0x14FFFFFF else 0x12000000
            paint.alpha = (0x10 + random.nextInt(0x12))
            val path = android.graphics.Path().apply {
                moveTo(x - halfWidth, -h * 0.1f)
                lineTo(x + halfWidth, -h * 0.1f)
                lineTo(x + halfWidth * 1.6f + w * 0.25f, h * 1.1f)
                lineTo(x - halfWidth * 1.6f + w * 0.25f, h * 1.1f)
                close()
            }
            canvas.drawPath(path, paint)
        }

        // 光斑：局部色相与明度变化，让玻璃内部"透出"的东西有层次
        repeat(14) {
            val cx = random.nextFloat() * w
            val cy = random.nextFloat() * h
            val radius = (0.10f + random.nextFloat() * 0.22f) * w
            val cool = random.nextBoolean()
            val color = if (cool) 0x3FA8BCD6 else 0x33D8C7A8
            paint.shader = RadialGradient(
                cx, cy, radius,
                intArrayOf(color.toInt(), color and 0x00FFFFFF),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        }
        paint.shader = null

        // 粗颗粒：降采样后仍能留下，是折射最容易看出来的细节
        val dots = (w * h) / 160
        repeat(dots) {
            val x = random.nextFloat() * w
            val y = random.nextFloat() * h
            val radius = 0.8f + random.nextFloat() * 1.8f
            val dark = random.nextBoolean()
            paint.color = if (dark) 0x1A000000 else 0x1CFFFFFF
            canvas.drawCircle(x, y, radius, paint)
        }
        return bitmap
    }
}

/** 铺满全屏的背景层。 */
@Composable
fun HomeBackgroundLayer(bitmap: Bitmap, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().background(Color.White)) {
        ComposeCanvas(modifier = Modifier.fillMaxSize()) {
            drawImage(
                image = bitmap.asImageBitmap(),
                srcSize = IntSize(bitmap.width, bitmap.height),
                dstSize = IntSize(
                    size.width.toInt().coerceAtLeast(1),
                    size.height.toInt().coerceAtLeast(1),
                ),
                filterQuality = FilterQuality.Medium,
            )
        }
    }
}
