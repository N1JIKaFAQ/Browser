package com.n1jika.myvia.browser

import android.graphics.Bitmap
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.n1jika.myvia.Prefs

/**
 * 每个标签页一套 WebViewClient/WebChromeClient（Via 的 per-tab 思想）。
 * 负责：广告拦截、地址栏同步、标题/进度回调、深色模式补偿。
 */
class BrowserEngine(
    private val view: BrowserView,
    private val onPageStarted: (url: String) -> Unit,
    private val onPageFinished: (url: String) -> Unit,
    private val onProgress: (progress: Int) -> Unit,
    private val onTitle: (title: String) -> Unit,
    private val onIcon: (icon: Bitmap?) -> Unit,
) {
    private val webView: WebView get() = view

    fun attach() {
        webView.webViewClient = Client()
        webView.webChromeClient = Chrome()
        applyDarkMode()
    }

    /** 按设置补偿不支持 force-dark 的站点。 */
    fun applyDarkMode() {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            val dark = when (Prefs.get(view.context).getString(Prefs.THEME, Prefs.THEME_SYSTEM)) {
                Prefs.THEME_DARK -> true
                Prefs.THEME_LIGHT -> false
                else -> {
                    val config = view.context.resources.configuration
                    val night = config.uiMode and
                        android.content.res.Configuration.UI_MODE_NIGHT_MASK
                    night == android.content.res.Configuration.UI_MODE_NIGHT_YES
                }
            }
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.settings, dark)
        }
    }

    private inner class Client : WebViewClient() {
        override fun shouldInterceptRequest(
            view: WebView,
            request: android.webkit.WebResourceRequest,
        ): android.webkit.WebResourceResponse? {
            if (Prefs.adBlockEnabled(this@BrowserEngine.view.context) &&
                AdBlocker.shouldBlock(this@BrowserEngine.view.context, request.url.toString())
            ) {
                return android.webkit.WebResourceResponse("text/plain", "utf-8", null)
            }
            return null
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            onPageStarted(url)
            onIcon(favicon)
        }

        override fun onPageFinished(view: WebView, url: String) {
            onPageFinished(url)
            if (Prefs.get(view.context).getString(Prefs.THEME, Prefs.THEME_SYSTEM)
                == Prefs.THEME_DARK
            ) {
                // 部分站点不跟随强制深色，注入背景兜底
                view.evaluateJavascript(
                    "(function(){if(navigator.userAgent.includes('Android'))" +
                        "{document.documentElement.style.colorScheme='dark'}})();",
                    null,
                )
            }
        }
    }

    private inner class Chrome : android.webkit.WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            onProgress(newProgress)
        }

        override fun onReceivedTitle(view: WebView, title: String?) {
            if (!title.isNullOrBlank()) onTitle(title)
        }

        override fun onReceivedIcon(view: WebView, icon: Bitmap?) {
            onIcon(icon)
        }
    }
}
