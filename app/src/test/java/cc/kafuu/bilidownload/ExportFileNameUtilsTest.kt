package cc.kafuu.bilidownload

import cc.kafuu.bilidownload.common.constant.DownloadResourceType
import cc.kafuu.bilidownload.common.utils.DownloadFileNameUtils
import cc.kafuu.bilidownload.common.utils.ExportFileNameUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportFileNameUtilsTest {
    private val context = DownloadFileNameUtils.TemplateContext(
        videoName = "视频标题",
        partName = "第二集",
    )

    @Test
    fun buildFileName_defaultsToOriginalExportFormatForAllResourceTypes() {
        listOf(
            DownloadResourceType.AUDIO,
            DownloadResourceType.VIDEO,
            DownloadResourceType.MIXED,
        ).forEach { type ->
            val fileName = ExportFileNameUtils.buildFileName(
                resourceType = type,
                context = context,
                extension = "mp4",
                templates = ExportFileNameUtils.Templates(),
            )

            assertEquals("视频标题 - 第二集.mp4", fileName)
        }
    }

    @Test
    fun buildFileName_usesTheTemplateForTheActualResourceType() {
        val templates = ExportFileNameUtils.Templates(
            audio = "音频-{PartName}",
            video = "视频-{VideoName}",
            mixed = "{PartName}_{VideoName}",
        )
        val expectedNames = mapOf(
            DownloadResourceType.AUDIO to "音频-第二集",
            DownloadResourceType.VIDEO to "视频-视频标题",
            DownloadResourceType.MIXED to "第二集_视频标题",
        )
        expectedNames.forEach { (type, expectedName) ->
            val fileName = ExportFileNameUtils.buildFileName(
                resourceType = type,
                context = context,
                extension = "mkv",
                templates = templates,
            )

            assertEquals("$expectedName.mkv", fileName)
        }
    }

    @Test
    fun buildFileName_fallsBackToTitleWhenTemplateIsEmptyOrInvalid() {
        listOf("", "   ", " /:*? ").forEach { template ->
            val fileName = ExportFileNameUtils.buildFileName(
                resourceType = DownloadResourceType.AUDIO,
                context = context,
                extension = "m4a",
                templates = ExportFileNameUtils.Templates(audio = template),
            )

            assertEquals("视频标题 - 第二集.m4a", fileName)
        }
    }

    @Test
    fun buildFileName_sanitizesTemplateAndTitles() {
        val fileName = ExportFileNameUtils.buildFileName(
            resourceType = DownloadResourceType.VIDEO,
            context = DownloadFileNameUtils.TemplateContext("Video/Name", "Part:2"),
            extension = "mp4",
            templates = ExportFileNameUtils.Templates(video = "目录/{PartName}?{VideoName}"),
        )

        assertEquals("目录_Part_2_Video_Name.mp4", fileName)
    }

    @Test
    fun buildFileName_keepsTheActualExtensionIncludingConvertedAndExtensionlessFiles() {
        listOf("aac", "m4a", "mp4", "mkv", "").forEach { extension ->
            val fileName = ExportFileNameUtils.buildFileName(
                resourceType = DownloadResourceType.AUDIO,
                context = context,
                extension = extension,
                templates = ExportFileNameUtils.Templates(audio = "{PartName}"),
            )
            val suffix = if (extension.isEmpty()) "" else ".$extension"

            assertEquals("第二集$suffix", fileName)
        }
    }

    @Test
    fun buildFileName_truncatesChineseAndEmojiWithoutSplittingCodePoints() {
        val fileName = ExportFileNameUtils.buildFileName(
            resourceType = DownloadResourceType.MIXED,
            context = DownloadFileNameUtils.TemplateContext("测🎵".repeat(100), "P1"),
            extension = "mp4",
            templates = ExportFileNameUtils.Templates(mixed = "{VideoName}"),
        )

        assertEquals("测🎵".repeat(33) + "测.mp4", fileName)
        assertTrue(fileName.toByteArray(Charsets.UTF_8).size <= 240)
    }
}
