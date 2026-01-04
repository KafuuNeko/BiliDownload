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
import java.util.zip.Inflater


object NetworkUtils {
    fun parseCookies(cookieHeader: String?): Map<String, String> {
        return cookieHeader?.split(";")
            ?.map { it.trim() }
            ?.mapNotNull {
                val (name, value) = it.split("=", limit = 2)
                if (name.isNotEmpty() && value.isNotEmpty()) name to value else null
            }?.toMap() ?: hashMapOf()
    }

    fun containsUrl(text: String): Boolean {
        val urlPattern = ("((http|https)://)?(www.)?" +
                "[a-zA-Z0-9@:%._\\+~#?&//=]" +
                "{2,256}\\.[a-z]" +
                "{2,6}\\b([-a-zA-Z0-9@:%" +
                "._\\+~#?&//=]*)")
        val regex = Regex(urlPattern)
        return regex.containsMatchIn(text)
    }

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

    fun decompressDeflate(body: ResponseBody): String {
        val compressed = body.bytes()

        val inflater = Inflater(/* nowrap = */ false) // false = zlib header 包含
        inflater.setInput(compressed)

        val output = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        while (!inflater.finished()) {
            val count = try {
                inflater.inflate(buffer)
            } catch (e: Exception) {
                // 如果第一次失败，尝试 nowrap = true
                val inf2 = Inflater(true)
                inf2.setInput(compressed)
                while (!inf2.finished()) {
                    output.write(buffer, 0, inf2.inflate(buffer))
                }
                return output.toString("UTF-8")
            }
            output.write(buffer, 0, count)
        }

        return output.toString("UTF-8")
    }

}