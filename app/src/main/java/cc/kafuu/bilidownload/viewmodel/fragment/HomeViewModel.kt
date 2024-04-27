package cc.kafuu.bilidownload.viewmodel.fragment

import cc.kafuu.bilidownload.common.core.CoreViewModel
import cc.kafuu.bilidownload.view.activity.SearchActivity
import com.bumptech.glide.load.resource.bitmap.CircleCrop

class HomeViewModel: CoreViewModel() {
    fun jumpSearchActivity() {
        startActivity(SearchActivity::class.java)
    }
}