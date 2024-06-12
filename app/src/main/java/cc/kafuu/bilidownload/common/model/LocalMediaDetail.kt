package cc.kafuu.bilidownload.common.model

data class LocalMediaDetail(
    /**
     * 未解析的原始输出数据
     */
    val raw: String,
    /**
     * 媒体文件的容器格式
     */
    val format: String?,
    /**
     * 媒体文件的总播放时长(时:分:秒.毫秒)
     */
    val duration: String?,
    /**
     * 视频流使用的编解码器
     */
    val videoCodec: String?,
    /**
     * 音频流使用的编解码器
     */
    val audioCodec: String?,
    /**
     * 视频的分辨率(宽度x高度)
     */
    val resolution: String?,
    /*
    * 视频的帧率（帧/s）
    */
    val frameRate: String?,
    /**
     * 音频的采样率，以 Hz表示
     */
    val audioSampleRate: String?
)
