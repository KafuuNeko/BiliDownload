package cc.kafuu.bilidownload.feature.compose.layout.settings

import androidx.annotation.StringRes
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.constant.DownloadResourceType
import cc.kafuu.bilidownload.common.model.DownloadSourcePreset
import cc.kafuu.bilidownload.common.utils.DownloadFileNameUtils
import cc.kafuu.bilidownload.common.utils.ExportFileNameUtils

/**
 * 将占位符变量追加到当前命名模板末尾，按需补齐连接符以减少手动编辑成本。
 *
 * @param current 当前模板字符串。
 * @param token 待插入的变量占位符（如 `{VideoName}`）。
 * @param separator 与前文内容之间的默认连接符。
 * @return 插入变量后的新模板文本。
 */
internal fun appendFileNameTemplateToken(
    current: String,
    token: String,
    separator: String,
): String {
    val trimmedEnd = current.trimEnd()
    if (trimmedEnd.isEmpty()) return token
    val lastChar = trimmedEnd.last()
    val alreadySeparated =
        lastChar == '-' || lastChar == '_' || lastChar == '/' || lastChar == '(' || lastChar == '['
    if (alreadySeparated || current.endsWith(" ")) {
        return "$current$token"
    }
    return "$current$separator$token"
}

/**
 * 返回预设下载源节点的语义化展示名称资源 ID。
 *
 * @param preset 预设下载源枚举项。
 * @return 对应的厂商与区域文案资源 ID。
 */
@StringRes
internal fun downloadSourcePresetLabelRes(preset: DownloadSourcePreset): Int = when (preset) {
    DownloadSourcePreset.COS_OVERSEA -> R.string.settings_preset_cos_oversea
    DownloadSourcePreset.BSTAR_AKAMAI -> R.string.settings_preset_bstar_akamai
    DownloadSourcePreset.HANGZHOU_AKAMAI -> R.string.settings_preset_hangzhou_akamai
    DownloadSourcePreset.ALI_OVERSEA -> R.string.settings_preset_ali_oversea
    DownloadSourcePreset.BAIDU -> R.string.settings_preset_baidu
    DownloadSourcePreset.HUAWEI -> R.string.settings_preset_huawei
    DownloadSourcePreset.MIRROR_08H -> R.string.settings_preset_mirror_08h
    DownloadSourcePreset.ALI -> R.string.settings_preset_ali
    DownloadSourcePreset.COS -> R.string.settings_preset_cos
}

/**
 * 综合三类模板判断命名配置摘要，避免单类自定义配置被合成模板的默认状态掩盖。
 *
 * @param audioTemplate 当前音频命名模板。
 * @param videoTemplate 当前视频命名模板。
 * @param mixedTemplate 当前合成文件命名模板。
 * @param isExportMode 是否按导出规则将空白模板视为默认；下载空白模板使用任务相关名称。
 * @return 表示整组配置为默认或自定义的摘要资源 ID。
 */
@StringRes
internal fun fileNameTemplateSummaryRes(
    audioTemplate: String,
    videoTemplate: String,
    mixedTemplate: String,
    isExportMode: Boolean,
): Int {
    // 下载分类有独立默认前缀，导出三类文件共用同一默认格式。
    val templates = if (isExportMode) {
        listOf(audioTemplate, videoTemplate, mixedTemplate).map { template ->
            template to ExportFileNameUtils.DEFAULT_TEMPLATE
        }
    } else {
        listOf(
            audioTemplate to DownloadFileNameUtils.DEFAULT_AUDIO_TEMPLATE,
            videoTemplate to DownloadFileNameUtils.DEFAULT_VIDEO_TEMPLATE,
            mixedTemplate to DownloadFileNameUtils.DEFAULT_MIXED_TEMPLATE,
        )
    }

    // 只有三类都采用默认规则时才显示默认；空白下载模板属于任务回退规则。
    val isDefault = templates.all { (template, defaultTemplate) ->
        val trimmedTemplate = template.trim()
        val usesBlankDefault = isExportMode && trimmedTemplate.isEmpty()
        usesBlankDefault || trimmedTemplate == defaultTemplate
    }
    return if (isDefault) {
        R.string.settings_file_name_default_summary
    } else {
        R.string.settings_file_name_custom_summary
    }
}

/**
 * 按实际命名规则渲染示例，下载任务专属回退名尚不可知时返回 null。
 *
 * @param isExportMode 是否使用导出模板及其默认回退规则。
 * @param resourceType 当前预览行对应的资源分类。
 * @param template 当前下载分类的命名模板，导出时由 [exportTemplates] 选择模板。
 * @param extension 示例资源的扩展名。
 * @param sampleContext 独立于真实下载任务的稿件名与分 P 名示例。
 * @param exportTemplates 当前三类导出模板的快照。
 * @return 示例文件名；下载模板无法生成有效主名称时返回 null，交由界面说明任务回退。
 */
internal fun renderPreviewFileName(
    isExportMode: Boolean,
    @DownloadResourceType resourceType: Int,
    template: String,
    extension: String,
    sampleContext: DownloadFileNameUtils.TemplateContext,
    exportTemplates: ExportFileNameUtils.Templates,
): String? {
    // 导出留空时回退到稿件名与分 P 名，可以直接展示完整示例。
    if (isExportMode) {
        return ExportFileNameUtils.buildFileName(
            resourceType = resourceType,
            context = sampleContext,
            extension = extension,
            templates = exportTemplates,
        )
    }
    // 下载回退名取决于实际任务，预览只展示模板本身能确定的名称。
    return DownloadFileNameUtils.buildFileNameOrNull(
        template = template,
        context = sampleContext,
        extension = extension,
    )
}
