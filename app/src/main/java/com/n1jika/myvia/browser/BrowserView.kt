package com.n1jika.myvia.browser

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.webkit.WebSettings
import android.webkit.WebView
import com.n1jika.myvia.Prefs

/**
 * 全站共享配置的 WebView。
 * 每个标签页一个实例，配置随 Prefs 实时刷新。
 */
@SuppressLint("ViewConstructor")
class BrowserView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : WebView(context, attrs) {

    val engine: BrowserEngine by lazy {
        BrowserEngine(
            view = this,
            onPageStarted = { url -> tab?.currentUrl = url },
            onPageFinished = { url -> tab?.currentUrl = url },
            onProgress = { progress ->
                (context as? BrowserCallback)?.onProgressChanged(this, progress)
            },
            onTitle = { title ->
                tab?.title = title
                (context as? BrowserCallback)?.onTitleChanged(this, title)
            },
            onIcon = { icon ->
                (context as? BrowserCallback)?.onIconChanged(this, icon)
            },
        )
    }

    /** 所属标签页（由 TabAdapter 绑定时注入）。 */
    var tab: com.n1jika.myvia.tab.TabItem? = null

    interface BrowserCallback {
        fun onProgressChanged(view: BrowserView, progress: Int)
        fun onTitleChanged(view: BrowserView, title: String)
        fun onIconChanged(view: BrowserView, icon: android.graphics.Bitmap?)
    }

    init {
        isVerticalScrollBarEnabled = false
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            loadWithOverviewMode = true
            useWideViewPort = true
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        engine.attach()
    }

    /** 设置变更后重建 UA / 无图 / 深色补偿。 */
    fun applyPrefs() {
        settings.userAgentString = Prefs.userAgent(context, WebSettings.getDefaultUserAgent(context))
        settings.blockNetworkImage = Prefs.noImageEnabled(context)
        settings.textZoom = 100
        engine.applyDarkMode()
    }
}
