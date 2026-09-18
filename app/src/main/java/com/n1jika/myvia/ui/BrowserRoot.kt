package com.n1jika.myvia.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.widget.ViewPager2
import com.n1jika.myvia.tab.TabAdapter
import com.n1jika.myvia.tab.TabManager
import com.n1jika.myvia.ui.addressbar.AddressBarContent
import com.n1jika.myvia.ui.glass.BackdropSource
import com.n1jika.myvia.ui.glass.GlassButton
import com.n1jika.myvia.ui.glass.GlassSurface
import com.n1jika.myvia.ui.glass.GlassTokens
import com.n1jika.myvia.ui.glass.HamburgerIcon
import com.n1jika.myvia.ui.home.HomeBackground
import com.n1jika.myvia.ui.home.HomeBackgroundLayer
import com.n1jika.myvia.ui.menu.BrowserMenu
import com.n1jika.myvia.ui.menu.MenuAction
import com.n1jika.myvia.ui.motion.Haptics
import com.n1jika.myvia.ui.motion.Kind
import com.n1jika.myvia.ui.motion.Springs
import com.n1jika.myvia.ui.motion.impactEffect
import com.n1jika.myvia.ui.motion.rememberImpact
import com.n1jika.myvia.ui.motion.rememberHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 三种界面状态。元素在这三态之间连续飞行，而不是页面跳转。 */
enum class UiMode { Home, Editing, Browsing }

/** 跨 Compose 与 WebView 的共享状态。 */
class BrowserUiState {
    var mode by mutableStateOf(UiMode.Home)
    var query by mutableStateOf("")
    var currentUrl by mutableStateOf("")
    var loading by mutableStateOf(false)
    var menuOpen by mutableStateOf(false)
    var pageBackdrop by mutableStateOf<BackdropSource?>(null)
    /** 每次更换背景图自增，用来触发背景重新加载 */
    var backgroundVersion by mutableIntStateOf(0)
}

/**
 * 浏览器外壳。
 *
 * 屏幕上永远是同一批元素：搜索/地址栏、右上角菜单键、背景、网页层。
 * 状态切换只是把它们弹到不同的位置和尺寸，所以过渡天然连贯。
 */
@Composable
fun BrowserRoot(
    activity: FragmentActivity,
    ui: BrowserUiState,
    tabManager: TabManager,
    tabAdapter: TabAdapter,
    onNavigate: (String) -> Unit,
    onMenuAction: (MenuAction) -> Unit,
    onPagerCreated: (ViewPager2) -> Unit,
) {
    val density = LocalDensity.current
    val haptic = rememberHaptics()
    val impact = rememberImpact()
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    val bgImage = HomeBackground.rememberBackground(ui.backgroundVersion)
    val backdropNormal = HomeBackground.rememberBackdrop(bgImage, extraBlur = 1f)
    val backdropFocused = HomeBackground.rememberBackdrop(bgImage, extraBlur = GlassTokens.focusExtraBlur)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val statusBar = activity.window.decorView.rootWindowInsets?.let {
            androidx.core.view.WindowInsetsCompat
                .toWindowInsetsCompat(it)
                .getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars())
                .top
        } ?: 0

        // ---------- 布局度量 ----------
        val homeBar = Rect(
            left = w * 0.07f,
            top = h * 0.382f - with(density) { GlassTokens.searchBarHeight.toPx() } / 2f,
            right = w * 0.93f,
            bottom = h * 0.382f + with(density) { GlassTokens.searchBarHeight.toPx() } / 2f,
        )
        val browseBarWidth = w * 0.60f
        val browseBarHeight = with(density) { GlassTokens.addressBarHeight.toPx() }
        val browseBar = Rect(
            left = with(density) { 16.dp.toPx() },
            top = (statusBar + with(density) { 8.dp.toPx() }),
            right = with(density) { 16.dp.toPx() } + browseBarWidth,
            bottom = (statusBar + with(density) { 8.dp.toPx() }) + browseBarHeight,
        )
        val btnSize = with(density) { GlassTokens.menuButtonSize.toPx() }
        val homeBtn = Rect(
            left = w - with(density) { 16.dp.toPx() } - btnSize,
            top = (statusBar + with(density) { 14.dp.toPx() }),
            right = w - with(density) { 16.dp.toPx() },
            bottom = (statusBar + with(density) { 14.dp.toPx() }) + btnSize,
        )
        val browseBtn = Rect(
            left = browseBar.right + with(density) { 8.dp.toPx() },
            top = browseBar.top + (browseBarHeight - btnSize) / 2f,
            right = browseBar.right + with(density) { 8.dp.toPx() } + btnSize,
            bottom = browseBar.top + (browseBarHeight - btnSize) / 2f + btnSize,
        )

        // ---------- 飞行的元素 ----------
        val barRect = remember(w, h, statusBar) {
            Animatable(homeBar, Rect.VectorConverter)
        }
        val btnRect = remember(w, h, statusBar) {
            Animatable(homeBtn, Rect.VectorConverter)
        }
        val bar by barRect.asState()
        val btn by btnRect.asState()

        LaunchedEffect(ui.mode, w, h) {
            val browse = ui.mode == UiMode.Browsing
            kotlinx.coroutines.coroutineScope {
                launch { barRect.animateTo(if (browse) browseBar else homeBar, Springs.flight) }
                launch { btnRect.animateTo(if (browse) browseBtn else homeBtn, Springs.flight) }
            }
            if (browse) {
                // 地址栏与菜单键到位：来一次轻微碰撞，玻璃"碰"在一起
                impact.impact()
                haptic(Kind.Confirm)
            }
        }

        // ---------- 实时折射：浏览态按帧抓玻璃下方的屏幕块 ----------
        // 只抓"顶部条带"（覆盖地址栏 + 菜单键 + 打开时的菜单），飞行时该块随
        // bar 位置变化 → 抓到屏幕真实合成画面（主页→网页的交叉淡入也被如实折射）；
        // 落地后跟随网页滚动刷新。用 busy 标志避免抓帧请求堆积。
        LaunchedEffect(ui.mode, ui.backgroundVersion) {
            if (ui.mode != UiMode.Browsing) return@LaunchedEffect
            val window = activity.window
            var busy = false
            while (isActive) {
                if (!busy) {
                    busy = true
                    val barNow = barRect.value
                    val btnNow = btnRect.value
                    val menuPx = if (ui.menuOpen) {
                        with(density) {
                            GlassTokens.menuItemHeight.toPx() * GlassTokens.menuVisibleItems +
                                GlassTokens.menuCorner.toPx() * 2f
                        }
                    } else {
                        0f
                    }
                    val bandBottom = (maxOf(barNow.bottom, btnNow.bottom) + menuPx)
                        .coerceIn(1f, h)
                    ScreenCapture.captureRegion(
                        window = window,
                        rect = android.graphics.Rect(0, 0, w.toInt(), bandBottom.toInt()),
                    ) { bd ->
                        busy = false
                        if (bd != null) ui.pageBackdrop = bd
                    }
                }
                delay(if (ui.loading) 16L else 80L)
            }
        }

        // ---------- 背景层 ----------
        val bgAlpha by animateFloatAsState(
            targetValue = if (ui.mode == UiMode.Browsing) 0f else 1f,
            animationSpec = Springs.gentle,
            label = "bgAlpha",
        )
        HomeBackgroundLayer(
            bitmap = bgImage.bitmap,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = bgAlpha },
        )
        // 聚焦时叠一层模糊版背景并淡入：整屏糊掉，注意力集中到搜索框
        val blurredBgBitmap = HomeBackground.rememberBlurredBackground(bgImage)
        val bgBlurAlpha by animateFloatAsState(
            targetValue = if (ui.mode == UiMode.Editing) 1f else 0f,
            animationSpec = Springs.gentle,
            label = "bgBlurAlpha",
        )
        HomeBackgroundLayer(
            bitmap = blurredBgBitmap,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = bgAlpha * bgBlurAlpha },
        )

        // ---------- 网页层 ----------
        // AndroidView 是真 View，Compose 的 graphicsLayer(alpha) 对它无效，
        // 必须用 View 自己的 alpha/visibility 来控制显隐，否则未加载的 WebView
        // 会以深色底整块盖住主页背景
        val pageAlpha by animateFloatAsState(
            targetValue = if (ui.mode == UiMode.Browsing) 1f else 0f,
            animationSpec = Springs.gentle,
            label = "pageAlpha",
        )
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                ViewPager2(context).apply {
                    adapter = tabAdapter
                    isUserInputEnabled = false
                    onPagerCreated(this)
                }
            },
            update = { pager ->
                pager.alpha = pageAlpha
                pager.visibility = if (pageAlpha < 0.01f) android.view.View.INVISIBLE
                else android.view.View.VISIBLE
            },
        )

        // ---------- 聚焦遮罩：搜索框弹起时整屏压暗 ----------
        val dim by animateFloatAsState(
            targetValue = if (ui.mode == UiMode.Editing) GlassTokens.focusDim else 0f,
            animationSpec = Springs.gentle,
            label = "dim",
        )
        // 注意：不要把 alpha() 写在 background() 后面——background 会落在图层之外，
        // 结果是不透明的黑把整个界面盖死。直接给颜色本身带透明度最稳。
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = dim)),
        )

        val backdrop = when {
            ui.mode == UiMode.Browsing -> ui.pageBackdrop ?: backdropFocused
            ui.mode == UiMode.Editing -> backdropFocused
            else -> backdropNormal
        }
        // 文字/图标颜色按玻璃下方内容的明度自适应：浅底黑、深底白
        val contentColor = GlassTokens.contentColorFor(backdrop?.contentLuminance ?: 1f)

        // ---------- 流光：加载中绕边框顺时针跑 ----------
        // 页面上线后至少显示一小段，避免秒开时流光一闪而过、反而像闪烁
        var rimVisible by remember { mutableStateOf(false) }
        LaunchedEffect(ui.loading) {
            if (ui.loading) {
                rimVisible = true
            } else {
                kotlinx.coroutines.delay(RIM_MIN_VISIBLE_MS)
                rimVisible = false
            }
        }
        val spin = rememberInfiniteTransition(label = "rim")
        val spinValue by spin.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(GlassTokens.rimPeriodMs, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "rimSpin",
        )
        val rimAlpha by animateFloatAsState(
            targetValue = if (rimVisible) 1f else 0f,
            animationSpec = tween(450),
            label = "rimAlpha",
        )
        val rimProgress = if (rimAlpha > 0.01f) spinValue else null

        // ---------- 搜索 / 地址栏 ----------
        val barScale by animateFloatAsState(
            targetValue = if (ui.mode == UiMode.Editing) 1.03f else 1f,
            animationSpec = Springs.bouncy,
            label = "barScale",
        )
        val barCorner by animateDpAsState(
            targetValue = if (ui.mode == UiMode.Browsing) {
                GlassTokens.addressBarCorner
            } else {
                GlassTokens.searchBarCorner
            },
            animationSpec = Springs.snappyDp,
            label = "barCorner",
        )

        GlassSurface(
            modifier = Modifier
                .offset { IntOffset(bar.left.roundToInt(), bar.top.roundToInt()) }
                .size(
                    width = with(density) { bar.width.toDp() },
                    height = with(density) { bar.height.toDp() },
                )
                .graphicsLayer {
                    scaleX = barScale
                    scaleY = barScale
                    transformOrigin = TransformOrigin.Center
                }
                .impactEffect(impact)
                .pointerInput(ui.mode) {
                    if (ui.mode == UiMode.Browsing) {
                        var accumulated = 0f
                        detectVerticalDragGestures(
                            onDragStart = { accumulated = 0f },
                            onDragEnd = {
                                if (accumulated > 140f) {
                                    ui.mode = UiMode.Home
                                    haptic(Kind.Tick)
                                }
                                accumulated = 0f
                            },
                        ) { _, dragAmount ->
                            if (dragAmount > 0) accumulated += dragAmount
                        }
                    }
                },
            backdrop = backdrop,
            cornerRadius = barCorner,
            rimProgress = rimProgress,
            rimAlpha = rimAlpha,
        ) {
            AddressBarContent(
                text = ui.query,
                url = ui.currentUrl,
                editing = ui.mode != UiMode.Browsing,
                contentColor = contentColor,
                onTextChange = { ui.query = it },
                onSubmit = {
                    keyboard?.hide()
                    val text = ui.query.trim()
                    if (text.isNotEmpty()) {
                        ui.mode = UiMode.Browsing
                        onNavigate(text)
                        ui.query = ""
                    }
                },
                modifier = Modifier.fillMaxSize(),
                focusRequester = focusRequester,
            )
            // 非编辑态：整条玻璃可点，点一下进入编辑并把当前网址带进来
            if (ui.mode != UiMode.Editing) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(ui.mode) {
                            detectTapNoRipple {
                                ui.query = if (ui.mode == UiMode.Browsing) ui.currentUrl else ""
                                ui.mode = UiMode.Editing
                                haptic(Kind.Tick)
                            }
                        },
                )
            }
        }

        // 编辑态：自动聚焦并弹键盘（窗口级请求 IME，比 SoftwareKeyboardController 可靠）
        val rootView = LocalView.current
        LaunchedEffect(ui.mode) {
            val controller = androidx.core.view.WindowInsetsControllerCompat(activity.window, rootView)
            if (ui.mode == UiMode.Editing) {
                runCatching { focusRequester.requestFocus() }
                kotlinx.coroutines.delay(60)
                controller.show(androidx.core.view.WindowInsetsCompat.Type.ime())
            } else {
                controller.hide(androidx.core.view.WindowInsetsCompat.Type.ime())
            }
        }

        // ---------- 圆形菜单键 ----------
        GlassButton(
            onClick = {
                ui.menuOpen = true
                haptic(Kind.Tick)
            },
            backdrop = backdrop,
            modifier = Modifier
                .offset { IntOffset(btn.left.roundToInt(), btn.top.roundToInt()) }
                .impactEffect(impact),
        ) {
            HamburgerIcon(color = contentColor)
        }

        // ---------- 下拉菜单 ----------
        AnimatedVisibility(
            visible = ui.menuOpen,
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(140)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.10f))
                    .pointerInput(Unit) {
                        detectTapNoRipple { ui.menuOpen = false }
                    },
            )
        }
        // 外层 Box 负责对齐（align 必须作用于 BoxWithConstraints 的直接子节点，
        // 写在 AnimatedVisibility 里面是不生效的）
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(
                    top = with(density) { btn.bottom.toDp() } + 8.dp,
                    end = 12.dp,
                ),
        ) {
            AnimatedVisibility(
                visible = ui.menuOpen,
                enter = fadeIn(tween(140)) + scaleIn(
                    initialScale = 0.86f,
                    transformOrigin = TransformOrigin(1f, 0f),
                    animationSpec = Springs.snappy,
                ),
                exit = fadeOut(tween(120)) + scaleOut(
                    targetScale = 0.9f,
                    transformOrigin = TransformOrigin(1f, 0f),
                    animationSpec = Springs.snappy,
                ),
            ) {
                BrowserMenu(
                    backdrop = backdrop,
                    frost = GlassTokens.menuFrost,
                    onPick = { action ->
                        ui.menuOpen = false
                        onMenuAction(action)
                    },
                )
            }
        }
    }
}

/** 无涟漪的点击检测。 */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectTapNoRipple(
    onTap: () -> Unit,
) {
    detectTapGestures(onTap = { onTap() })
}

/** 流光最短可见时长：避免秒开时一闪而过 */
private const val RIM_MIN_VISIBLE_MS = 700L
