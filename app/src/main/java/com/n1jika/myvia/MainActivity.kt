package com.n1jika.myvia

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService
import androidx.viewpager2.widget.ViewPager2
import com.n1jika.myvia.browser.BrowserFragment
import com.n1jika.myvia.browser.BrowserView
import com.n1jika.myvia.browser.UrlUtils
import com.n1jika.myvia.databinding.ActivityMainBinding
import com.n1jika.myvia.settings.SettingsActivity
import com.n1jika.myvia.tab.TabAdapter
import com.n1jika.myvia.tab.TabItem
import com.n1jika.myvia.tab.TabManager

class MainActivity : AppCompatActivity(), BrowserView.BrowserCallback {

    val tabManager = TabManager()
    private lateinit var binding: ActivityMainBinding
    private lateinit var tabAdapter: TabAdapter

    private val currentFragment: BrowserFragment?
        get() = supportFragmentManager.fragments
            .firstOrNull { it is BrowserFragment && it.isResumed } as? BrowserFragment

    private val currentView: BrowserView? get() = currentFragment?.webView

    private val pagerListener = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            updateAddressBar()
            binding.progressBar.visibility = android.view.View.GONE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Prefs.applyTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        tabAdapter = TabAdapter(this)
        binding.pager.adapter = tabAdapter
        binding.pager.isUserInputEnabled = false
        binding.pager.registerOnPageChangeCallback(pagerListener)

        setupAddressBar()
        setupBottomBar()

        if (tabManager.size == 0) {
            openNewTab(Prefs.homeUrl(this))
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val view = currentView
                when {
                    view == null -> finish()
                    view.canGoBack() -> view.goBack()
                    tabManager.size > 1 -> closeTab(tabManager.tabs[binding.pager.currentItem])
                    else -> finish()
                }
            }
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_VIEW) {
            intent.dataString?.let { openNewTab(it) }
        }
    }

    override fun onResume() {
        super.onResume()
        currentFragment?.refreshPrefs()
        Prefs.applyTheme(this)
    }

    // ---------- 地址栏 ----------

    private fun setupAddressBar() {
        binding.addressBar.setOnEditorActionListener { _, actionId, keyEvent ->
            val submit = actionId == EditorInfo.IME_ACTION_GO ||
                actionId == EditorInfo.IME_ACTION_DONE ||
                (keyEvent?.keyCode == KeyEvent.KEYCODE_ENTER &&
                    keyEvent.action == KeyEvent.ACTION_DOWN)
            if (submit) {
                navigate(binding.addressBar.text.toString())
                hideKeyboard()
                true
            } else {
                false
            }
        }
        // 点击地址栏时全选并显示当前 URL（Via 习惯）
        binding.addressBar.setOnClickListener {
            binding.addressBar.selectAll()
        }
        updateAddressBar()
    }

    private fun navigate(input: String) {
        val url = UrlUtils.parseUserInput(input)
        currentView?.loadUrl(url) ?: openNewTab(url)
    }

    private fun updateAddressBar() {
        val tab = tabManager.tabs.getOrNull(binding.pager.currentItem)
        if (tab == null) {
            binding.addressBar.setText("")
            binding.addressBar.hint = getString(R.string.app_name)
            return
        }
        // 用户正在输入时不覆盖
        if (binding.addressBar.hasFocus()) {
            binding.addressBar.hint = tab.title
            return
        }
        binding.addressBar.setText(tab.currentUrl)
        binding.addressBar.hint = tab.title
    }

    private fun hideKeyboard() {
        getSystemService<InputMethodManager>()
            ?.hideSoftInputFromWindow(binding.addressBar.windowToken, 0)
    }

    // ---------- 底部工具栏 ----------

    private fun setupBottomBar() {
        binding.btnBack.setOnClickListener {
            currentView?.let { if (it.canGoBack()) it.goBack() }
        }
        binding.btnForward.setOnClickListener {
            currentView?.let { if (it.canGoForward()) it.goForward() }
        }
        binding.btnHome.setOnClickListener {
            currentView?.loadUrl(Prefs.homeUrl(this))
        }
        binding.btnTabs.setOnClickListener { showTabsDialog() }
        binding.btnMenu.setOnClickListener { showMenu() }
    }

    private fun showMenu() {
        val items = listOf(
            getString(R.string.menu_refresh),
            getString(R.string.menu_share),
            getString(R.string.menu_find_in_page),
            getString(R.string.menu_add_adblock),
            getString(R.string.menu_settings),
            getString(R.string.menu_about),
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.menu)
            .setItems(items.toTypedArray()) { _, which ->
                when (which) {
                    0 -> currentView?.reload()
                    1 -> currentView?.url?.let { shareUrl(it) }
                    2 -> showFindDialog()
                    3 -> addCurrentHostToBlocklist()
                    4 -> startActivity(Intent(this, SettingsActivity::class.java))
                    5 -> showAbout()
                }
            }
            .show()
    }

    private fun shareUrl(url: String) {
        startActivity(Intent.createChooser(Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
        }, getString(R.string.menu_share)))
    }

    private fun showFindDialog() {
        val input = EditText(this)
        input.hint = getString(R.string.find_hint)
        AlertDialog.Builder(this)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                // WebView.findAddress 已从公开 API 移除，用 Chromium 内建的 window.find
                val query = org.json.JSONObject.quote(input.text.toString())
                currentView?.evaluateJavascript("window.find($query)", null)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** 将当前页面主机加入自定义拦截列表（骨架版存本地，后续可导出规则）。 */
    private fun addCurrentHostToBlocklist() {
        val host = runCatching { java.net.URI(currentView?.url ?: "").host }.getOrNull()
        if (host.isNullOrBlank()) return
        val prefs = Prefs.get(this)
        val list = prefs.getString(Prefs.CUSTOM_BLOCK_HOSTS, "")!!.split("\n")
            .filter { it.isNotBlank() }
        if (!list.contains(host)) {
            prefs.edit()
                .putString(Prefs.CUSTOM_BLOCK_HOSTS, (list + host).joinToString("\n"))
                .apply()
        }
        currentView?.reload()
    }

    private fun showAbout() {
        AlertDialog.Builder(this)
            .setTitle(R.string.app_name)
            .setMessage("MyVia v${BuildConfig.VERSION_NAME}\n\n一个为自己定制的轻量浏览器。")
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    // ---------- 标签页 ----------

    private fun openNewTab(url: String) {
        val tab = tabManager.addTab(url)
        tabAdapter.addTab(tab, url)
        tabAdapter.notifyItemInserted(tabAdapter.itemCount - 1)
        binding.pager.currentItem = tabAdapter.itemCount - 1
        updateTabCount()
    }

    private fun closeTab(tab: TabItem) {
        if (tabManager.size <= 1) {
            finish()
            return
        }
        tabManager.removeTab(tab.id)
        tabAdapter.removeTab(tab)
        updateTabCount()
    }

    private fun showTabsDialog() {
        val titles = tabManager.tabs.map {
            val n = if (it.title.isNotBlank()) it.title else getString(R.string.untitled)
            "$n\n${it.currentUrl}"
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.tabs)
            .setItems(titles) { _, which -> binding.pager.currentItem = which }
            .setNeutralButton(R.string.new_tab) { _, _ -> openNewTab(Prefs.homeUrl(this)) }
            .setOnKeyListener { _, keyCode, event ->
                // 支持在标签列表里滑动删除（骨架版暂不实现）
                keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP
            }
            .show()
    }

    private fun updateTabCount() {
        binding.tabCount.text = tabManager.size.toString()
    }

    // ---------- BrowserView 回调 ----------

    override fun onProgressChanged(view: BrowserView, progress: Int) {
        runOnUiThread {
            if (view === currentView) {
                binding.progressBar.let {
                    it.progress = progress
                    it.visibility = if (progress in 1..99) {
                        android.view.View.VISIBLE
                    } else {
                        android.view.View.GONE
                    }
                }
            }
        }
    }

    override fun onTitleChanged(view: BrowserView, title: String) {
        runOnUiThread {
            if (view.tab?.id?.let { tabManager.findTab(it) } != null) updateAddressBar()
        }
    }

    override fun onIconChanged(view: BrowserView, icon: Bitmap?) {
        // 骨架版暂不显示站点图标，留给后续标签页 UI
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
