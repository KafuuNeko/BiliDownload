package cc.kafuu.bilidownload.common.utils

import android.util.Log
import cc.kafuu.bilidownload.common.model.IAsyncCallback
import cc.kafuu.bilidownload.common.model.LocalMediaDetail
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode

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
        val command = "-i $filePath -hide_banner"
        FFmpegKit.executeAsync(command) { session ->
            val output = session.output
            Log.d(TAG, "getMediaInfo: $output")
            if (ReturnCode.isSuccess(session.returnCode)) {
                callback.onSuccess(parseMediaInfo(output))
            } else {
                callback.onFailure(Exception("Failed to get media info, return code: ${session.returnCode}"))
            }
        }
    }

    private fun parseMediaInfo(output: String) = LocalMediaDetail(
        raw = output,
        format = findMatch("Input #0, (\\w+),", output),
        duration = findMatch("Duration: (\\d{2}:\\d{2}:\\d{2}\\.\\d{2}),", output),
        videoCodec = findMatch("Video: (\\w+)", output),
        audioCodec = findMatch("Audio: (\\w+)", output),
        resolution = findMatch(", (\\d{3,}x\\d{3,})", output),
        frameRate = findMatch(", (\\d{1,3} fps)", output),
        audioSampleRate = findMatch("Audio: .+, (\\d{4,} Hz)", output)
    )


    private fun findMatch(regex: String, input: String): String? {
        val pattern = Regex(regex)
        return pattern.find(input)?.groupValues?.get(1) // 返回第一个匹配组的值
    }
}