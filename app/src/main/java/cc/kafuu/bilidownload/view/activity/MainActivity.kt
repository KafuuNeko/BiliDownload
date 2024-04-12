package cc.kafuu.bilidownload.view.activity

import android.util.Log
import cc.kafuu.bilidownload.BR
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.adapter.FragmentAdapter
import cc.kafuu.bilidownload.common.core.CoreActivity
import cc.kafuu.bilidownload.common.network.IServerCallback
import cc.kafuu.bilidownload.common.room.entity.DownloadTaskEntity
import cc.kafuu.bilidownload.common.network.manager.NetworkManager
import cc.kafuu.bilidownload.common.network.model.BiliPlayStreamData
import cc.kafuu.bilidownload.common.utils.CommonLibs
import cc.kafuu.bilidownload.common.utils.PermissionUtils
import cc.kafuu.bilidownload.databinding.ActivityMainBinding
import cc.kafuu.bilidownload.model.MainTabType
import cc.kafuu.bilidownload.service.DownloadService
import cc.kafuu.bilidownload.view.fragment.HomeFragment
import cc.kafuu.bilidownload.view.fragment.MeFragment
import cc.kafuu.bilidownload.viewmodel.activity.MainViewModel
import kotlinx.coroutines.runBlocking

@Suppress("DEPRECATED_IDENTITY_EQUALS")
class MainActivity : CoreActivity<ActivityMainBinding, MainViewModel>(
    MainViewModel::class.java,
    R.layout.activity_main,
    BR.viewModel
) {

    companion object {
        private const val TAG = "MainActivity"
    }

    var first: Boolean = true

    fun downloadTest() {
        NetworkManager.biliVideoRepository.getPlayStreamData(
            "BV1Ai4y197Wt",
            481763422,
            object :
                IServerCallback<BiliPlayStreamData> {
                override fun onSuccess(
                    httpCode: Int,
                    code: Int,
                    message: String,
                    data: BiliPlayStreamData
                ) {
                    Log.d(
                        TAG,
                        "onSuccess: httpCode: $httpCode, code: $code, message: $message, data: $data"
                    )

                    Log.d(
                        TAG,
                        "onSuccess: ${data.dash.video.map { "${it.id}, ${it.codecs}, ${it.mimeType}" }}}"
                    )
                    runBlocking {
                        val taskId = CommonLibs.requireAppDatabase().downloadTaskDao().insert(
                            DownloadTaskEntity.createEntity(
                                "BV1Ai4y197Wt",
                                481763422,
                                data.dash.video[2],
                                data.dash.audio[2]
                            )
                        )
                        DownloadService.startDownload(this@MainActivity, taskId)
                        Log.d(TAG, "onSuccess: $taskId")
                    }

                }

                override fun onFailure(httpCode: Int, code: Int, message: String) {
                    Log.e(TAG, "onFailure: httpCode: $httpCode, code: $code, message: $message")
                }

            })
//        FFMpegJNI.mergeMedia(
//            "/storage/emulated/0/Android/data/cc.kafuu.bilidownload/files/cache/download/task-e10/merge2.mp4",
//            arrayOf(
//                "/storage/emulated/0/Android/data/cc.kafuu.bilidownload/files/cache/download/task-e10/1169319048-1-30216.mp4",
//                "/storage/emulated/0/Android/data/cc.kafuu.bilidownload/files/cache/download/task-e10/1169319048-1-30032.mp4"
//            ),
//            arrayOf()
//        )
    }

    override fun initViews() {
        setImmersionStatusBar()
        //downloadTest()
        mViewDataBinding.vp2Content.apply {
            adapter = FragmentAdapter(supportFragmentManager, lifecycle).apply {
                addFragmentView(getFragments())
            }
            isUserInputEnabled = false
            currentItem = MainTabType.TAB_HOME
        }
        mViewDataBinding.rbHome.apply {
            isChecked = true
        }
        mViewModel.tabPositionLiveData.observe(this) { position ->
            if (mViewDataBinding.vp2Content.currentItem !== position) {
                mViewDataBinding.vp2Content.setCurrentItem(position, false)
            } else {
                if (first) {
                    first = false
                } else {
                    if (!PermissionUtils.hasStoragePermissions(this)) {
                        PermissionUtils.requestStoragePermissions(this)
                    } else {
                        downloadTest()
                    }
                }
            }
        }
    }

    private fun getFragments() = listOf(
        HomeFragment.newInstance(),
        MeFragment.newInstance()
    )
}