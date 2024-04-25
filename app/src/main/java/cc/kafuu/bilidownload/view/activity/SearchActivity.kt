package cc.kafuu.bilidownload.view.activity

import android.view.inputmethod.InputMethodManager
import cc.kafuu.bilidownload.BR
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.core.CoreActivity
import cc.kafuu.bilidownload.databinding.ActivitySearchBinding
import cc.kafuu.bilidownload.view.fragment.SearchListFragment
import cc.kafuu.bilidownload.viewmodel.activity.SearchViewModel


class SearchActivity : CoreActivity<ActivitySearchBinding, SearchViewModel>(
    SearchViewModel::class.java,
    R.layout.activity_search,
    BR.viewModel
) {
    private var mSearchListFragment: SearchListFragment? = null

    override fun initViews() {
        setImmersionStatusBar()
        initFragment()
        initListener()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        mViewDataBinding.etSearchContent.apply {
            requestFocus()
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(
                this,
                InputMethodManager.SHOW_IMPLICIT
            )
        }
    }

    private fun initListener() {
        mViewModel.searchRequestLiveData.observe(this) {
            mSearchListFragment?.doSearch(it)
        }
    }

    private fun initFragment() {
        mSearchListFragment = SearchListFragment.newInstance()
    }
}