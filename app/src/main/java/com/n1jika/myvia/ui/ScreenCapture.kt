package com.n1jika.myvia.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.view.Window
import androidx.compose.ui.geometry.Offset
import com.n1jika.myvia.ui.glass.BackdropSource

/**
 * 抓取屏幕内容给玻璃当背景。
 *
 * 网页上方的玻璃要"透出"实时网页，而 WebView 内容无法直接当作着色器输入，
 * 所以按需抓一帧、模糊后复用。Android 的 PixelCopy 只提供 SurfaceView / Surface /
 * Window 三种来源（没有抓单个 View 的重载），抓到的坐标空间与 Compose 的
 * boundsInWindow() 一致。这里用**区域抓取**：只抓玻璃正上方那一小块，开销极小，
 * 从而能在浏览态按帧刷新做到"实时折射"。
 */
object ScreenCapture {

    private val main = Handler(Looper.getMainLooper())

    /** 抓整窗（备用/一次性）。 */
    fun captureWindow(window: Window?, onResult: (Bitmap?) -> Unit) {
        val decor = window?.decorView ?: return onResult(null)
        val w = decor.width
        val h = decor.height
        if (w <= 0 || h <= 0) return onResult(null)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        decor.post {
            runCatching {
                PixelCopy.request(
                    window!!,
                    bitmap,
                    PixelCopy.OnPixelCopyFinishedListener { r: Int ->
                        onResult(if (r == PixelCopy.SUCCESS) bitmap else null)
                    },
                    main,
                )
            }.onFailure { onResult(null) }
        }
    }

    /**
     * 只抓窗口里 `rect`（屏幕坐标）这一块，转成带原点的玻璃源，逐帧调用即实时跟随滚动。
     *
     * 优先 [PixelCopy]：它读的是**合成后的屏幕像素**，能反映 WebView 当前滚动位置；
     * 抓不到（个别机型/DRM）才退回 `View.draw()` 软绘。回调在主线程；失败回 null，
     * 调用方保留上一帧。
     */
    fun captureRegion(
        window: Window?,
        rect: Rect,
        sourceView: View? = null,
        onResult: (BackdropSource?) -> Unit,
    ) {
        val decor: View = window?.decorView ?: return onResult(null)
        val clamp = Rect(rect).apply { intersect(0, 0, decor.width, decor.height) }
        if (clamp.width() <= 0 || clamp.height() <= 0) return onResult(null)
        val origin = Offset(clamp.left.toFloat(), clamp.top.toFloat())

        val region = Bitmap.createBitmap(clamp.width(), clamp.height(), Bitmap.Config.ARGB_8888)
        val fallback = {
            if (!region.isRecycled) region.recycle()
            drawFallback(sourceView, clamp, origin, onResult)
        }
        runCatching {
            PixelCopy.request(
                window!!,
                clamp,
                region,
                PixelCopy.OnPixelCopyFinishedListener { r: Int ->
                    if (r == PixelCopy.SUCCESS) {
                        val bd = BackdropSource.fromRegion(region, origin)
                        if (!region.isRecycled) region.recycle()
                        onResult(bd)
                    } else {
                        fallback()
                    }
                },
                main,
            )
        }.onFailure { fallback() }
    }

    private fun drawFallback(
        sourceView: View?,
        clamp: Rect,
        origin: Offset,
        onResult: (BackdropSource?) -> Unit,
    ) {
        if (sourceView == null || sourceView.width <= 0 || sourceView.height <= 0) {
            onResult(null)
            return
        }
        val shot = Bitmap.createBitmap(clamp.width(), clamp.height(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(shot)
        canvas.translate(-clamp.left.toFloat(), -clamp.top.toFloat())
        runCatching { sourceView.draw(canvas) }
            .onSuccess { onResult(BackdropSource.fromRegion(shot, origin)) }
            .onFailure { shot.recycle(); onResult(null) }
    }

    /** 整窗抓取并直接转成玻璃源（备用路径）。 */
    fun captureBackdrop(window: Window?, onResult: (BackdropSource?) -> Unit) {
        captureWindow(window) { bitmap ->
            onResult(bitmap?.let { BackdropSource.fromScreen(it) })
        }
    }
}
