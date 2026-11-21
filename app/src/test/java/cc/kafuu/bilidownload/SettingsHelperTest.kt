package cc.kafuu.bilidownload

import cc.kafuu.bilidownload.common.constant.DownloadResourceType
import cc.kafuu.bilidownload.common.model.DownloadSourcePreset
import cc.kafuu.bilidownload.common.utils.DownloadFileNameUtils
import cc.kafuu.bilidownload.common.utils.ExportFileNameUtils
import cc.kafuu.bilidownload.feature.compose.layout.settings.appendFileNameTemplateToken
import cc.kafuu.bilidownload.feature.compose.layout.settings.downloadSourcePresetLabelRes
import cc.kafuu.bilidownload.feature.compose.layout.settings.fileNameTemplateSummaryRes
import cc.kafuu.bilidownload.feature.compose.layout.settings.renderPreviewFileName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsHelperTest {
    private val sampleContext = DownloadFileNameUtils.TemplateContext(
        videoName = "Sample/Video",
        partName = "P1",
    )

    @Test
    fun appendFileNameTemplateToken_returnsTokenDirectlyWhenBlank() {
        val result = appendFileNameTemplateToken(
            current = "   ",
            token = DownloadFileNameUtils.VIDEO_NAME_TOKEN,
            separator = "-",
        )

        assertEquals(DownloadFileNameUtils.VIDEO_NAME_TOKEN, result)
    }

    @Test
    fun appendFileNameTemplateToken_addsSeparatorBetweenAdjacentTokens() {
        val result = appendFileNameTemplateToken(
            current = "{VideoName}",
            token = DownloadFileNameUtils.PART_NAME_TOKEN,
            separator = " - ",
        )

        assertEquals("{VideoName} - {PartName}", result)
    }

    @Test
    fun appendFileNameTemplateToken_avoidsDuplicateSeparatorWhenTrailingSeparatorExists() {
        val dashResult = appendFileNameTemplateToken(
            current = "{VideoName}-",
            token = DownloadFileNameUtils.PART_NAME_TOKEN,
            separator = "-",
        )
        val spaceResult = appendFileNameTemplateToken(
            current = "{VideoName} - ",
            token = DownloadFileNameUtils.PART_NAME_TOKEN,
            separator = " - ",
        )

        assertEquals("{VideoName}-{PartName}", dashResult)
        assertEquals("{VideoName} - {PartName}", spaceResult)
    }

    @Test
    fun downloadSourcePresetLabelRes_providesUniqueResourceForEveryPreset() {
        val labelResIds = DownloadSourcePreset.entries.map { preset ->
            downloadSourcePresetLabelRes(preset = preset)
        }

        assertTrue(labelResIds.all { resId -> resId != 0 })
        assertEquals(DownloadSourcePreset.entries.size, labelResIds.toSet().size)
    }

    /** 下载与导出任一分类自定义后，整组摘要都必须显示自定义。 */
    @Test
    fun fileNameTemplateSummaryRes_detectsCustomTemplatesInEveryCategory() {
        // 两种命名模式使用各自真实默认值，逐类改变配置而非只覆盖合成模板。
        val namingModes = listOf(
            false to listOf(
                DownloadFileNameUtils.DEFAULT_AUDIO_TEMPLATE,
                DownloadFileNameUtils.DEFAULT_VIDEO_TEMPLATE,
                DownloadFileNameUtils.DEFAULT_MIXED_TEMPLATE,
            ),
            true to listOf(
                ExportFileNameUtils.DEFAULT_TEMPLATE,
                ExportFileNameUtils.DEFAULT_TEMPLATE,
                ExportFileNameUtils.DEFAULT_TEMPLATE,
            ),
        )

        // 音频、视频、合成中的单项变更都应被摘要识别。
        namingModes.forEach { (isExportMode, defaultTemplates) ->
            defaultTemplates.indices.forEach { customCategoryIndex ->
                val templates = defaultTemplates.mapIndexed { categoryIndex, template ->
                    if (categoryIndex == customCategoryIndex) "custom-name" else template
                }
                val summaryRes = fileNameTemplateSummaryRes(
                    audioTemplate = templates[0],
                    videoTemplate = templates[1],
                    mixedTemplate = templates[2],
                    isExportMode = isExportMode,
                )
                assertEquals(R.string.settings_file_name_custom_summary, summaryRes)
            }
        }
    }

    /** 模板外围空白会由实际命名工具去除，摘要同样将其视为默认配置。 */
    @Test
    fun fileNameTemplateSummaryRes_recognizesDefaultTemplatesForBothModes() {
        // 下载三类默认模板含不同前缀，均须正确匹配。
        assertEquals(
            R.string.settings_file_name_default_summary,
            fileNameTemplateSummaryRes(
                audioTemplate = " ${DownloadFileNameUtils.DEFAULT_AUDIO_TEMPLATE} ",
                videoTemplate = " ${DownloadFileNameUtils.DEFAULT_VIDEO_TEMPLATE} ",
                mixedTemplate = " ${DownloadFileNameUtils.DEFAULT_MIXED_TEMPLATE} ",
                isExportMode = false,
            ),
        )

        // 导出三类共用同一默认模板。
        assertEquals(
            R.string.settings_file_name_default_summary,
            fileNameTemplateSummaryRes(
                audioTemplate = ExportFileNameUtils.DEFAULT_TEMPLATE,
                videoTemplate = ExportFileNameUtils.DEFAULT_TEMPLATE,
                mixedTemplate = ExportFileNameUtils.DEFAULT_TEMPLATE,
                isExportMode = true,
            ),
        )
    }

    /** 空白下载模板使用任务名，空白导出模板使用默认格式，两者不能共用默认判定。 */
    @Test
    fun fileNameTemplateSummaryRes_distinguishesBlankDownloadsFromDefaultExports() {
        // 为两种命名模式明确声明空白模板对应的展示结果。
        val cases = listOf(
            false to R.string.settings_file_name_custom_summary,
            true to R.string.settings_file_name_default_summary,
        )
        // 同时覆盖空串、空格与制表符，保持摘要和实际命名的空白语义一致。
        cases.forEach { (isExportMode, expectedSummaryRes) ->
            assertEquals(
                expectedSummaryRes,
                fileNameTemplateSummaryRes(
                    audioTemplate = "",
                    videoTemplate = "   ",
                    mixedTemplate = "\t",
                    isExportMode = isExportMode,
                ),
            )
        }
    }

    /** 下载模板为空或清理后无名称时，预览必须说明任务回退，不能虚构默认文件名。 */
    @Test
    fun renderPreviewFileName_returnsNullForUnusableDownloadTemplates() {
        listOf("", "   ", "---", "////", "...").forEach { template ->
            val fileName = renderPreviewFileName(
                isExportMode = false,
                resourceType = DownloadResourceType.MIXED,
                template = template,
                extension = "mp4",
                sampleContext = sampleContext,
                exportTemplates = ExportFileNameUtils.Templates(),
            )
            assertNull(fileName)
        }
    }

    /** 有效下载模板继续按实际字符清理规则展示完整示例。 */
    @Test
    fun renderPreviewFileName_rendersValidDownloadTemplates() {
        val fileName = renderPreviewFileName(
            isExportMode = false,
            resourceType = DownloadResourceType.VIDEO,
            template = DownloadFileNameUtils.DEFAULT_VIDEO_TEMPLATE,
            extension = "mp4",
            sampleContext = sampleContext,
            exportTemplates = ExportFileNameUtils.Templates(),
        )
        assertEquals("Video-Sample_Video-P1.mp4", fileName)
    }

    /** 导出模板为空或无有效字符时，仍按正式导出规则预览稿件名与分 P 名。 */
    @Test
    fun renderPreviewFileName_keepsDefaultExportFallback() {
        listOf("", "---", "////").forEach { template ->
            val fileName = renderPreviewFileName(
                isExportMode = true,
                resourceType = DownloadResourceType.MIXED,
                template = template,
                extension = "mp4",
                sampleContext = sampleContext,
                exportTemplates = ExportFileNameUtils.Templates(mixed = template),
            )
            assertEquals("Sample_Video - P1.mp4", fileName)
        }
    }
}
