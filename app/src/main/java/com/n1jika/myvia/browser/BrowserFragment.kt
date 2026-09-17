package com.n1jika.myvia.browser

import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.n1jika.myvia.MainActivity
import com.n1jika.myvia.databinding.FragmentBrowserBinding
import com.n1jika.myvia.tab.TabItem

/** 承载单个标签页 WebView 的 Fragment。 */
class BrowserFragment : Fragment() {

    private var _binding: FragmentBrowserBinding? = null
    private val binding get() = _binding!!

    val webView: BrowserView get() = binding.browserView

    /** 由 [com.n1jika.myvia.tab.TabManager] 创建并传入。 */
    var tab: TabItem? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentBrowserBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val host = requireActivity() as? MainActivity
        // 优先复用 TabManager 中已有的 TabItem，保证标题/URL 状态共享
        tab = arguments?.let { args ->
            if (args.containsKey(ARG_TAB_ID)) {
                host?.tabManager?.findTab(args.getLong(ARG_TAB_ID))
            } else {
                null
            }
        } ?: TabItem(0, "about:blank")
        webView.tab = tab

        webView.applyPrefs()
        val url = savedInstanceState?.getString(STATE_URL)
            ?: arguments?.getString(ARG_URL)
            ?: tab?.currentUrl
            ?: "about:blank"
        if (url.isNotBlank() && url != "about:blank") webView.loadUrl(url)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.url?.let { outState.putString(STATE_URL, it) }
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onPause() {
        webView.onPause()
        super.onPause()
    }

    override fun onDestroyView() {
        // 释放 WebView：从父布局移除后 destroy，避免 WindowLeaked 与内存泄漏
        webView.apply {
            stopLoading()
            (parent as? ViewGroup)?.removeView(this)
            destroy()
        }
        _binding = null
        super.onDestroyView()
    }

    fun loadUrl(url: String) {
        webView.loadUrl(url)
    }

    /** 广告拦截等设置变更后重建 UA/无图配置。 */
    fun refreshPrefs() {
        webView.applyPrefs()
    }

    companion object {
        const val ARG_URL = "arg_url"
        const val ARG_TAB_ID = "arg_tab_id"
        private const val STATE_URL = "state_url"

        fun newInstance(url: String): BrowserFragment = BrowserFragment().apply {
            arguments = Bundle().apply { putString(ARG_URL, url) }
        }
    }
}
