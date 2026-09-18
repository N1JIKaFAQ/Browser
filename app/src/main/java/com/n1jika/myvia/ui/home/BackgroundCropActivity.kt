package com.n1jika.myvia.ui.home

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.n1jika.myvia.Prefs
import java.io.File

/**
 * 背景图裁切：全屏取景框 = 屏幕宽高比，可双指缩放/拖动图片，
 * 完成时把取景框内的画面按屏幕分辨率导出（1:1，不降采样 → 不糊）。
 */
class BackgroundCropActivity : ComponentActivity() {

    private var source: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent.dataString?.let { runCatching { Uri.parse(it) }.getOrNull() }
        val decoded = uri?.let { decodeFull(it) }
        if (decoded == null) {
            finish()
            return
        }
        source = decoded
        setContent {
            CropScreen(
                source = decoded,
                onDone = { cropped -> saveAndFinish(cropped) },
                onCancel = { finish() },
            )
        }
    }

    override fun onDestroy() {
        source?.recycle()
        source = null
        super.onDestroy()
    }

    private fun saveAndFinish(cropped: Bitmap) {
        val file = File(filesDir, "myvia_background.png")
        runCatching {
            file.outputStream().use { cropped.compress(Bitmap.CompressFormat.PNG, 100, it) }
            Prefs.setBackgroundUri(this, Uri.fromFile(file))
        }
        if (cropped !== source) cropped.recycle()
        setResult(RESULT_OK)
        finish()
    }

    private fun decodeFull(uri: Uri): Bitmap? = runCatching {
        val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }.getOrNull()
}

@Composable
private fun CropScreen(
    source: Bitmap,
    onDone: (Bitmap) -> Unit,
    onCancel: () -> Unit,
) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }

    fun coverScale(vw: Float, vh: Float): Float {
        if (source.width == 0 || source.height == 0) return 1f
        return maxOf(vw / source.width, vh / source.height)
    }

    fun clamp(p: Offset, s: Float, vw: Int, vh: Int): Offset {
        val iw = source.width * s
        val ih = source.height * s
        val maxX = ((iw - vw) / 2f).coerceAtLeast(0f)
        val maxY = ((ih - vh) / 2f).coerceAtLeast(0f)
        return Offset(p.x.coerceIn(-maxX, maxX), p.y.coerceIn(-maxY, maxY))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { viewport = it }
            .pointerInput(viewport) {
                detectTransformGestures { _, panBy, zoomBy, _ ->
                    val vw = viewport.width
                    val vh = viewport.height
                    zoom = (zoom * zoomBy).coerceIn(1f, 5f)
                    val s = coverScale(vw.toFloat(), vh.toFloat()) * zoom
                    pan = clamp(pan + panBy, s, vw, vh)
                }
            },
    ) {
        val s0 = coverScale(viewport.width.toFloat(), viewport.height.toFloat())
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (viewport.width > 0 && viewport.height > 0) {
                val s = s0 * zoom
                drawIntoCanvas { canvas ->
                    val m = Matrix().apply {
                        postTranslate(viewport.width / 2f + pan.x, viewport.height / 2f + pan.y)
                        postScale(s, s)
                        postTranslate(-source.width / 2f, -source.height / 2f)
                    }
                    canvas.nativeCanvas.concat(m)
                    canvas.nativeCanvas.drawBitmap(
                        source, 0f, 0f,
                        android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG),
                    )
                }
            }
        }

        // 顶部操作条（工具屏，普通文字按钮即可）
        Box(
            Modifier
                .fillMaxSize()
                .padding(8.dp),
        ) {
            CropAction("取消", onCancel, Modifier.align(Alignment.CenterStart))
            CropAction(
                "完成",
                { onDone(exportCrop(source, s0 * zoom, pan, viewport)) },
                Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

@Composable
private fun CropAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(8.dp)
            .clickable(onClick = onClick),
    ) {
        BasicText(
            text = label,
            style = TextStyle(color = Color.White, fontSize = 17.sp),
        )
    }
}

/** 把取景框内的画面按视口分辨率导出（1:1，不降采样）。 */
private fun exportCrop(source: Bitmap, scale: Float, pan: Offset, viewport: IntSize): Bitmap {
    val vw = viewport.width.coerceAtLeast(1)
    val vh = viewport.height.coerceAtLeast(1)
    val out = Bitmap.createBitmap(vw, vh, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(out)
    val m = Matrix().apply {
        postTranslate(vw / 2f + pan.x, vh / 2f + pan.y)
        postScale(scale, scale)
        postTranslate(-source.width / 2f, -source.height / 2f)
    }
    canvas.concat(m)
    canvas.drawBitmap(source, 0f, 0f, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
    return out
}
