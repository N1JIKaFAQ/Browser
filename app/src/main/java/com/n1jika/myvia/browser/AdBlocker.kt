package com.n1jika.myvia.browser

import android.content.Context
import com.n1jika.myvia.Prefs
import java.util.Locale

/**
 * 轻量级广告拦截：按请求主机名匹配内置规则表 + 用户自定义黑名单。
 * 骨架版本内置少量常见广告/统计域名，后续可升级为订阅 ruleset（EasyList 精简版）。
 */
object AdBlocker {

    private val BLOCKED_HOSTS = setOf(
        "doubleclick.net",
        "googlesyndication.com",
        "google-analytics.com",
        "adsense.google.com",
        "admob.com",
        "facebook.net",
        "cnzz.com",
        "hm.baidu.com",
        "tj.baidu.com",
        "admaster.com.cn",
        "sigmob.com",
        "pangle.cn",
        "tanx.com",
        "irs01.com",
        "miaozhen.com",
    )

    /** 命中广告规则返回 true（内置表 + 用户逐条添加的主机名）。 */
    fun shouldBlock(context: Context, url: String): Boolean {
        val host = runCatching {
            java.net.URI(url).host?.lowercase(Locale.ROOT)
        }.getOrNull() ?: return false

        if (BLOCKED_HOSTS.any { host == it || host.endsWith(".$it") }) return true

        val custom = Prefs.get(context)
            .getString(Prefs.CUSTOM_BLOCK_HOSTS, "")!!
            .lineSequence()
            .map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.isNotEmpty() }
        return custom.any { host == it || host.endsWith(".$it") }
    }
}
