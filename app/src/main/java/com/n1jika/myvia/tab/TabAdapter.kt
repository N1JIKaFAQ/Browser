package com.n1jika.myvia.tab

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.n1jika.myvia.browser.BrowserFragment

/**
 * ViewPager2 标签适配器。
 *
 * 标签用稳定 id 与 position 解耦：删除中间标签时，其余标签的 id 不变，
 * FragmentStateAdapter 因而不会误删/误复用 Fragment。
 * URL 一律通过 Fragment arguments 传递，Activity 重建后由 FragmentManager 自动恢复。
 */
class TabAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    /** position -> 稳定 id。与 TabManager.tabs 顺序保持一致。 */
    private val ids = mutableListOf<Long>()

    /** 稳定 id -> 初始 URL（仅在 Fragment 首次创建时使用）。 */
    private val urls = HashMap<Long, String>()

    fun hasFragmentFor(tabId: Long): Boolean = ids.contains(tabId)

    fun addTab(tab: TabItem, url: String, index: Int = ids.size) {
        ids.add(index.coerceIn(0, ids.size), tab.id)
        urls[tab.id] = url
    }

    fun removeTab(tab: TabItem) {
        val index = ids.indexOf(tab.id)
        if (index >= 0) {
            ids.removeAt(index)
            notifyItemRemoved(index)
        }
        urls.remove(tab.id)
    }

    override fun getItemCount(): Int = ids.size

    override fun getItemId(position: Int): Long = ids[position]

    override fun containsItem(itemId: Long): Boolean = ids.contains(itemId)

    override fun createFragment(position: Int): Fragment {
        val tabId = ids[position]
        return BrowserFragment.newInstance(urls[tabId] ?: "about:blank").also {
            it.arguments?.putLong(BrowserFragment.ARG_TAB_ID, tabId)
        }
    }
}
