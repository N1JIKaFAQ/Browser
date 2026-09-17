package com.n1jika.myvia.browser

/** 地址栏输入解析：网址直达，其余交给搜索引擎。 */
object UrlUtils {

    private const val SEARCH_ENGINE = "https://www.baidu.com/s?wd="

    fun parseUserInput(input: String): String {
        val text = input.trim()
        if (text.isEmpty()) return "about:blank"

        // 已带协议
        if (text.startsWith("http://", true) || text.startsWith("https://", true)) return text
        // 快捷协议
        if (text.startsWith("file://", true) || text.startsWith("about:", true)) return text

        // 像域名/IP：包含点且无空格，或以 localhost 开头
        val looksLikeHost = !text.contains(" ") &&
            (Regex("^[^/]+\\.[a-zA-Z]{2,}(:\\d+)?(/.*)?$").matches(text) ||
                text.startsWith("localhost"))
        if (looksLikeHost) return "https://$text"

        // 单字主机名也尝试直达（如 intranet 主机）
        if (!text.contains(" ") && text.all { it.isLetterOrDigit() || it == '-' } && text.length > 1 &&
            !Regex("^[0-9]+$").matches(text)
        ) {
            return "http://$text"
        }

        return SEARCH_ENGINE + java.net.URLEncoder.encode(text, "UTF-8")
    }
}
