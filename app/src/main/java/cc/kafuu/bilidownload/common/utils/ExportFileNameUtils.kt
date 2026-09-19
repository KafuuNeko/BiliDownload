package cc.kafuu.bilidownload.common.utils

import cc.kafuu.bilidownload.common.constant.DownloadResourceType

/** 单个导出与批量导出共用的文件命名规则。 */
object ExportFileNameUtils {
    const val DEFAULT_TEMPLATE =
        "${DownloadFileNameUtils.VIDEO_NAME_TOKEN} - ${DownloadFileNameUtils.PART_NAME_TOKEN}"

    data class Templates(
        val audio: String = DEFAULT_TEMPLATE,
        val video: String = DEFAULT_TEMPLATE,
        val mixed: String = DEFAULT_TEMPLATE,
    )

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
