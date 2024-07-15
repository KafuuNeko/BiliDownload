package cc.kafuu.bilidownload.common.adapter

import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import cc.kafuu.bilidownload.common.core.CoreFragmentBuilder


class FragmentAdapter(
    fragmentManager: FragmentManager,
    lifecycle: Lifecycle,
    private val fragmentBuilders: List<CoreFragmentBuilder<*>>
) : FragmentStateAdapter(fragmentManager, lifecycle) {
    override fun createFragment(position: Int) = fragmentBuilders[position].build()
    override fun getItemCount() = fragmentBuilders.size
}
