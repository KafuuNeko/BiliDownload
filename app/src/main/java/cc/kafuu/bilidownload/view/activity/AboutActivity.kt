package cc.kafuu.bilidownload.view.activity

import cc.kafuu.bilidownload.BR
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.core.CoreActivity
import cc.kafuu.bilidownload.databinding.ActivityAboutBinding
import cc.kafuu.bilidownload.viewmodel.activity.AboutViewModel

class AboutActivity : CoreActivity<ActivityAboutBinding, AboutViewModel>(
    AboutViewModel::class.java,
    R.layout.activity_about,
    BR.viewModel
) {
    override fun initViews() {
        // 初始化视图
    }
}