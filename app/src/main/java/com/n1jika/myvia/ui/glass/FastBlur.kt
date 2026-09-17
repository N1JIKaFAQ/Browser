package com.n1jika.myvia.ui.glass

import android.graphics.Bitmap

/**
 * 纯 CPU 的快速方框模糊（三次近似高斯）。
 *
 * 只作用在**降采样后**的背景小图上（默认 1/8，约 4 万像素），
 * 耗时在毫秒级，且不依赖 API 31 的 RenderEffect，全版本一致。
 */
object FastBlur {

    fun blur(source: Bitmap, radius: Int): Bitmap {
        if (radius < 1) return source
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)

        // 三次方框模糊近似高斯
        repeat(3) {
            boxBlurHorizontal(pixels, w, h, radius)
            boxBlurVertical(pixels, w, h, radius)
        }

        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, w, 0, 0, w, h)
        }
    }

    private fun boxBlurHorizontal(p: IntArray, w: Int, h: Int, r: Int) {
        val div = 2 * r + 1
        val out = IntArray(w)
        for (y in 0 until h) {
            val row = y * w
            var a = 0; var red = 0; var g = 0; var b = 0
            for (i in -r..r) {
                val c = p[row + i.coerceIn(0, w - 1)]
                a += c ushr 24 and 0xFF; red += c ushr 16 and 0xFF
                g += c ushr 8 and 0xFF; b += c and 0xFF
            }
            for (x in 0 until w) {
                out[x] = ((a / div) shl 24) or ((red / div) shl 16) or ((g / div) shl 8) or (b / div)
                val add = p[row + (x + r + 1).coerceIn(0, w - 1)]
                val sub = p[row + (x - r).coerceIn(0, w - 1)]
                a += (add ushr 24 and 0xFF) - (sub ushr 24 and 0xFF)
                red += (add ushr 16 and 0xFF) - (sub ushr 16 and 0xFF)
                g += (add ushr 8 and 0xFF) - (sub ushr 8 and 0xFF)
                b += (add and 0xFF) - (sub and 0xFF)
            }
            System.arraycopy(out, 0, p, row, w)
        }
    }

    private fun boxBlurVertical(p: IntArray, w: Int, h: Int, r: Int) {
        val div = 2 * r + 1
        val out = IntArray(h)
        for (x in 0 until w) {
            var a = 0; var red = 0; var g = 0; var b = 0
            for (i in -r..r) {
                val c = p[i.coerceIn(0, h - 1) * w + x]
                a += c ushr 24 and 0xFF; red += c ushr 16 and 0xFF
                g += c ushr 8 and 0xFF; b += c and 0xFF
            }
            for (y in 0 until h) {
                out[y] = ((a / div) shl 24) or ((red / div) shl 16) or ((g / div) shl 8) or (b / div)
                val add = p[(y + r + 1).coerceIn(0, h - 1) * w + x]
                val sub = p[(y - r).coerceIn(0, h - 1) * w + x]
                a += (add ushr 24 and 0xFF) - (sub ushr 24 and 0xFF)
                red += (add ushr 16 and 0xFF) - (sub ushr 16 and 0xFF)
                g += (add ushr 8 and 0xFF) - (sub ushr 8 and 0xFF)
                b += (add and 0xFF) - (sub and 0xFF)
            }
            for (y in 0 until h) p[y * w + x] = out[y]
        }
    }
}
