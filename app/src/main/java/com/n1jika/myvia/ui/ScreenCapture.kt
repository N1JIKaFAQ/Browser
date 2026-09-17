package com.n1jika.myvia.ui

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.Window
import com.n1jika.myvia.ui.glass.BackdropSource

/**
 * 抓取窗口内容当玻璃的背景。
 *
 * 网页上方的玻璃必须"透出"网页本身，而 WebView 的内容没法直接当着色器输入，
 * 所以按需抓一帧窗口快照、模糊后复用。只在进入浏览态、页面加载完成、打开菜单时抓，
 * 绝不逐帧抓屏。
 *
 * 用 Window 版本的 PixelCopy（Android 只提供 SurfaceView / Surface / Window 三种来源，
 * 没有直接抓某个 View 的重载）；抓到的坐标空间与 Compose 的 boundsInWindow() 一致。
 */
object ScreenCapture {

    fun captureWindow(window: Window?, onResult: (Bitmap?) -> Unit) {
        val decor = window?.decorView ?: return onResult(null)
        val w = decor.width
        val h = decor.height
        if (w <= 0 || h <= 0) return onResult(null)

        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        decor.post {
            runCatching {
                PixelCopy.request(
                    window,
                    bitmap,
                    PixelCopy.OnPixelCopyFinishedListener { result: Int ->
                        onResult(if (result == PixelCopy.SUCCESS) bitmap else null)
                    },
                    Handler(Looper.getMainLooper()),
                )
            }.onFailure { onResult(null) }
        }
    }

    /** 抓帧并直接转成玻璃可用的背景源。 */
    fun captureBackdrop(window: Window?, onResult: (BackdropSource?) -> Unit) {
        captureWindow(window) { bitmap ->
            onResult(bitmap?.let { BackdropSource.fromScreen(it) })
        }
    }
}
