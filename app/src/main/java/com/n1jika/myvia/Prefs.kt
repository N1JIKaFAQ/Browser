package com.n1jika.myvia

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager

/** SharedPreferences 键与全局配置访问。 */
object Prefs {
    const val HOME_URL = "pref_home_url"
    const val AD_BLOCK = "pref_ad_block"
    const val THEME = "pref_theme"
    const val UA_MODE = "pref_ua_mode"
    const val NO_IMAGE = "pref_no_image"
    const val CUSTOM_BLOCK_HOSTS = "pref_custom_block_hosts"
    /** 用户自选的主页背景图 URI（持久化授权） */
    const val BACKGROUND_URI = "pref_background_uri"

    /** UA 模式取值。 */
    const val UA_DEFAULT = "0"          // Via 风格：隐藏标识
    const val UA_ANDROID = "1"          // 默认 Android 标识
    const val UA_DESKTOP = "2"          // 桌面站点标识

    /** 主题取值（与 ListPreference entries 顺序一致）。 */
    const val THEME_SYSTEM = "0"
    const val THEME_LIGHT = "1"
    const val THEME_DARK = "2"

    fun get(context: Context): SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context)

    fun homeUrl(context: Context): String =
        get(context).getString(HOME_URL, "https://www.baidu.com")!!

    fun adBlockEnabled(context: Context): Boolean =
        get(context).getBoolean(AD_BLOCK, true)

    fun noImageEnabled(context: Context): Boolean =
        get(context).getBoolean(NO_IMAGE, false)

    /** 用户自选的主页背景图；未设置返回 null。 */
    fun backgroundUri(context: Context): android.net.Uri? =
        get(context).getString(BACKGROUND_URI, null)?.takeIf { it.isNotBlank() }
            ?.let { android.net.Uri.parse(it) }

    fun setBackgroundUri(context: Context, uri: android.net.Uri?) {
        get(context).edit().apply {
            if (uri == null) remove(BACKGROUND_URI) else putString(BACKGROUND_URI, uri.toString())
        }.apply()
    }

    fun userAgent(context: Context, default: String): String =
        when (get(context).getString(UA_MODE, UA_DEFAULT)) {
            UA_ANDROID -> default
            UA_DESKTOP -> default
                .replace("Mobile", "Tablet; rv:109.0")
                .replace("Android", "Windows NT 10.0; Win64; x64")
            else -> default
                .replace("; wv", "")
                .replace(Regex("Mobile.*Version/[^ ]*"), "Mobile")
        }

    fun applyTheme(context: Context) {
        val mode = when (get(context).getString(THEME, THEME_SYSTEM)) {
            THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }
}
