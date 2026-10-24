package cc.kafuu.bilidownload.common.utils

import cc.kafuu.bilidownload.common.network.IServerCallback
import cc.kafuu.bilidownload.common.network.manager.NetworkManager
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.DataFormatException
import java.util.zip.Inflater


/**
 * 提供 Cookie 解析、地址识别、跳转解析及弹幕响应解压等网络边界工具。
 *
 * Cookie 解析不改变账号状态；解压方法负责消费并关闭传入响应体，调用方负责处理失败提示。
 */
object NetworkUtils {
    /**
     * 从 Cookie 请求头提取有效名值对，供退出登录等流程读取必要字段。
     *
     * 只按首个等号分隔，保留值内部的等号；空段、无等号、空名称和空值均忽略。
     * 同名字段使用最后一个有效值，null 或没有有效字段时返回空映射。
     *
     * @param cookieHeader 待解析的请求头内容；只用于内存解析，不应记录到日志。
     */
    fun parseCookies(cookieHeader: String?): Map<String, String> {
        return cookieHeader?.split(";")
            ?.map { it.trim() }
            ?.mapNotNull {
                val separator = it.indexOf('=')
                if (separator <= 0) return@mapNotNull null
                val name = it.substring(0, separator).trim()
                val value = it.substring(separator + 1).trim()
                if (name.isNotEmpty() && value.isNotEmpty()) name to value else null
            }?.toMap() ?: hashMapOf()
    }

    /** 判断文本中是否包含符合当前正则规则的地址片段，不验证域名可达性或业务支持范围。 */
    fun containsUrl(text: String): Boolean {
        val urlPattern = ("((http|https)://)?(www.)?" +
                "[a-zA-Z0-9@:%._\\+~#?&//=]" +
                "{2,256}\\.[a-z]" +
                "{2,6}\\b([-a-zA-Z0-9@:%" +
                "._\\+~#?&//=]*)")
        val regex = Regex(urlPattern)
        return regex.containsMatchIn(text)
    }

    /** 使用共享客户端跟随跳转，在 OkHttp 回调线程返回最终地址；非 200 响应走失败回调。 */
    fun redirection(
        url: String,
        callback: IServerCallback<String>
    ) = NetworkManager.okHttpClient.newCall(
        Request.Builder().apply {
            url(url)
            get()
        }.build()
    ).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            callback.onFailure(0, 0, e.message ?: "Unknown error")
        }

        @Throws(IOException::class)
        override fun onResponse(call: Call, response: Response) {
            if (response.code() != 200) {
                callback.onFailure(response.code(), 0, "Response code: ${response.code()}")
                return
            }
            val location = response.request().url().toString()
            callback.onSuccess(response.code(), 0, "success", location)
        }
    })

    /**
     * 消费 deflate 响应体并返回 UTF-8 文本，兼容 zlib 包装与 raw 两种格式。
     *
     * 在调用线程完成读取和解压，并关闭 [body]。解码器无法继续产出字节时立即失败，
     * 避免在 Retrofit 弹幕回调中空转；格式不匹配时使用全新的 raw 解码器和输出缓冲重试。
     *
     * @throws IOException 响应读取失败、压缩数据为空、截断或无法完整解码时抛出。
     */
    @Throws(IOException::class)
    fun decompressDeflate(body: ResponseBody): String {
        val compressed = body.use { it.bytes() }
        return try {
            inflate(compressed, raw = false)
        } catch (_: DataFormatException) {
            // raw 格式必须从空缓冲区重试，不能拼接首次解码的部分结果。
            try {
                inflate(compressed, raw = true)
            } catch (error: DataFormatException) {
                throw IOException("Invalid deflate response", error)
            }
        }
    }

    /**
     * 执行一次独立解压；失败时丢弃本次输出，并在所有退出路径释放原生解码器。
     *
     * @param raw true 表示输入不含 zlib 包装头。
     * @throws DataFormatException 输入与指定格式不匹配，交由上层决定是否切换格式。
     * @throws IOException 输入不足、需要额外字典等原因导致解码停滞时抛出。
     */
    private fun inflate(compressed: ByteArray, raw: Boolean): String {
        val inflater = Inflater(raw)
        try {
            // 每种格式独占输入和输出状态，防止失败尝试的部分文本混入后续结果。
            inflater.setInput(compressed)
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count > 0) {
                    output.write(buffer, 0, count)
                } else if (!inflater.finished()) {
                    // inflate 返回零并不表示流已结束；继续使用同一输入只会重复空转。
                    throw IOException("Incomplete or unsupported deflate response")
                }
            }
            return output.toString("UTF-8")
        } finally {
            // Inflater 持有原生资源，异常退出也必须释放，不能依赖垃圾回收的时机。
            inflater.end()
        }
    }

}
