package cc.kafuu.bilidownload.feature.viewbinding.view.activity

import android.content.Intent
import android.text.TextUtils
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import cc.kafuu.bilidownload.BR
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.CommonLibs.requireContext
import cc.kafuu.bilidownload.common.constant.SearchType
import cc.kafuu.bilidownload.common.core.viewbinding.CoreActivity
import cc.kafuu.bilidownload.common.utils.BiliAddressParser
import cc.kafuu.bilidownload.common.utils.bindOnEditorAction
import cc.kafuu.bilidownload.databinding.ActivitySearchBinding
import cc.kafuu.bilidownload.feature.viewbinding.view.fragment.SearchListFragment
import cc.kafuu.bilidownload.feature.viewbinding.viewmodel.activity.SearchViewModel

class SearchActivity : CoreActivity<ActivitySearchBinding, SearchViewModel>(
    SearchViewModel::class.java,
    R.layout.activity_search,
    BR.viewModel
), AdapterView.OnItemSelectedListener {
    companion object {
        private const val EXTRA_AUTO_SEARCH_CONTENT = "auto_search_content"

        fun buildIntent(searchContent: String) = Intent().apply {
            putExtra(EXTRA_AUTO_SEARCH_CONTENT, searchContent)
        }
    }

    private var mIsAutoSearch = false

    override fun initViews() {
        setImmersionStatusBar()
        mViewDataBinding.initSearchContent()
        initListener()
        handleAutoSearch()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || mIsAutoSearch) return
        mViewDataBinding.etSearchContent.apply {
            requestFocus()
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(
                this,
                InputMethodManager.SHOW_IMPLICIT
            )
        }
    }

    override fun onResume() {
        super.onResume()
        if (mIsAutoSearch) return
        // 检查搜索框内容是否为可跳转的地址，如果是则全选并展开键盘
        val searchText = mViewDataBinding.etSearchContent.text.toString()
        if (!TextUtils.isEmpty(searchText) && isJumpableAddress(searchText)) {
            mViewDataBinding.etSearchContent.apply {
                requestFocus()
                selectAll()
                post {
                    (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(
                        this,
                        InputMethodManager.SHOW_IMPLICIT
                    )
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        mIsAutoSearch = false
    }

    private fun ActivitySearchBinding.initSearchContent() {
        val adapter = ArrayAdapter<String>(requireContext(), R.layout.dropdown_item)
        etSearchContent.setAdapter(adapter)
        etSearchContent.threshold = 1
        mViewModel.searchRecordLiveData.observe(this@SearchActivity) { list ->
            adapter.clear()
            adapter.addAll(list.map { it.keyword })
            adapter.notifyDataSetChanged()
        }
        etSearchContent.setOnItemClickListener { parent, view, position, id ->
            val keyword = parent.getItemAtPosition(position) as String
            etSearchContent.setText(keyword)
            onStartSearch()
        }
        etSearchContent.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                mViewDataBinding.etSearchContent.showDropDown()
            }
        }
        etSearchContent.setOnClickListener {
            if (mViewDataBinding.etSearchContent.isPopupShowing) {
                mViewDataBinding.etSearchContent.showDropDown()
            } else {
                mViewDataBinding.etSearchContent.dismissDropDown()
            }
        }
    }

    private fun initListener() {
        mViewModel.searchRequestLiveData.observe(this) {
            if (TextUtils.isEmpty(it)) return@observe
            mViewDataBinding.fvFragment.getFragment<SearchListFragment>().doSearch(it)
            hideSoftInput()
        }
        mViewDataBinding.spSearchType.onItemSelectedListener = this
        mViewDataBinding.tvSearch.setOnClickListener {
            onStartSearch()
        }
        bindOnEditorAction(mViewDataBinding.etSearchContent) {
            onStartSearch()
        }
    }

    private fun onStartSearch(@SearchType searchType: Int = getSearchType()) {
        mViewModel.onSearch(mViewDataBinding.etSearchContent.text.toString(), searchType)
        mViewDataBinding.apply {
            etSearchContent.clearFocus()
            etSearchContent.post { etSearchContent.dismissDropDown() }
        }
    }

    private fun handleAutoSearch() {
        val searchContent = intent.getStringExtra(EXTRA_AUTO_SEARCH_CONTENT) ?: return
        mIsAutoSearch = true
        mViewDataBinding.etSearchContent.setText(searchContent)
        onStartSearch(SearchType.VIDEO)
    }

    private fun hideSoftInput() {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(
            mViewDataBinding.etSearchContent.windowToken, 0
        )
    }

    /**
     * 搜索类型被选择事件 */
    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        mViewDataBinding.fvFragment.getFragment<SearchListFragment>()
            .onSearchTypeChange(getSearchType())
    }

    override fun onNothingSelected(parent: AdapterView<*>?) = Unit

    private fun getSearchType() = when (mViewDataBinding.spSearchType.selectedItemPosition) {
        0 -> SearchType.VIDEO
        1 -> SearchType.MEDIA_BANGUMI
        2 -> SearchType.MEDIA_FT
        else -> throw IllegalArgumentException("Unknown search type: $mViewDataBinding")
    }

    /**
     * 检查搜索内容是否为可跳转到详情页的地址
     * 包括：包含URL或包含视频ID（BV、AV、EP、SS）
     */
    private fun isJumpableAddress(text: String): Boolean {
        return BiliAddressParser.containsSupportedAddress(text)
    }
}
