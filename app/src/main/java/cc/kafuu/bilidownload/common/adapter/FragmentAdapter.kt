package cc.kafuu.bilidownload.common.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter


class FragmentAdapter(fragmentManager: FragmentManager, lifecycle: Lifecycle) :
    FragmentStateAdapter(fragmentManager, lifecycle) {
    private val fragmentList: MutableList<Fragment> = ArrayList()
    fun addFragmentView(fragments: List<Fragment>?) {
        if (fragments != null) {
            fragmentList.clear()
            fragmentList.addAll(fragments)
        }
    }

    override fun createFragment(position: Int): Fragment = fragmentList[position]

    override fun getItemCount(): Int = fragmentList.size
}

