package cc.kafuu.bilidownload.feature.viewbinding.view.activity

import android.content.Intent
import cc.kafuu.bilidownload.BR
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.core.viewbinding.CoreActivity
import cc.kafuu.bilidownload.common.ext.getSerializableByClass
import cc.kafuu.bilidownload.common.model.bili.BiliFavoriteModel
import cc.kafuu.bilidownload.databinding.ActivityFavoriteDetailsBinding
import cc.kafuu.bilidownload.feature.viewbinding.view.fragment.FavoriteContentFragment
import cc.kafuu.bilidownload.feature.viewbinding.viewmodel.activity.FavoriteDetailsViewModel

class FavoriteDetailsActivity :
    CoreActivity<ActivityFavoriteDetailsBinding, FavoriteDetailsViewModel>(
        FavoriteDetailsViewModel::class.java,
        R.layout.activity_favorite_details,
        BR.viewModel
    ) {

    companion object {
        private const val KEY_OBJECT_FAVORITE = "object_favorite"

        fun buildIntent(favorite: BiliFavoriteModel) = Intent().apply {
            putExtra(KEY_OBJECT_FAVORITE, favorite)
        }
    }

    override fun initViews() {
        try {
            mViewModel.init()
            initFragment()
        } catch (e: Exception) {
            e.printStackTrace()
            mViewModel.finishActivity()
        }
    }

    private fun FavoriteDetailsViewModel.init() {
        val biliFavoriteModel = intent.getSerializableByClass<BiliFavoriteModel>(
            KEY_OBJECT_FAVORITE
        ) ?: throw IllegalArgumentException("Unknown object")
        initData(biliFavoriteModel)
    }


    private fun initFragment() {
        supportFragmentManager.beginTransaction().apply {
            replace(R.id.fl_content, FavoriteContentFragment.builder().build())
            commit()
        }
    }
}