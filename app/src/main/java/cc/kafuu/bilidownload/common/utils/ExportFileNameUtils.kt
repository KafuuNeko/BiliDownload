package cc.kafuu.bilidownload.common.utils

import cc.kafuu.bilidownload.common.constant.DownloadResourceType

/** 单个导出与批量导出共用的文件命名规则。 */
object ExportFileNameUtils {
    /** 未配置分类模板时使用稿件名与分 P 名组合。 */
    const val DEFAULT_TEMPLATE =
        "${DownloadFileNameUtils.VIDEO_NAME_TOKEN} - ${DownloadFileNameUtils.PART_NAME_TOKEN}"

    /** 音频、视频及合成资源的导出模板快照；各分类缺省使用 [DEFAULT_TEMPLATE]。 */
    data class Templates(
        val audio: String = DEFAULT_TEMPLATE,
        val video: String = DEFAULT_TEMPLATE,
        val mixed: String = DEFAULT_TEMPLATE,
    )

    /**
     * 根据资源类型选择模板，渲染经过字符清理和 UTF-8 字节长度限制的导出文件名。
     *
     * 未知资源类型使用默认模板；空白或渲染后无有效字符的模板回退到稿件名与分 P 名，
     * 两者仍不可用时由 [DownloadFileNameUtils] 使用通用名称。该方法不创建文件，重名由导出方处理。
     *
     * @param resourceType 资源分类，决定使用音频、视频或合成模板。
     * @param context 本次导出的稿件名与分 P 名快照。
     * @param extension 资源扩展名，由公共命名工具清理后追加。
     * @param templates 本次导出采用的分类模板快照。
     * @return 含有效扩展名的安全文件名；扩展名清理后为空时不追加后缀。
     */
    fun buildFileName(
        @DownloadResourceType resourceType: Int,
        context: DownloadFileNameUtils.TemplateContext,
        extension: String,
        templates: Templates,
    ): String {
        val template = when (resourceType) {
            DownloadResourceType.AUDIO -> templates.audio
            DownloadResourceType.VIDEO -> templates.video
            DownloadResourceType.MIXED -> templates.mixed
            else -> DEFAULT_TEMPLATE
        }
        return DownloadFileNameUtils.buildFileName(
            template = template,
            context = context,
            extension = extension,
            fallbackBaseName = "${context.videoName} - ${context.partName}",
        )
    }
}
