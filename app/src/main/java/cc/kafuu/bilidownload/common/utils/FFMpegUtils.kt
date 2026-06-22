package cc.kafuu.bilidownload.common.utils

import android.util.Log
import cc.kafuu.bilidownload.common.model.IAsyncCallback
import cc.kafuu.bilidownload.common.model.LocalMediaDetail
import cc.kafuu.bilidownload.common.model.av.AVCodec
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import com.google.gson.JsonParser
import java.io.File

object FFMpegUtils {
    private const val TAG = "FFMpegUtils"

    data class AudioRemuxFormat(
        val suffix: String,
        val mimeType: String,
        val muxer: String
    )

    fun detectAudioRemuxFormat(sourceFile: String): AudioRemuxFormat? {
        val mediaDetail = getMediaInfo(sourceFile) ?: return null
        return when (mediaDetail.audioCodec?.lowercase()) {
            "aac" -> AudioRemuxFormat("aac", "audio/aac", "adts")
            "flac" -> AudioRemuxFormat("flac", "audio/flac", "flac")
            "mp3" -> AudioRemuxFormat("mp3", "audio/mpeg", "mp3")
            "opus" -> AudioRemuxFormat("opus", "audio/opus", "opus")
            "vorbis" -> AudioRemuxFormat("ogg", "audio/ogg", "ogg")
            "ac3" -> AudioRemuxFormat("ac3", "audio/ac3", "ac3")
            "eac3" -> AudioRemuxFormat("eac3", "audio/eac3", "eac3")
            "alac" -> AudioRemuxFormat("m4a", "audio/mp4", "mp4")
            else -> null
        }
    }

    fun remuxAudio(
        sourceFile: String,
        targetFile: String,
        format: AudioRemuxFormat
    ): Boolean {
        val command = "-y -i ${quotePath(sourceFile)} -map 0:a:0 -vn " +
            "-c:a copy -f ${format.muxer} ${quotePath(targetFile)}"
        Log.d(TAG, "remuxAudio: $command")

        val session = FFmpegKit.execute(command)
        val outputFile = File(targetFile)
        val isSuccess = ReturnCode.isSuccess(session.returnCode) &&
            outputFile.exists() &&
            outputFile.length() > 0L
        if (!isSuccess && outputFile.exists()) {
            outputFile.delete()
        }
        return isSuccess
    }

    /**
     * @brief 以异步的形式转换视频封装格式、编码
     */
    fun convertMediaAsync(
        sourceFile: String, targetFile: String,
        videoCodec: Pair<AVCodec, AVCodec>?, audioCodec: Pair<AVCodec, AVCodec>?,
        onProgress: (time: Double) -> Unit, onComplete: (success: Boolean) -> Unit
    ): FFmpegSession {
        val videoCodecParam = videoCodec?.let {
            if (it.first != it.second) "-c:v ${it.second.fullName}" else "-c:v copy"
        } ?: ""
        val audioCodecParam = audioCodec?.let {
            if (it.first != it.second) "-c:a ${it.second.fullName}" else "-c:a copy"
        } ?: ""

        val command = "-i \"$sourceFile\" $videoCodecParam $audioCodecParam \"$targetFile\""
        Log.d(TAG, "convertMediaWithStatistics: $command")

        return FFmpegKit.executeAsync(command,
            { onComplete(ReturnCode.isSuccess(it.returnCode)) },
            { Log.d(TAG, it.message) },
            { onProgress(it.time) }
        )
    }

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
        val command = buildMediaInfoCommand(filePath)
        FFprobeKit.executeAsync(command) { session ->
            val output = session.output
            Log.d(TAG, "getMediaInfo: $output")
            if (ReturnCode.isSuccess(session.returnCode)) {
                callback.onSuccess(parseMediaInfo(filePath, output))
            } else {
                callback.onFailure(Exception("Failed to get media info, return code: ${session.returnCode}"))
            }
        }
    }

    fun getMediaInfo(filePath: String): LocalMediaDetail? {
        val session = FFprobeKit.execute(buildMediaInfoCommand(filePath))
        if (!ReturnCode.isSuccess(session.returnCode)) {
            Log.d(TAG, "getMediaInfo failed, return code: ${session.returnCode}")
            return null
        }

        return runCatching {
            parseMediaInfo(filePath, session.output)
        }.onFailure {
            Log.e(TAG, "parse media info failed", it)
        }.getOrNull()
    }

    private fun buildMediaInfoCommand(filePath: String): String {
        return "-v quiet -print_format json -show_format -show_streams ${quotePath(filePath)}"
    }

    private fun quotePath(path: String): String {
        return "\"${path.replace("\"", "\\\"")}\""
    }

    private fun parseMediaInfo(filePath: String, output: String): LocalMediaDetail {
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
            path = filePath,
            raw = output,
            format = format.get("format_name")?.asString,
            duration = durationInSeconds,
            formatDuration = formattedDuration,
            videoCodec = videoCodec,
            audioCodec = audioCodec,
            resolution = resolution,
            frameRate = frameRate,
            audioSampleRate = audioSampleRate
        )
    }
}
