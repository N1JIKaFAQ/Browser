package com.n1jika.myvia.ui.menu

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.TextStyle
import com.n1jika.myvia.ui.glass.BackdropSource
import com.n1jika.myvia.ui.glass.GlassSurface
import com.n1jika.myvia.ui.glass.GlassTokens

/** 菜单里的功能项。 */
enum class MenuAction {
    Bookmarks, History, Downloads, Incognito, Share,
    AddBookmark, DesktopMode, Toolbox, BackgroundImage, Settings,
}

data class MenuEntry(
    val action: MenuAction,
    val label: String,
    val icon: DrawScope.() -> Unit,
)

/**
 * 菜单条目表。以后加功能只在这里加一行，布局和滚动不用动。
 */
val defaultMenuEntries: List<MenuEntry> = listOf(
    MenuEntry(MenuAction.Bookmarks, "书签", MenuIcons.bookmark),
    MenuEntry(MenuAction.History, "历史", MenuIcons.history),
    MenuEntry(MenuAction.Downloads, "下载", MenuIcons.download),
    MenuEntry(MenuAction.Incognito, "隐身", MenuIcons.incognito),
    MenuEntry(MenuAction.Share, "分享", MenuIcons.share),
    MenuEntry(MenuAction.AddBookmark, "添加书签", MenuIcons.addBookmark),
    MenuEntry(MenuAction.DesktopMode, "电脑模式", MenuIcons.desktop),
    MenuEntry(MenuAction.Toolbox, "工具箱", MenuIcons.toolbox),
    MenuEntry(MenuAction.BackgroundImage, "背景图", MenuIcons.picture),
    MenuEntry(MenuAction.Settings, "设置", MenuIcons.settings),
)

/**
 * 液态玻璃下拉菜单。
 *
 * 可视区**固定显示 4 项**（[GlassTokens.menuVisibleItems]），超出部分上下滚动，
 * 上下边缘各有一条渐隐带，暗示"还有更多"；越界时有平台自带的回弹效果。
 */
@Composable
fun BrowserMenu(
    backdrop: BackdropSource?,
    onPick: (MenuAction) -> Unit,
    modifier: Modifier = Modifier,
    frost: Float = -1f,
    entries: List<MenuEntry> = defaultMenuEntries,
) {
    val viewportHeight = GlassTokens.menuItemHeight * GlassTokens.menuVisibleItems

    GlassSurface(
        modifier = modifier.width(GlassTokens.menuWidth),
        backdrop = backdrop,
        cornerRadius = GlassTokens.menuCorner,
        frost = frost,
    ) {
        Column(
            modifier = Modifier
                .height(viewportHeight)
                .fillMaxWidth()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val fade = GlassTokens.menuItemHeight.toPx() * 0.42f
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Transparent,
                            fade / size.height to Color.Black,
                            (size.height - fade) / size.height to Color.Black,
                            1f to Color.Transparent,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                }
                .verticalScroll(rememberScrollState()),
        ) {
            entries.forEach { entry ->
                MenuRow(entry) { onPick(entry.action) }
            }
        }
    }
}

@Composable
private fun MenuRow(entry: MenuEntry, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(GlassTokens.menuItemHeight)
            .background(
                if (pressed) Color.Black.copy(alpha = 0.07f) else Color.Transparent,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Canvas(modifier = Modifier.size(22.dp)) { entry.icon.invoke(this) }
        Spacer(Modifier.width(14.dp))
        BasicText(
            text = entry.label,
            style = TextStyle(
                color = Color(0xFF15171C),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

/** 菜单出现时的锚点：从按钮那一角长出来。 */
@Composable
fun MenuScrim(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
    )
}
