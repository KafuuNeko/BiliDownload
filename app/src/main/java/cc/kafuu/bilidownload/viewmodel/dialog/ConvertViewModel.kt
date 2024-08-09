package cc.kafuu.bilidownload.viewmodel.dialog

import androidx.lifecycle.MutableLiveData
import cc.kafuu.bilidownload.common.constant.MediaSteamType
import cc.kafuu.bilidownload.common.core.CoreViewModel
import cc.kafuu.bilidownload.common.ext.liveData
import cc.kafuu.bilidownload.common.model.action.ViewAction
import cc.kafuu.bilidownload.common.model.av.AVCodec
import cc.kafuu.bilidownload.common.model.av.AVFormat

class ConvertViewModel : CoreViewModel() {
    companion object {
        class ConfirmAction : ViewAction()
        class CancelAction : ViewAction()
    }

    // 对话框标题
    private val mTitleLiveData = MutableLiveData<String>()
    val titleLiveData = mTitleLiveData.liveData()

    // 所有支持的音视频封装格式
    private val mAVFormatListLiveData = MutableLiveData<List<AVFormat>>(listOf())
    val avFormatListLiveData = mAVFormatListLiveData.liveData()

    // 初始化封装格式
    private val mInitialFormatLiveData = MutableLiveData<AVFormat>()
    val initialFormatLiveData = mInitialFormatLiveData.liveData()

    // 当前选择的封装格式
    private val mFormatLiveData = MutableLiveData<AVFormat>()
    val formatLiveData = mFormatLiveData.liveData()

    // 初始化音频编码
    private val mInitialAudioCodecLiveData = MutableLiveData<AVCodec?>()
    val initialAudioCodecLiveData = mInitialAudioCodecLiveData.liveData()

    // 当前选择的音频编码
    private val mAudioCodecLiveData = MutableLiveData<AVCodec?>()
    val audioCodecLiveData = mAudioCodecLiveData.liveData()

    // 初始化视频编码
    private val mInitialVideoCodecLiveData = MutableLiveData<AVCodec?>()
    val initialVideoCodecLiveData = mInitialVideoCodecLiveData.liveData()

    // 当前选择的视频编码
    private val mVideoCodecLiveData = MutableLiveData<AVCodec?>()
    val videoCodecLiveData = mVideoCodecLiveData.liveData()


    /**
     * @brief 初始化所有支持的封装格式
     */
    fun init(
        title: String,
        initialFormat: AVFormat,
        initialVideoCodec: AVCodec?,
        initialAudioCodec: AVCodec?
    ) {
        mTitleLiveData.value = title

        mInitialFormatLiveData.value = initialFormat
        mInitialVideoCodecLiveData.value = initialVideoCodec
        mInitialAudioCodecLiveData.value = initialAudioCodec

        mFormatLiveData.value = initialFormat
        mVideoCodecLiveData.value = initialVideoCodec
        mAudioCodecLiveData.value = initialAudioCodec

        AVFormat.entries.filter {
            val videoCondition = it.videoSupportCodecs != null || initialVideoCodec == null
            val audioCondition = it.audioSupportCodecs != null || initialAudioCodec == null
            videoCondition && audioCondition
        }.also {
            mAVFormatListLiveData.value = it
        }
    }

    /**
     * @brief 切换当前的音视频封装格式
     */
    fun updateFormat(currentFormat: AVFormat) {
        if (mFormatLiveData.value == currentFormat) return

        mFormatLiveData.value = currentFormat

        mAudioCodecLiveData.value = currentFormat.audioSupportCodecs?.find {
            it == mAudioCodecLiveData.value
        } ?: currentFormat.audioSupportCodecs?.firstOrNull()

        mVideoCodecLiveData.value = currentFormat.videoSupportCodecs?.find {
            it == mVideoCodecLiveData.value
        } ?: currentFormat.videoSupportCodecs?.firstOrNull()
    }

    /**
     * @brief 根据编码类型自动更新当前已选中的编码
     */
    fun updateCurrentCodec(currentCodec: AVCodec) {
        when (currentCodec.codecType) {
            MediaSteamType.AUDIO -> mAudioCodecLiveData
            MediaSteamType.VIDEO -> mVideoCodecLiveData
            else -> return
        }.also {
            if (it.value == currentCodec) {
                return
            }
            it.value = currentCodec
        }
    }

    /**
     * @brief 某个编码是否被选中
     */
    fun isSelected(codec: AVCodec): Boolean {
        return if (codec.codecType == MediaSteamType.AUDIO) {
            mAudioCodecLiveData.value == codec
        } else {
            mVideoCodecLiveData.value == codec
        }
    }

    /**
     * @brief 某个封装格式是否被选中
     */
    fun isSelected(format: AVFormat): Boolean {
        return mFormatLiveData.value == format
    }

    /**
     * @brief 点击确认按钮
     */
    fun onConfirm() {
        sendViewAction(ConfirmAction())
    }

    /**
     * @brief 点击取消按钮
     */
    fun onCancel() {
        sendViewAction(CancelAction())
    }
}