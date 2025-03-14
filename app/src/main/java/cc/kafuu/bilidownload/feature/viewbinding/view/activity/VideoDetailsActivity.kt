package cc.kafuu.bilidownload.feature.viewbinding.view.activity

import android.annotation.SuppressLint
import android.content.Intent
import android.view.KeyEvent
import androidx.recyclerview.widget.LinearLayoutManager
import cc.kafuu.bilidownload.BR
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.adapter.VideoPartRVAdapter
import cc.kafuu.bilidownload.common.core.viewbinding.CoreActivity
import cc.kafuu.bilidownload.common.ext.getSerializableByClass
import cc.kafuu.bilidownload.common.model.bili.BiliMediaModel
import cc.kafuu.bilidownload.common.model.bili.BiliVideoModel
import cc.kafuu.bilidownload.common.model.bili.BiliVideoPartModel
import cc.kafuu.bilidownload.databinding.ActivityVideoDetailsBinding
import cc.kafuu.bilidownload.feature.viewbinding.viewmodel.activity.VideoDetailsViewModel

class VideoDetailsActivity : CoreActivity<ActivityVideoDetailsBinding, VideoDetailsViewModel>(
    VideoDetailsViewModel::class.java,
    R.layout.activity_video_details,
    BR.viewModel
) {
    companion object {
        private const val TAG = "VideoDetailsActivity"

        private const val KEY_OBJECT_TYPE = "object_type"
        private const val KEY_OBJECT_INSTANCE = "object_instance"

        fun buildIntent(video: BiliVideoModel) = Intent().apply {
            putExtra(KEY_OBJECT_TYPE, BiliVideoModel::class.simpleName)
            putExtra(KEY_OBJECT_INSTANCE, video)
        }

        fun buildIntent(media: BiliMediaModel) = Intent().apply {
            putExtra(KEY_OBJECT_TYPE, BiliMediaModel::class.simpleName)
            putExtra(KEY_OBJECT_INSTANCE, media)
        }
    }

    override fun initViews() {
        setImmersionStatusBar()
        if (!doInitData()) {
            mViewModel.finishActivity()
            return
        }
        initList()
        mViewModel.loadingVideoPartLiveData.observe(this) { part ->
            onItemLoadingStatusChanged(
                part ?: mViewModel.selectedVideoPartLiveData.value ?: return@observe
            )
            mViewModel.multipleSelectItemsLiveData.value?.forEach {
                onItemLoadingStatusChanged(it)
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (!mViewModel.onBack()) finish()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun doInitData() = when (intent.getStringExtra(KEY_OBJECT_TYPE)) {
        BiliVideoModel::class.simpleName -> {
            mViewModel.initData(
                intent.getSerializableByClass<BiliVideoModel>(KEY_OBJECT_INSTANCE)!!
            )
            true
        }

        BiliMediaModel::class.simpleName -> {
            mViewModel.initData(
                intent.getSerializableByClass<BiliMediaModel>(KEY_OBJECT_INSTANCE)!!
            )
            true
        }

        else -> false
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun initList() {
        mViewDataBinding.rvParts.apply {
            adapter = VideoPartRVAdapter(mViewModel, this@VideoDetailsActivity)
            layoutManager = LinearLayoutManager(this@VideoDetailsActivity)
        }
        mViewModel.latestChangeIndexLiveData.observe(this) {
            if (it < 0) {
                mViewDataBinding.rvParts.adapter?.notifyDataSetChanged()
            } else {
                mViewDataBinding.rvParts.adapter?.notifyItemChanged(it)
            }
        }
    }

    private fun onItemLoadingStatusChanged(part: BiliVideoPartModel) {
        mViewDataBinding.rvParts.adapter?.notifyItemChanged(
            mViewModel.biliVideoPageListLiveData.value?.indexOf(part) ?: return
        )
    }
}