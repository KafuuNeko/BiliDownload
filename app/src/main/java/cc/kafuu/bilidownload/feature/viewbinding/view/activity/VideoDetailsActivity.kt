package cc.kafuu.bilidownload.feature.viewbinding.view.activity

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import cc.kafuu.bilidownload.BR
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.CommonLibs
import cc.kafuu.bilidownload.common.adapter.VideoPartRVAdapter
import cc.kafuu.bilidownload.common.core.viewbinding.CoreActivity
import cc.kafuu.bilidownload.common.ext.getSerializableByClass
import cc.kafuu.bilidownload.common.model.ResultWrapper
import cc.kafuu.bilidownload.common.model.action.ViewAction
import cc.kafuu.bilidownload.common.model.bili.BiliMediaModel
import cc.kafuu.bilidownload.common.model.bili.BiliVideoModel
import cc.kafuu.bilidownload.common.model.bili.BiliVideoPartModel
import cc.kafuu.bilidownload.common.utils.FileUtils
import cc.kafuu.bilidownload.databinding.ActivityVideoDetailsBinding
import cc.kafuu.bilidownload.feature.viewbinding.view.dialog.ConfirmDialog
import cc.kafuu.bilidownload.feature.viewbinding.viewmodel.activity.VideoDetailsViewModel
import kotlinx.coroutines.launch
import java.io.File

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

    private lateinit var mCreateDocumentLauncher: ActivityResultLauncher<Intent>
    private var mPendingCoverUrl: String? = null
    private var mPendingFileName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val contracts = ActivityResultContracts.StartActivityForResult()
        mCreateDocumentLauncher = registerForActivityResult(contracts) {
            if (it.resultCode == RESULT_OK && it.data != null) {
                val uri = it.data?.data ?: return@registerForActivityResult
                lifecycleScope.launch {
                    saveCoverToUri(uri)
                }
            }
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

    override fun onViewAction(action: ViewAction) {
        when (action) {
            is VideoDetailsViewModel.Companion.SaveCoverAction -> onSaveCover(action)
            is VideoDetailsViewModel.Companion.ShowSaveCoverConfirmAction -> onShowSaveCoverConfirm(action)
            else -> super.onViewAction(action)
        }
    }
    
    private fun onShowSaveCoverConfirm(action: VideoDetailsViewModel.Companion.ShowSaveCoverConfirmAction) {
        lifecycleScope.launch {
            val result = ConfirmDialog.buildDialog(
                CommonLibs.getString(R.string.text_save_cover),
                CommonLibs.getString(R.string.save_cover_confirm_message),
                CommonLibs.getString(R.string.text_cancel),
                CommonLibs.getString(R.string.text_confirm)
            ).showAndWaitResult(this@VideoDetailsActivity)
            if (result is ResultWrapper.Success && result.value) {
                mViewModel.confirmSaveCover(action.coverUrl, action.bvid)
            }
        }
    }

    private fun onSaveCover(action: VideoDetailsViewModel.Companion.SaveCoverAction) {
        mPendingCoverUrl = action.coverUrl
        mPendingFileName = action.fileName
        
        // 从文件名中提取扩展名，确定MIME类型
        val extension = action.fileName.substringAfterLast('.', "jpg")
        val mimeType = when (extension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            else -> "image/jpeg"
        }
        
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mimeType
            putExtra(Intent.EXTRA_TITLE, action.fileName)
        }
        mCreateDocumentLauncher.launch(intent)
    }

    private suspend fun saveCoverToUri(uri: Uri) {
        val coverUrl = mPendingCoverUrl ?: return
        val fileName = mPendingFileName ?: return
        
        // 创建临时文件
        val tempFile = File(CommonLibs.requireContext().cacheDir, "cover_${System.currentTimeMillis()}.tmp")
        
        try {
            // 下载图片到临时文件
            val success = FileUtils.downloadImageToFile(coverUrl, tempFile)
            if (!success) {
                mViewModel.popMessage(
                    cc.kafuu.bilidownload.common.model.action.popmessage.ToastMessageAction(
                        CommonLibs.getString(R.string.save_cover_failed_message)
                    )
                )
                return
            }
            
            // 将临时文件写入到用户选择的URI
            val writeSuccess = FileUtils.writeFileToUri(CommonLibs.requireContext(), uri, tempFile)
            if (writeSuccess) {
                mViewModel.popMessage(
                    cc.kafuu.bilidownload.common.model.action.popmessage.ToastMessageAction(
                        CommonLibs.getString(R.string.save_cover_success_message)
                    )
                )
            } else {
                mViewModel.popMessage(
                    cc.kafuu.bilidownload.common.model.action.popmessage.ToastMessageAction(
                        CommonLibs.getString(R.string.save_cover_failed_message)
                    )
                )
            }
        } finally {
            // 清理临时文件
            tempFile.delete()
            mPendingCoverUrl = null
            mPendingFileName = null
        }
    }
}