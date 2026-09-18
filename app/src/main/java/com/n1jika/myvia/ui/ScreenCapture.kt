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
     * 只抓窗口里 `rect`（屏幕坐标）这一块，转成带原点的玻璃源。
     *
     * 优先用 [sourceView]（当前 WebView）**软件绘制**出该区域的实时网页像素——
     * PixelCopy 抓 WebView 合成层在部分机型（尤其 ColorOS）拿不到内容、会退化成
     * 壁纸或黑块；而 `View.draw()` 能稳定拿到网页当前画面（含滚动位置）。
     * sourceView 不可用时再退回 PixelCopy。回调在主线程触发。
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

        if (sourceView != null && sourceView.width > 0 && sourceView.height > 0) {
            val shot = Bitmap.createBitmap(clamp.width(), clamp.height(), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(shot)
            canvas.translate(-clamp.left.toFloat(), -clamp.top.toFloat())
            runCatching { sourceView.draw(canvas) }
                .onFailure {
                    shot.recycle()
                    captureViaPixelCopy(window, clamp, origin, onResult)
                    return
                }
            onResult(BackdropSource.fromRegion(shot, origin))
            return
        }
        captureViaPixelCopy(window, clamp, origin, onResult)
    }

    private fun captureViaPixelCopy(
        window: Window,
        clamp: Rect,
        origin: Offset,
        onResult: (BackdropSource?) -> Unit,
    ) {
        val region = Bitmap.createBitmap(clamp.width(), clamp.height(), Bitmap.Config.ARGB_8888)
        runCatching {
            PixelCopy.request(
                window,
                clamp,
                region,
                PixelCopy.OnPixelCopyFinishedListener { r: Int ->
                    onResult(
                        if (r == PixelCopy.SUCCESS) BackdropSource.fromRegion(region, origin) else null,
                    )
                },
                main,
            )
        }.onFailure { onResult(null) }
    }

    /** 整窗抓取并直接转成玻璃源（备用路径）。 */
    fun captureBackdrop(window: Window?, onResult: (BackdropSource?) -> Unit) {
        captureWindow(window) { bitmap ->
            onResult(bitmap?.let { BackdropSource.fromScreen(it) })
        }
    }
}
