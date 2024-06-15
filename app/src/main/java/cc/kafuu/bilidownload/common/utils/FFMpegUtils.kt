package cc.kafuu.bilidownload.common.utils

import android.util.Log
import cc.kafuu.bilidownload.common.model.IAsyncCallback
import cc.kafuu.bilidownload.common.model.LocalMediaDetail
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import com.google.gson.JsonParser

object FFMpegUtils {
    private const val TAG = "FFMpegUtils"

    /**
     * @brief 合并音频或视频
     */
    fun mergeMedia(input1: String, input2: String, output: String): Boolean {
        val command =
            "-i \"$input1\" -i \"$input2\" -c:v copy -c:a copy -strict experimental \"$output\""
        Log.d(TAG, "mergeMedia: $command")
        val session = FFmpegKit.execute(command)
        return ReturnCode.isSuccess(session.returnCode)
    }

    /**
     * @brief 取得媒体文件具体信息
     *
     * @param filePath 要取得的文件路径
     *
     * @param callback 回调
     */
    fun getMediaInfo(filePath: String, callback: IAsyncCallback<LocalMediaDetail, Exception>) {
        val command = "-v quiet -print_format json -show_format -show_streams $filePath"
        FFprobeKit.executeAsync(command) { session ->
            val output = session.output
            Log.d(TAG, "getMediaInfo: $output")
            if (ReturnCode.isSuccess(session.returnCode)) {
                callback.onSuccess(parseMediaInfo(output))
            } else {
                callback.onFailure(Exception("Failed to get media info, return code: ${session.returnCode}"))
            }
        }
    }

    private fun parseMediaInfo(output: String): LocalMediaDetail {
        val jsonObject = JsonParser.parseString(output).asJsonObject
        val format = jsonObject.getAsJsonObject("format")
        val streams = jsonObject.getAsJsonArray("streams")

        val durationInSeconds = format.get("duration")?.asDouble ?: 0.0
        val formattedDuration = TimeUtils.formatDuration(durationInSeconds)

        var videoCodec: String? = null
        var resolution: String? = null
        var frameRate: String? = null
        var audioCodec: String? = null
        var audioSampleRate: String? = null

        streams.forEach { streamElement ->
            val stream = streamElement.asJsonObject
            val codecType = stream.get("codec_type").asString
            if (codecType == "video" && videoCodec == null) {
                videoCodec = stream.get("codec_name")?.asString
                resolution = "${stream.get("width")?.asInt}x${stream.get("height")?.asInt}"
                frameRate = stream.get("r_frame_rate")?.asString?.let {
                    val fps = TimeUtils.evalFrameRate(it)
                    "${String.format("%.4f", fps)} fps"
                }
            } else if (codecType == "audio" && audioCodec == null) {
                audioCodec = stream.get("codec_name")?.asString
                audioSampleRate = stream.get("sample_rate")?.asString?.let {
                    "$it Hz"
                }
            }
        }

        return LocalMediaDetail(
            raw = output,
            format = format.get("format_name")?.asString,
            duration = formattedDuration,
            videoCodec = videoCodec,
            audioCodec = audioCodec,
            resolution = resolution,
            frameRate = frameRate,
            audioSampleRate = audioSampleRate
        )
    }
}