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
    // 媒体扩展名只占少量字节；为主名称及冲突序号保留空间，避免异常扩展名耗尽总预算。
    private const val MAX_EXTENSION_BYTES = 32
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

    /**
     * 清理调用方提供的完整文件名，作为写入文件系统或 SAF 文档提供方前的最后一道防线。
     *
     * 按最后一个点拆分主名称与扩展名，主名称中的非法字符替换为下划线，扩展名中的非法字符
     * 与空白直接移除。扩展名最多保留 32 个 UTF-8 字节，完整名称最多 240 字节，截取时保留
     * 完整 Unicode 码点；扩展名清理后为空时不追加点。该方法不检查重名，也不创建文件。
     *
     * @param fileName 待落盘的显示名称，可包含扩展名。
     * @param fallbackBaseName 清理后没有有效主名称时使用的回退名称。
     * @return 可安全交给文件系统或文档提供方的文件名。
     */
    fun sanitizeFileName(
        fileName: String,
        fallbackBaseName: String = DEFAULT_FALLBACK_BASE_NAME,
    ): String {
        val dotIndex = fileName.lastIndexOf('.')
        val baseName = if (dotIndex > 0) fileName.substring(0, dotIndex) else fileName
        val extension = if (dotIndex > 0) fileName.substring(dotIndex + 1) else ""
        return buildFileNameWithSuffix(
            baseName = sanitizeBaseName(baseName).ifBlank { sanitizeBaseName(fallbackBaseName) },
            extension = sanitizeExtension(extension),
            suffix = "",
        )
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

    /** 根据目标目录检查重名，追加序号时为扩展名和序号预留字节。 */
    fun resolveUniqueFileName(
        desiredName: String,
        nameExists: (String) -> Boolean,
    ): String {
        val dotIndex = desiredName.lastIndexOf('.')
        val baseName = if (dotIndex > 0) desiredName.substring(0, dotIndex) else desiredName
        val extension = if (dotIndex > 0) desiredName.substring(dotIndex + 1) else ""
        val safeExtension = sanitizeExtension(extension)
        var index = 0
        while (true) {
            val suffix = if (index == 0) "" else "($index)"
            val fileName = buildFileNameWithSuffix(baseName, safeExtension, suffix)
            if (!nameExists(fileName)) return fileName
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

    /** 清除扩展名中的受限字符并限制字节数，避免主名称或重名序号失去可用空间。 */
    private fun sanitizeExtension(value: String): String {
        val sanitized = value
            .trim()
            .trimStart('.')
            .replace(illegalFileNameCharsRegex, "")
            .replace(whitespaceRegex, "")
            .trimFileNameSeparators()
        return truncateUtf8(sanitized, MAX_EXTENSION_BYTES)
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
