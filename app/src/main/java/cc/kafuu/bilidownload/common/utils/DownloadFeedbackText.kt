package cc.kafuu.bilidownload.common.utils

import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.CommonLibs
import cc.kafuu.bilidownload.common.download.DownloadFailure
import cc.kafuu.bilidownload.common.download.DownloadRetry

/** 将脱敏的下载状态统一转换为通知和详情页文案，不展示原始网络异常。 */
object DownloadFeedbackText {
    /** 描述当前自动恢复次数，不将临时网络故障展示为最终失败。 */
    fun retry(value: DownloadRetry): String = CommonLibs.getString(
        R.string.download_retrying, value.attempt, value.maxRetries
    )

    /** 返回有操作指引的最终失败原因；无运行态快照时保留通用失败提示。 */
    fun failure(value: DownloadFailure?): String = when (value?.kind) {
        DownloadFailure.Kind.NETWORK -> CommonLibs.getString(R.string.download_failure_network)
        DownloadFailure.Kind.HTTP -> CommonLibs.getString(R.string.download_failure_http, value.httpCode ?: 0)
        DownloadFailure.Kind.STORAGE -> CommonLibs.getString(R.string.download_failure_storage)
        DownloadFailure.Kind.INVALID_RESPONSE -> CommonLibs.getString(R.string.download_failure_response)
        DownloadFailure.Kind.SOURCE_UNAVAILABLE -> CommonLibs.getString(R.string.download_failure_source)
        else -> CommonLibs.getString(R.string.text_download_failed)
    }
}
