package cc.kafuu.bilidownload.common.utils

import android.util.Log
import cc.kafuu.bilidownload.common.CommonLibs
import cc.kafuu.bilidownload.common.model.IAsyncCallback
import cc.kafuu.bilidownload.common.model.LocalMediaDetail
import cc.kafuu.bilidownload.common.model.av.AVCodec
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import com.google.gson.JsonParser
import java.io.File

/**
 * 统一 FFmpegKit/FFprobeKit 的参数构造、媒体探测与结果判断。
 *
 * - 路径和 SAF 协议地址均作为独立参数传入，调用方不要自行拼接命令引号。
 * - 同步方法占用调用线程，页面应切换到 IO 线程；异步会话的回调由 FFmpegKit 分发。
 * - 本工具不登记或删除输入资源，输出的命名、持久化、发布和失败补偿由调用方负责。
 */
object FFMpegUtils {
    private const val TAG = "FFMpegUtils"

    /** 音频无损转封装所需的扩展名、MIME 类型和 FFmpeg muxer，三者须配套使用。 */
    data class AudioRemuxFormat(
        val suffix: String,
        val mimeType: String,
        val muxer: String
    )

    /** 同步探测首个音频流的编码，返回匹配的无损封装；探测失败或编码不支持时返回 null。 */
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

    /**
     * 同步复制首个音频流到指定封装，去掉视频流但不重新编码音频。
     *
     * 目标会被覆盖，必须使用区别于源文件的输出路径；失败时尝试删除本次输出，不清理源文件。
     * 返回 true 仅代表原生执行成功且输出非空，资源记录和后续发布仍由调用方提交。
     */
    fun remuxAudio(
        sourceFile: String,
        targetFile: String,
        format: AudioRemuxFormat
    ): Boolean {
        // 显式选择首个音频流和 muxer，避免目标后缀或文件名中的引号改变处理方式。
        val session = FFmpegKit.executeWithArguments(arrayOf(
            "-y", "-i", sourceFile, "-map", "0:a:0", "-vn",
            "-c:a", "copy", "-f", format.muxer, targetFile,
        ))
        val outputFile = File(targetFile)
        val isSuccess = ReturnCode.isSuccess(session.returnCode) &&
            outputFile.exists() &&
            outputFile.length() > 0L
        if (!isSuccess && outputFile.exists()) {
            // 不把残留半成品交给登记/发布流程；删除失败仍按本次转封装失败返回。
            outputFile.delete()
        }
        return isSuccess
    }

    /**
     * 异步转换封装格式和编码，优先读取有效的公共 URI；参数数组保留路径中的引号。
     *
     * 输入解析在调用线程执行，原生转换及回调由 FFmpegKit 分发；回调更新 UI 时须自行切换线程。
     * 只有原生执行成功且输出非空时才通知成功，失败或取消的临时输出由调用方清理。
     *
     * @param targetFile 独立的目标路径，调用方负责唯一命名，不能覆盖输入资源。
     * @param videoCodec 源编码与目标编码；相同时使用 copy，为 null 时不显式设置视频编码器。
     * @param audioCodec 源编码与目标编码；相同时使用 copy，为 null 时不显式设置音频编码器。
     * @param onProgress FFmpegKit 报告的处理进度，时间单位为毫秒。
     * @param onComplete 原生结束后的结果回调，不代表资源已登记或发布。
     * @param contentUri 首选内容 URI，不可用时按来源解析规则回退到文件路径。
     * @return FFmpeg 会话，由调用方保存以便查看状态或请求取消。
     */
    fun convertMediaAsync(
        sourceFile: String, targetFile: String,
        videoCodec: Pair<AVCodec, AVCodec>?, audioCodec: Pair<AVCodec, AVCodec>?,
        onProgress: (time: Double) -> Unit, onComplete: (success: Boolean) -> Unit,
        contentUri: String? = null,
    ): FFmpegSession {
        // SAF 输入在这里解析；编码相同只复制码流，避免不必要的有损重编码。
        val arguments = mutableListOf("-i", resolveMediaInput(sourceFile, contentUri))
        videoCodec?.let {
            arguments += listOf("-c:v", if (it.first != it.second) it.second.fullName else "copy")
        }
        audioCodec?.let {
            arguments += listOf("-c:a", if (it.first != it.second) it.second.fullName else "copy")
        }
        arguments += targetFile
        // 退出码与有效输出共同决定成功，不能把空文件当作可登记资源。
        return FFmpegKit.executeWithArgumentsAsync(arguments.toTypedArray(),
            { onComplete(ReturnCode.isSuccess(it.returnCode) && File(targetFile).length() > 0L) },
            { Log.d(TAG, it.message) },
            { onProgress(it.time) }
        )
    }

    /**
     * 合并音视频并检查输出；各路径作为独立参数传递，避免引号改变命令边界。
     *
     * 同步执行且不修改输入，调用方需提供独立的新输出路径，并负责登记成品或清理失败输出。
     * 返回成功前核对退出码和非空输出；此处不提交下载状态，也不决定何时删除音视频源文件。
     */
    fun mergeMedia(input1: String, input2: String, output: String): Boolean {
        val session = FFmpegKit.executeWithArguments(arrayOf(
            "-i", input1, "-i", input2, "-c:v", "copy", "-c:a", "copy",
            "-strict", "experimental", output,
        ))
        return ReturnCode.isSuccess(session.returnCode) && File(output).length() > 0L
    }

    /**
     * 异步探测本地路径，在 FFprobeKit 回调线程返回解析结果。
     *
     * 原生执行失败或 JSON 解析异常均交给失败回调，避免调用方一直停留在加载状态。
     * 需要读取公共 URI 的页面应在 IO 线程使用带 contentUri 的同步重载。
     *
     * @param filePath 用于探测并写入详情模型的本地路径。
     * @param callback 不保证运行在主线程，界面状态分发由调用方负责。
     */
    fun getMediaInfo(filePath: String, callback: IAsyncCallback<LocalMediaDetail, Exception>) {
        // 原生命令成功仍可能返回不完整的结构，解析失败须走同一失败出口。
        FFprobeKit.executeWithArgumentsAsync(buildMediaInfoArguments(filePath)) { session ->
            val output = session.output
            Log.d(TAG, "getMediaInfo: $output")
            if (ReturnCode.isSuccess(session.returnCode)) {
                val detail = try {
                    parseMediaInfo(filePath, output)
                } catch (error: Exception) {
                    callback.onFailure(error)
                    return@executeWithArgumentsAsync
                }
                callback.onSuccess(detail)
            } else {
                callback.onFailure(Exception("Failed to get media info, return code: ${session.returnCode}"))
            }
        }
    }

    /**
     * 在调用线程通过路径或有效 URI 探测媒体，页面调用时须切换到 IO 线程。
     *
     * 探测、权限或解析异常均返回 null；详情中的 path 保留传入路径，不能据此推断旧路径仍可读取。
     * 本方法只读取输入，不登记资源，也不延长 URI 授权的有效期。
     */
    fun getMediaInfo(filePath: String, contentUri: String? = null): LocalMediaDetail? = runCatching {
        val session = FFprobeKit.executeWithArguments(
            buildMediaInfoArguments(resolveMediaInput(filePath, contentUri)),
        )
        if (!ReturnCode.isSuccess(session.returnCode)) {
            Log.d(TAG, "getMediaInfo failed, return code: ${session.returnCode}")
            return@runCatching null
        }
        parseMediaInfo(filePath, session.output)
    }.onFailure { Log.e(TAG, "Unable to read media info", it) }.getOrNull()

    /**
     * 通过公共解析规则优先选择可读 URI，再转换为 FFmpegKit 的 SAF 只读输入地址。
     *
     * URI 不可用时尝试路径回退；content URI 不能直接当作原生文件系统路径使用。
     * 当前读取权限仍需覆盖会话执行期间，地址解析成功不保证后续不会撤权或删除资源。
     */
    private fun resolveMediaInput(filePath: String, contentUri: String?): String {
        if (contentUri.isNullOrBlank()) return filePath
        val context = CommonLibs.requireContext()
        val uri = FileUtils.resolveReadUri(context, File(filePath), contentUri) ?: return filePath
        return FFmpegKitConfig.getSafParameterForRead(context, uri)
    }

    /** 只获取格式与流的 JSON 信息；input 已是单个完整参数，不额外套命令引号。 */
    private fun buildMediaInfoArguments(input: String) = arrayOf(
        "-v", "quiet", "-print_format", "json", "-show_format", "-show_streams", input,
    )

    /** 读取 FFprobe JSON 的首个音频/视频流；结构异常由同步或异步入口转成各自的失败结果。 */
    private fun parseMediaInfo(filePath: String, output: String): LocalMediaDetail {
        // 时长以秒保存，格式化文本只用于展示，转换进度计算仍使用原始数值。
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

        // 与页面单轨展示模型对应，只记录首个同类流，不把后续轨道覆盖到当前显示项。
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
