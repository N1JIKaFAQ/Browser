package com.n1jika.myvia.ui.menu

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate

/**
 * 手绘线性图标。统一 24×24 视口、1.7 描边、圆头圆角，不依赖任何图标库。
 * 用法：`Canvas(Modifier.size(22.dp)) { MenuIcons.download() }`
 */
object MenuIcons {

    private val ink = Color(0xFF15171C)

    private fun DrawScope.u(v: Float): Float = size.minDimension / 24f * v

    private fun DrawScope.lineWidth(v: Float = 1.7f): Float = size.minDimension / 24f * v

    private fun DrawScope.lineStroke(v: Float = 1.7f) =
        Stroke(width = lineWidth(v), cap = StrokeCap.Round, join = StrokeJoin.Round)

    /** 书签 */
    val bookmark: DrawScope.() -> Unit = {
        translate(u(4f), u(3f)) {
            val p = Path().apply {
                moveTo(0f, u(18f))
                lineTo(0f, u(1.5f))
                lineTo(u(16f), u(1.5f))
                lineTo(u(16f), u(18f))
                lineTo(u(8f), u(12.5f))
                close()
            }
            drawPath(p, ink)
        }
    }

    /** 历史（时钟） */
    val history: DrawScope.() -> Unit = {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = u(8f)
        drawCircle(ink, radius = r, center = c, style = lineStroke())
        drawLine(ink, c, Offset(c.x, c.y - u(5f)), lineWidth(), StrokeCap.Round)
        drawLine(ink, c, Offset(c.x + u(4f), c.y), lineWidth(), StrokeCap.Round)
    }

    /** 下载 */
    val download: DrawScope.() -> Unit = {
        val cx = size.width / 2f
        drawLine(ink, Offset(cx, u(3.5f)), Offset(cx, u(14f)), lineWidth(), StrokeCap.Round)
        drawLine(ink, Offset(cx - u(5f), u(9f)), Offset(cx, u(14f)), lineWidth(), StrokeCap.Round)
        drawLine(ink, Offset(cx + u(5f), u(9f)), Offset(cx, u(14f)), lineWidth(), StrokeCap.Round)
        drawLine(ink, Offset(u(4.5f), u(19f)), Offset(u(19.5f), u(19f)), lineWidth(), StrokeCap.Round)
    }

    /** 隐身（闭眼 + 斜线） */
    val incognito: DrawScope.() -> Unit = {
        val p = Path().apply {
            moveTo(u(2f), u(12f))
            quadraticBezierTo(u(12f), u(2.5f), u(22f), u(12f))
            quadraticBezierTo(u(12f), u(21.5f), u(2f), u(12f))
            close()
        }
        drawPath(p, ink, style = lineStroke())
        drawLine(ink, Offset(u(4.5f), u(19.5f)), Offset(u(19.5f), u(4.5f)), lineWidth(), StrokeCap.Round)
    }

    /** 分享 */
    val share: DrawScope.() -> Unit = {
        val a = Offset(u(6f), u(12f))
        val b = Offset(u(18f), u(5f))
        val c = Offset(u(18f), u(19f))
        drawLine(ink, a, b, lineWidth(1.4f), StrokeCap.Round)
        drawLine(ink, a, c, lineWidth(1.4f), StrokeCap.Round)
        drawCircle(ink, u(3f), a)
        drawCircle(ink, u(3f), b)
        drawCircle(ink, u(3f), c)
    }

    /** 添加书签（书签 + 加号） */
    val addBookmark: DrawScope.() -> Unit = {
        translate(u(2f), u(3f)) {
            val p = Path().apply {
                moveTo(0f, u(18f))
                lineTo(0f, u(1.5f))
                lineTo(u(14f), u(1.5f))
                lineTo(u(14f), u(18f))
                lineTo(u(7f), u(12.5f))
                close()
            }
            drawPath(p, ink, style = lineStroke())
        }
        translate(u(18f), u(17f)) {
            drawLine(ink, Offset(-u(4f), 0f), Offset(u(4f), 0f), lineWidth(), StrokeCap.Round)
            drawLine(ink, Offset(0f, -u(4f)), Offset(0f, u(4f)), lineWidth(), StrokeCap.Round)
        }
    }

    /** 电脑模式（显示器） */
    val desktop: DrawScope.() -> Unit = {
        drawRoundRect(
            color = ink,
            topLeft = Offset(u(2.5f), u(4f)),
            size = Size(u(19f), u(13f)),
            cornerRadius = CornerRadius(u(2f)),
            style = lineStroke(),
        )
        drawLine(ink, Offset(u(12f), u(17f)), Offset(u(12f), u(20f)), lineWidth(), StrokeCap.Round)
        drawLine(ink, Offset(u(8f), u(20.5f)), Offset(u(16f), u(20.5f)), lineWidth(), StrokeCap.Round)
    }

    /** 工具箱（三根带旋钮的滑杆） */
    val toolbox: DrawScope.() -> Unit = {
        listOf(7f to 9f, 12f to 16f, 17f to 6f).forEach { (y, knobX) ->
            drawLine(ink, Offset(u(3f), u(y)), Offset(u(21f), u(y)), lineWidth(1.5f), StrokeCap.Round)
            drawCircle(ink, u(2.6f), Offset(u(knobX), u(y)))
        }
    }

    /** 设置（齿轮） */
    val settings: DrawScope.() -> Unit = {
        val c = Offset(size.width / 2f, size.height / 2f)
        val outer = u(7.5f)
        repeat(8) { i ->
            rotate(degrees = i * 45f, pivot = c) {
                drawLine(
                    color = ink,
                    start = Offset(c.x, c.y - outer),
                    end = Offset(c.x, c.y - outer - u(2.6f)),
                    strokeWidth = u(2.4f),
                    cap = StrokeCap.Round,
                )
            }
        }
        drawCircle(ink, outer, c, style = lineStroke())
        drawCircle(ink, u(3f), c, style = lineStroke())
    }

    /** 背景图（相框 + 山与太阳） */
    val picture: DrawScope.() -> Unit = {
        drawRoundRect(
            color = ink,
            topLeft = Offset(u(3f), u(4.5f)),
            size = Size(u(18f), u(15f)),
            cornerRadius = CornerRadius(u(2.5f)),
            style = lineStroke(),
        )
        // 山
        val mountain = Path().apply {
            moveTo(u(3f), u(17f))
            lineTo(u(9.5f), u(11f))
            lineTo(u(14f), u(15.5f))
            lineTo(u(16.5f), u(13.5f))
            lineTo(u(21f), u(17f))
            close()
        }
        drawPath(mountain, ink)
        // 太阳
        drawCircle(ink, u(2f), Offset(u(8.5f), u(8f)), style = lineStroke(1.4f))
    }
}
