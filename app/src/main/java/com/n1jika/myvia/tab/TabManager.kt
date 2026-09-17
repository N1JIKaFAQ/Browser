package com.n1jika.myvia.tab

import java.util.concurrent.atomic.AtomicLong

/** 一个标签页的数据模型。currentUrl 由页面回调持续更新。 */
class TabItem(
    val id: Long,
    var currentUrl: String,
    var title: String = "",
)

/**
 * 标签页列表管理。
 * 当前实现为"会话内标签"，应用重启后从首页重新开始；
 * 持久化（恢复上次会话）列入 README 中的后续路线。
 */
class TabManager {

    companion object {
        private val nextId = AtomicLong(1)
    }

    val tabs = mutableListOf<TabItem>()

    fun addTab(url: String, index: Int = tabs.size): TabItem {
        val tab = TabItem(nextId.getAndIncrement(), url)
        tabs.add(index.coerceIn(0, tabs.size), tab)
        return tab
    }

    fun removeTab(id: Long) {
        tabs.removeAll { it.id == id }
    }

    fun findPosition(id: Long): Int = tabs.indexOfFirst { it.id == id }

    fun findTab(id: Long): TabItem? = tabs.firstOrNull { it.id == id }

    val size: Int get() = tabs.size
}
