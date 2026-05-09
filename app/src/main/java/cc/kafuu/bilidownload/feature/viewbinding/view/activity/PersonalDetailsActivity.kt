package cc.kafuu.bilidownload.feature.viewbinding.view.activity

import android.content.Intent
import android.view.View
import android.view.inputmethod.InputMethodManager
import cc.kafuu.bilidownload.BR
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.CommonLibs
import cc.kafuu.bilidownload.common.adapter.FragmentAdapter
import cc.kafuu.bilidownload.common.core.viewbinding.CoreActivity
import cc.kafuu.bilidownload.common.core.viewbinding.CoreFragmentBuilder
import cc.kafuu.bilidownload.common.manager.AccountManager
import cc.kafuu.bilidownload.common.utils.bindOnEditorAction
import cc.kafuu.bilidownload.databinding.ActivityPersonalDetailsBinding
import cc.kafuu.bilidownload.feature.viewbinding.view.fragment.FavoriteListFragment
import cc.kafuu.bilidownload.feature.viewbinding.view.fragment.ManuscriptFragment
import cc.kafuu.bilidownload.feature.viewbinding.view.fragment.WatchHistoryFragment
import cc.kafuu.bilidownload.feature.viewbinding.viewmodel.activity.PersonalDetailsViewModel
import com.google.android.material.tabs.TabLayoutMediator

class PersonalDetailsActivity :
    CoreActivity<ActivityPersonalDetailsBinding, PersonalDetailsViewModel>(
        PersonalDetailsViewModel::class.java,
        R.layout.activity_personal_details,
        BR.viewModel
    ) {
    private var mManuscriptPosition = 0

    companion object {
        private const val KEY_MID = "mid"

        fun buildIntent(mid: Long) = Intent().apply {
            putExtra(KEY_MID, mid)
        }
    }

    override fun initViews() {
        setImmersionStatusBar()
        val mid = intent.getLongExtra(KEY_MID, 0)
        mViewDataBinding.initViews(mid)
        mViewDataBinding.initSearchViews()
        mViewModel.init(mid)
    }

    private fun ActivityPersonalDetailsBinding.initViews(mid: Long) {
        val fragments = getFragmentBuilders(mid)
        vp2Personal.apply {
            adapter =
                FragmentAdapter(supportFragmentManager, lifecycle, fragments.map { it.second })
        }
        TabLayoutMediator(tlPageSelector, vp2Personal) { tab, position ->
            tab.text = CommonLibs.getString(fragments[position].first)
        }.attach()
    }

    private fun ActivityPersonalDetailsBinding.initSearchViews() {
        ivManuscriptSearch.setOnClickListener {
            toggleManuscriptSearch()
        }
        ivClearManuscriptSearch.setOnClickListener {
            etManuscriptSearchContent.setText("")
            searchManuscript(null)
        }
        tvManuscriptSearch.setOnClickListener {
            searchManuscript(etManuscriptSearchContent.text?.toString())
        }
        bindOnEditorAction(etManuscriptSearchContent) {
            searchManuscript(etManuscriptSearchContent.text?.toString())
        }
    }

    private fun ActivityPersonalDetailsBinding.toggleManuscriptSearch() {
        val showSearch = cardManuscriptSearch.visibility != View.VISIBLE
        cardManuscriptSearch.visibility = if (showSearch) View.VISIBLE else View.GONE
        if (showSearch) {
            vp2Personal.currentItem = mManuscriptPosition
            etManuscriptSearchContent.requestFocus()
            etManuscriptSearchContent.post {
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(
                    etManuscriptSearchContent,
                    InputMethodManager.SHOW_IMPLICIT
                )
            }
        } else {
            hideSoftInput()
        }
    }

    private fun searchManuscript(keyword: String?) {
        mViewDataBinding.apply {
            vp2Personal.currentItem = mManuscriptPosition
            etManuscriptSearchContent.clearFocus()
            hideSoftInput()
            vp2Personal.post { searchManuscriptWhenReady(keyword) }
        }
    }

    private fun searchManuscriptWhenReady(keyword: String?, retryCount: Int = 3) {
        val fragment = getManuscriptFragment()
        if (fragment != null) {
            fragment.search(keyword)
            return
        }
        if (retryCount > 0) {
            mViewDataBinding.vp2Personal.postDelayed({
                searchManuscriptWhenReady(keyword, retryCount - 1)
            }, 100)
        }
    }

    private fun hideSoftInput() {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(
            mViewDataBinding.etManuscriptSearchContent.windowToken, 0
        )
    }

    private fun getManuscriptFragment() =
        supportFragmentManager.findFragmentByTag("f$mManuscriptPosition") as? ManuscriptFragment

    private fun PersonalDetailsViewModel.init(mid: Long) {
        initData(mid)
    }

    private fun getFragmentBuilders(mid: Long): List<Pair<Int, CoreFragmentBuilder<*>>> {
        val isMyself = mid == AccountManager.accountLiveData.value?.mid

        val fragmentBuilders = mutableListOf<Pair<Int, CoreFragmentBuilder<*>>>()
        if (isMyself) {
            fragmentBuilders.add(R.string.personal_tab_history to WatchHistoryFragment.builder())
        }
        mManuscriptPosition = fragmentBuilders.size
        fragmentBuilders.add(R.string.personal_tab_manuscript to ManuscriptFragment.builder(mid))
        fragmentBuilders.add(R.string.personal_tab_favorite to FavoriteListFragment.builder(mid))

        return fragmentBuilders
    }

}
