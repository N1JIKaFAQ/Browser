package com.n1jika.myvia

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.WindowCompat
import androidx.viewpager2.widget.ViewPager2
import com.n1jika.myvia.browser.BrowserFragment
import com.n1jika.myvia.browser.BrowserView
import com.n1jika.myvia.browser.UrlUtils
import com.n1jika.myvia.settings.SettingsActivity
import com.n1jika.myvia.tab.TabAdapter
import com.n1jika.myvia.tab.TabItem
import com.n1jika.myvia.tab.TabManager
import com.n1jika.myvia.ui.BrowserRoot
import com.n1jika.myvia.ui.BrowserUiState
import com.n1jika.myvia.ui.ScreenCapture
import com.n1jika.myvia.ui.UiMode
import com.n1jika.myvia.ui.menu.MenuAction

class MainActivity : AppCompatActivity(), BrowserView.BrowserCallback {

    val tabManager = TabManager()
    val ui = BrowserUiState()

    private lateinit var tabAdapter: TabAdapter
    private var pager: ViewPager2? = null

    private val currentTab: TabItem?
        get() = pager?.let { tabManager.tabs.getOrNull(it.currentItem) }

    private val currentWebView: BrowserView?
        get() = currentTab?.let { tab ->
            val fragment = supportFragmentManager
                .findFragmentByTag("f${tab.id}") as? BrowserFragment
            fragment?.webView
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        Prefs.applyTheme(this)
        super.onCreate(savedInstanceState)
        // 内容铺满全屏（含状态栏/导航栏区域），玻璃才能压着背景延伸
        WindowCompat.setDecorFitsSystemWindows(window, false)

        tabAdapter = TabAdapter(this)

        val content = ComposeView(this).apply {
            setContent {
                BrowserRoot(
                    activity = this@MainActivity,
                    ui = ui,
                    tabManager = tabManager,
                    tabAdapter = tabAdapter,
                    onNavigate = ::navigate,
                    onMenuAction = ::handleMenuAction,
                    onPagerCreated = { created ->
                        pager = created
                        if (tabManager.size == 0) openNewTab(Prefs.homeUrl(this@MainActivity))
                    },
                )
            }
        }
        setContentView(content)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    ui.menuOpen -> ui.menuOpen = false

                    ui.mode == UiMode.Editing ->
                        ui.mode = if (ui.currentUrl.isNotBlank()) UiMode.Browsing else UiMode.Home

                    ui.mode == UiMode.Browsing && currentWebView?.canGoBack() == true ->
                        currentWebView?.goBack()

                    ui.mode == UiMode.Browsing -> ui.mode = UiMode.Home

                    tabManager.size > 1 -> currentTab?.let { closeTab(it) }

                    else -> finish()
                }
            }
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_VIEW) {
            intent.dataString?.let { url ->
                ui.mode = UiMode.Browsing
                ui.currentUrl = url
                currentWebView?.loadUrl(url) ?: openNewTab(url)
            }
        }
    }

    // ---------- 导航 ----------

    private fun navigate(input: String) {
        val url = UrlUtils.parseUserInput(input)
        val view = currentWebView
        if (view == null) {
            openNewTab(url)
            return
        }
        currentTab?.currentUrl = url
        ui.currentUrl = url
        view.loadUrl(url)
    }

    private fun capturePageBackdrop() {
        ScreenCapture.captureBackdrop(window) { backdrop ->
            if (backdrop != null) ui.pageBackdrop = backdrop
        }
    }

    // ---------- 标签 ----------

    private fun openNewTab(url: String) {
        val tab = tabManager.addTab(url)
        tabAdapter.addTab(tab, url)
        tabAdapter.notifyItemInserted(tabAdapter.itemCount - 1)
        pager?.currentItem = tabAdapter.itemCount - 1
        ui.currentUrl = url
    }

    private fun closeTab(tab: TabItem) {
        if (tabManager.size <= 1) {
            finish()
            return
        }
        tabManager.removeTab(tab.id)
        tabAdapter.removeTab(tab)
    }

    // ---------- 菜单动作 ----------

    private fun handleMenuAction(action: MenuAction) {
        when (action) {
            MenuAction.Share -> {
                val url = ui.currentUrl
                if (url.isNotBlank()) {
                    startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, url)
                            },
                            getString(R.string.menu_share),
                        ),
                    )
                }
            }

            MenuAction.DesktopMode -> {
                val prefs = Prefs.get(this)
                val desktop = prefs.getString(Prefs.UA_MODE, Prefs.UA_DEFAULT) == Prefs.UA_DESKTOP
                prefs.edit()
                    .putString(
                        Prefs.UA_MODE,
                        if (desktop) Prefs.UA_ANDROID else Prefs.UA_DESKTOP,
                    )
                    .apply()
                currentWebView?.applyPrefs()
                currentWebView?.reload()
                Toast.makeText(
                    this,
                    if (desktop) "已切回手机模式" else "已切换电脑模式",
                    Toast.LENGTH_SHORT,
                ).show()
            }

            MenuAction.Settings -> startActivity(Intent(this, SettingsActivity::class.java))

            // 以下功能在后续阶段实现（P5），先给出明确反馈而不是静默失败
            MenuAction.Bookmarks, MenuAction.History, MenuAction.Downloads,
            MenuAction.Incognito, MenuAction.AddBookmark, MenuAction.Toolbox,
            -> Toast.makeText(this, "${labelOf(action)}功能开发中", Toast.LENGTH_SHORT).show()
        }
    }

    private fun labelOf(action: MenuAction): String = when (action) {
        MenuAction.Bookmarks -> "书签"
        MenuAction.History -> "历史"
        MenuAction.Downloads -> "下载"
        MenuAction.Incognito -> "隐身"
        MenuAction.AddBookmark -> "添加书签"
        MenuAction.Toolbox -> "工具箱"
        else -> ""
    }

    // ---------- BrowserView 回调 ----------

    override fun onProgressChanged(view: BrowserView, progress: Int) {
        runOnUiThread {
            if (view === currentWebView) {
                ui.loading = progress in 1..99
                view.url?.let { if (it != ui.currentUrl) ui.currentUrl = it }
                if (progress >= 100 && ui.mode == UiMode.Browsing) {
                    ui.loading = false
                    capturePageBackdrop()
                }
            }
        }
    }

    override fun onTitleChanged(view: BrowserView, title: String) {
        runOnUiThread {
            if (view === currentWebView && ui.mode == UiMode.Browsing) {
                view.url?.let { if (it != ui.currentUrl) ui.currentUrl = it }
            }
        }
    }

    override fun onIconChanged(view: BrowserView, icon: Bitmap?) = Unit
}
