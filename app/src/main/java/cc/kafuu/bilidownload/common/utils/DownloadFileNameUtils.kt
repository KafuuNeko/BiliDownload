package cc.kafuu.bilidownload.common.utils

import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlin.math.max

object DownloadFileNameUtils {
    const val VIDEO_NAME_TOKEN = "{VideoName}"
    const val PART_NAME_TOKEN = "{PartName}"

    const val DEFAULT_AUDIO_TEMPLATE = "Audio-$VIDEO_NAME_TOKEN-$PART_NAME_TOKEN"
    const val DEFAULT_VIDEO_TEMPLATE = "Video-$VIDEO_NAME_TOKEN-$PART_NAME_TOKEN"
    const val DEFAULT_MIXED_TEMPLATE = "$VIDEO_NAME_TOKEN-$PART_NAME_TOKEN"

    private const val DEFAULT_FALLBACK_BASE_NAME = "resource"
    private const val MAX_FILE_NAME_BYTES = 240
    private val illegalFileNameCharsRegex = Regex("""[\\/:*?"<>|\u0000-\u001F]""")
    private val whitespaceRegex = Regex("""\s+""")
    private val duplicateUnderscoreRegex = Regex("""_+""")
    private val underscoreBeforeDashRegex = Regex("""_-""")
    private val underscoreAfterDashRegex = Regex("""-_""")

    data class TemplateContext(
        val videoName: String,
        val partName: String
    )

    fun buildFileName(
        template: String,
        context: TemplateContext,
        extension: String,
        fallbackBaseName: String
    ): String {
        val baseName = renderBaseName(template, context, fallbackBaseName)
        val safeExtension = sanitizeExtension(extension)
        return buildFileNameWithSuffix(baseName, safeExtension, "")
    }

    fun buildUniqueFile(
        directory: File,
        template: String,
        context: TemplateContext,
        extension: String,
        fallbackBaseName: String
    ): File {
        return buildUniqueFile(
            directory = directory,
            baseName = renderBaseName(template, context, fallbackBaseName),
            extension = extension
        )
    }

    fun buildUniqueFile(
        directory: File,
        baseName: String,
        extension: String
    ): File {
        if (!directory.isDirectory) {
            directory.mkdirs()
        }

        val safeBaseName = sanitizeBaseName(baseName).ifBlank {
            sanitizeBaseName(DEFAULT_FALLBACK_BASE_NAME)
        }
        val safeExtension = sanitizeExtension(extension)
        val unavailableNames = directory.list()
            ?.mapTo(mutableSetOf()) { it.lowercase(Locale.ROOT) }
            ?: mutableSetOf()

        var index = 0
        while (true) {
            val suffix = if (index == 0) "" else " ($index)"
            val fileName = buildFileNameWithSuffix(safeBaseName, safeExtension, suffix)
            val unavailableName = fileName.lowercase(Locale.ROOT)
            val candidateFile = File(directory, fileName)
            if (!candidateFile.exists() && !unavailableNames.contains(unavailableName)) {
                unavailableNames.add(unavailableName)
                return candidateFile
            }
            unavailableNames.add(unavailableName)
            index++
        }
    }

    private fun renderBaseName(
        template: String,
        context: TemplateContext,
        fallbackBaseName: String
    ): String {
        val rendered = template.trim()
            .ifBlank { fallbackBaseName }
            .replace(VIDEO_NAME_TOKEN, context.videoName)
            .replace(PART_NAME_TOKEN, context.partName)

        return sanitizeBaseName(rendered).ifBlank {
            sanitizeBaseName(fallbackBaseName)
        }.ifBlank {
            DEFAULT_FALLBACK_BASE_NAME
        }
    }

    private fun sanitizeBaseName(value: String): String {
        return value
            .replace(illegalFileNameCharsRegex, "_")
            .replace(duplicateUnderscoreRegex, "_")
            .replace(underscoreBeforeDashRegex, "-")
            .replace(underscoreAfterDashRegex, "-")
            .replace(whitespaceRegex, " ")
            .trimFileNameSeparators()
    }

    private fun sanitizeExtension(value: String): String {
        return value
            .trim()
            .trimStart('.')
            .replace(illegalFileNameCharsRegex, "")
            .replace(whitespaceRegex, "")
            .trimFileNameSeparators()
    }

    private fun buildFileNameWithSuffix(
        baseName: String,
        extension: String,
        suffix: String
    ): String {
        val extensionPart = if (extension.isBlank()) "" else ".$extension"
        val maxBaseBytes = max(
            1,
            MAX_FILE_NAME_BYTES -
                suffix.toByteArray(StandardCharsets.UTF_8).size -
                extensionPart.toByteArray(StandardCharsets.UTF_8).size
        )
        val safeBaseName = sanitizeBaseName(baseName).ifBlank { DEFAULT_FALLBACK_BASE_NAME }
        val truncatedBaseName = truncateUtf8(safeBaseName, maxBaseBytes).ifBlank {
            truncateUtf8(DEFAULT_FALLBACK_BASE_NAME, maxBaseBytes).ifBlank { "r" }
        }
        return "$truncatedBaseName$suffix$extensionPart"
    }

    private fun truncateUtf8(value: String, maxBytes: Int): String {
        var byteCount = 0
        var index = 0
        val builder = StringBuilder()
        while (index < value.length) {
            val codePoint = value.codePointAt(index)
            val chars = String(Character.toChars(codePoint))
            val charBytes = chars.toByteArray(StandardCharsets.UTF_8).size
            if (byteCount + charBytes > maxBytes) break
            builder.append(chars)
            byteCount += charBytes
            index += Character.charCount(codePoint)
        }
        return builder.toString().trimFileNameSeparators()
    }

    private fun String.trimFileNameSeparators(): String {
        return trim { it <= ' ' || it == '.' || it == '-' || it == '_' }
    }
}
