package com.n1jika.myvia.ui.addressbar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 地址栏内容。极简：无图标、无按钮、无提示文字，只有居中的文字本身。
 *
 * - 颜色自适应（[contentColor]）：浅底黑字、深底白字，配合透明玻璃在任何网站可读
 * - 浏览态：网址**几何居中**，过长时两端羽化渐隐（近似高斯）而非省略号硬切
 */
@Composable
fun AddressBarContent(
    text: String,
    url: String,
    editing: Boolean,
    contentColor: Color,
    onTextChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    val style = TextStyle(
        color = contentColor,
        fontSize = 17.sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (editing) {
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                    // 硬件/外接键盘的回车也要能提交（IME 的 Go 只覆盖软键盘）
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown &&
                            (event.key == Key.Enter || event.key == Key.NumPadEnter)
                        ) {
                            onSubmit()
                            true
                        } else {
                            false
                        }
                    },
                singleLine = true,
                textStyle = style,
                cursorBrush = SolidColor(contentColor),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onSubmit() }),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        inner()
                    }
                },
            )
        } else {
            // 外层盒负责两端渐隐遮罩，内层用 Center 对齐 + 内容宽度 → 文字真正几何居中
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        val f = 0.16f
                        drawRect(
                            brush = Brush.horizontalGradient(
                                0f to Color.Transparent,
                                f * 0.4f to Color.Black.copy(alpha = 0.5f),
                                f to Color.Black,
                                (1f - f) to Color.Black,
                                (1f - f * 0.4f) to Color.Black.copy(alpha = 0.5f),
                                1f to Color.Transparent,
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = url,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    style = style,
                )
            }
        }
    }
}
