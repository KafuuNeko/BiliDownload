package cc.kafuu.bilidownload.common.utils

import java.net.URI
import java.util.Locale

/**
 * Extracts addresses that can be resolved directly by the application.
 *
 * Only Bilibili content IDs and b23.tv short links are accepted. This keeps shared text from
 * falling through to the normal keyword-search path.
 */
object BiliAddressParser {
    private val contentIdRegex = Regex(
        pattern = "(?<![A-Za-z0-9])(?:BV[A-Za-z0-9]{10}|av\\d+|ep\\d+|ss\\d+)(?![A-Za-z0-9])",
        option = RegexOption.IGNORE_CASE
    )
    private val urlRegex = Regex(
        pattern = "https?://[^\\s<>\"']+",
        option = RegexOption.IGNORE_CASE
    )

    fun extractSupportedAddress(text: String?): String? {
        if (text.isNullOrBlank()) return null

        findContentId(text)?.let { return it }
        return findB23ShortUrl(text)
    }

    fun findB23ShortUrl(text: String): String? {
        return urlRegex.findAll(text)
            .map { it.value.trimTrailingPunctuation() }
            .firstOrNull(::isB23ShortUrl)
    }

    fun containsSupportedAddress(text: String?): Boolean {
        return extractSupportedAddress(text) != null
    }

    fun findContentId(text: String): String? {
        val value = contentIdRegex.find(text)?.value ?: return null
        return value.take(2).uppercase(Locale.ROOT) + value.drop(2)
    }

    private fun isB23ShortUrl(url: String): Boolean {
        val host = runCatching { URI(url).host }.getOrNull() ?: return false
        return host.equals(B23_HOST, ignoreCase = true) ||
            host.endsWith(".$B23_HOST", ignoreCase = true)
    }

    private fun String.trimTrailingPunctuation(): String {
        return trimEnd(
            '.', ',', ';', '!', '。', '，', '；', '！', '？',
            ')', ']', '}', '）', '】'
        )
    }

    private const val B23_HOST = "b23.tv"
}
