package cc.kafuu.bilidownload.view.activity

import cc.kafuu.bilidownload.BR
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.core.CoreActivity
import cc.kafuu.bilidownload.databinding.ActivitySearchBinding
import cc.kafuu.bilidownload.viewmodel.activity.SearchViewModel

class SearchActivity : CoreActivity<ActivitySearchBinding, SearchViewModel>(
    SearchViewModel::class.java,
    R.layout.activity_search,
    BR.viewModel
) {
    override fun initViews() {
        setImmersionStatusBar()

    }
}