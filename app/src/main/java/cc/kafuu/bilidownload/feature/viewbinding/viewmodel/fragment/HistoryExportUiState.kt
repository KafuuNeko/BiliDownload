package cc.kafuu.bilidownload.feature.viewbinding.viewmodel.fragment

import cc.kafuu.bilidownload.common.download.BatchExportUseCase

/** 导出流程的互斥阶段；目录选择请求只交付一次，进度由 ViewModel 跨视图重建保留。 */
sealed interface HistoryExportUiState {
    data object Idle : HistoryExportUiState
    data class SelectingDirectory(val requestId: Long, val launchPending: Boolean = true) : HistoryExportUiState
    data class Exporting(val progress: BatchExportUseCase.Progress) : HistoryExportUiState
}
