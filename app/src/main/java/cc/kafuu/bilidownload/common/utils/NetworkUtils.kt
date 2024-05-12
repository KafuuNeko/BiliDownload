package cc.kafuu.bilidownload.common.utils

object NetworkUtils {
    fun parseCookies(cookieHeader: String?): Map<String, String> {
        return cookieHeader?.split(";")
            ?.map { it.trim() }
            ?.mapNotNull {
                val (name, value) = it.split("=", limit = 2)
                if (name.isNotEmpty() && value.isNotEmpty()) name to value else null
            }?.toMap() ?: hashMapOf()
    }
}