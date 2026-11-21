package cc.kafuu.bilidownload.feature.compose.layout.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.core.compose.ActivityPreview
import cc.kafuu.bilidownload.common.model.BatchQualityMismatchMode
import cc.kafuu.bilidownload.common.model.DownloadPathMode
import cc.kafuu.bilidownload.common.model.DownloadSourceMode
import cc.kafuu.bilidownload.common.model.DownloadSourcePreset
import cc.kafuu.bilidownload.common.utils.DownloadFileNameUtils
import cc.kafuu.bilidownload.common.utils.ExportFileNameUtils
import cc.kafuu.bilidownload.feature.compose.viewmodel.settings.SettingsUiIntent
import cc.kafuu.bilidownload.feature.compose.viewmodel.settings.SettingsUiState

private val SettingsContentMaxWidth = 640.dp

private enum class SettingsSheet {
    DOWNLOAD_PATH,
    RESOURCE_FILE_NAME,
    EXPORT_FILE_NAME,
    DOWNLOAD_SOURCE,
    BATCH_QUALITY_MISMATCH,
}

/**
 * 设置页顶层布局入口，根据加载状态切换占位加载视图与分组配置列表。
 *
 * @param state 设置页当前不可变状态快照。
 * @param onIntent 向上层派发用户设置意图的回调。
 */
@Composable
fun SettingsLayout(
    state: SettingsUiState,
    onIntent: (SettingsUiIntent) -> Unit,
) {
    when (state) {
        SettingsUiState.Loading -> LoadingView()
        is SettingsUiState.Normal -> SettingsContent(state = state, onIntent = onIntent)
    }
}

@Composable
private fun LoadingView() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color = MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsContent(
    state: SettingsUiState.Normal,
    onIntent: (SettingsUiIntent) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var activeSheet by rememberSaveable { mutableStateOf<SettingsSheet?>(null) }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(connection = scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            SettingsTopBar(
                scrollBehavior = scrollBehavior,
                onBackClick = { onIntent(SettingsUiIntent.GoBack) },
            )
        },
    ) { innerPadding ->
        // 居中约束内容最大宽度，兼顾横屏与平板阅读舒适度
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues = innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = SettingsContentMaxWidth)
                    .fillMaxWidth()
                    .verticalScroll(state = rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                StorageAndNamingSection(
                    state = state,
                    onOpenPathSheet = { activeSheet = SettingsSheet.DOWNLOAD_PATH },
                    onOpenResourceFileNameSheet = {
                        activeSheet = SettingsSheet.RESOURCE_FILE_NAME
                    },
                    onOpenExportFileNameSheet = { activeSheet = SettingsSheet.EXPORT_FILE_NAME },
                )

                DownloadAndNetworkSection(
                    state = state,
                    onOpenSourceSheet = { activeSheet = SettingsSheet.DOWNLOAD_SOURCE },
                    onOpenBatchQualitySheet = {
                        activeSheet = SettingsSheet.BATCH_QUALITY_MISMATCH
                    },
                )

                PostProcessingSection(state = state, onIntent = onIntent)

                PlaybackSection(state = state, onIntent = onIntent)
            }
        }
    }

    // 根据当前激活的底部面板类型挂载对应配置编辑器
    SettingsActiveSheetHost(
        activeSheet = activeSheet,
        state = state,
        onIntent = onIntent,
        onDismiss = { activeSheet = null },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTopBar(
    scrollBehavior: TopAppBarScrollBehavior,
    onBackClick: () -> Unit,
) {
    val showDivider = scrollBehavior.state.overlappedFraction > 0.01f

    Column(modifier = Modifier.fillMaxWidth()) {
        // 返回操作交给页面意图通道，顶栏只根据滚动状态调整表面层次。
        TopAppBar(
            title = {
                Text(
                    text = stringResource(id = R.string.text_settings),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_arrow_back_2),
                        contentDescription = stringResource(id = R.string.iv_back_content_description),
                        modifier = Modifier.size(20.dp),
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                scrolledContainerColor = MaterialTheme.colorScheme.surface,
                navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
            ),
            scrollBehavior = scrollBehavior,
        )
        // 列表向上滚动后淡入底部分割线，与内容区形成明确视觉边界
        AnimatedVisibility(
            visible = showDivider,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}

@Composable
private fun SettingsActiveSheetHost(
    activeSheet: SettingsSheet?,
    state: SettingsUiState.Normal,
    onIntent: (SettingsUiIntent) -> Unit,
    onDismiss: () -> Unit,
) {
    // 按当前激活项弹出对应底部面板，下载与导出命名复用同一套模板编辑器
    when (activeSheet) {
        // 路径切换可能触发权限请求，面板持续展示实际已生效的页面状态。
        SettingsSheet.DOWNLOAD_PATH -> DownloadPathSheet(
            state = state,
            onIntent = onIntent,
            onDismiss = onDismiss,
        )

        // 两套命名配置共用视图结构，但分别发送下载与导出的模板变更意图。
        SettingsSheet.RESOURCE_FILE_NAME -> FileNameTemplateSheet(
            title = stringResource(id = R.string.settings_resource_file_name),
            description = stringResource(id = R.string.settings_resource_file_name_desc),
            hint = stringResource(id = R.string.settings_resource_file_name_hint),
            tokenSeparator = "-",
            isExportMode = false,
            audioTemplate = state.audioResourceFileNameTemplate,
            videoTemplate = state.videoResourceFileNameTemplate,
            mixedTemplate = state.mixedResourceFileNameTemplate,
            onAudioChange = { template ->
                onIntent(SettingsUiIntent.SetAudioResourceFileNameTemplate(template = template))
            },
            onVideoChange = { template ->
                onIntent(SettingsUiIntent.SetVideoResourceFileNameTemplate(template = template))
            },
            onMixedChange = { template ->
                onIntent(SettingsUiIntent.SetMixedResourceFileNameTemplate(template = template))
            },
            onReset = { onIntent(SettingsUiIntent.ResetResourceFileNameTemplates) },
            onDismiss = onDismiss,
        )

        SettingsSheet.EXPORT_FILE_NAME -> FileNameTemplateSheet(
            title = stringResource(id = R.string.settings_export_file_name),
            description = stringResource(id = R.string.settings_export_file_name_desc),
            hint = stringResource(id = R.string.settings_export_file_name_hint),
            tokenSeparator = " - ",
            isExportMode = true,
            audioTemplate = state.audioExportFileNameTemplate,
            videoTemplate = state.videoExportFileNameTemplate,
            mixedTemplate = state.mixedExportFileNameTemplate,
            onAudioChange = { template ->
                onIntent(SettingsUiIntent.SetAudioExportFileNameTemplate(template = template))
            },
            onVideoChange = { template ->
                onIntent(SettingsUiIntent.SetVideoExportFileNameTemplate(template = template))
            },
            onMixedChange = { template ->
                onIntent(SettingsUiIntent.SetMixedExportFileNameTemplate(template = template))
            },
            onReset = { onIntent(SettingsUiIntent.ResetExportFileNameTemplates) },
            onDismiss = onDismiss,
        )

        SettingsSheet.DOWNLOAD_SOURCE -> DownloadSourceSheet(
            state = state,
            onIntent = onIntent,
            onDismiss = onDismiss,
        )

        SettingsSheet.BATCH_QUALITY_MISMATCH -> BatchQualityMismatchSheet(
            state = state,
            onIntent = onIntent,
            onDismiss = onDismiss,
        )

        null -> Unit
    }
}

private fun previewNormalState(
    downloadPathMode: DownloadPathMode = DownloadPathMode.EXTERNAL_MEDIA,
    isPictureInPictureSupported: Boolean = true,
): SettingsUiState.Normal = SettingsUiState.Normal(
    downloadPathMode = downloadPathMode,
    currentPathDisplay = when (downloadPathMode) {
        DownloadPathMode.INTERNAL -> "/Android/data/cc.kafuu.bilidownload/files/resources"
        DownloadPathMode.EXTERNAL -> "/storage/emulated/0/Download/BVD"
        DownloadPathMode.EXTERNAL_MEDIA -> "/storage/emulated/0/Movies/BVD"
    },
    downloadSourceMode = DownloadSourceMode.CUSTOM_HOST,
    downloadSourceCustomHost = DownloadSourcePreset.ALI.host,
    batchQualityMismatchMode = BatchQualityMismatchMode.AUTO_FALLBACK,
    deleteSourceFilesAfterMerge = true,
    autoRemuxAudioAfterDownload = false,
    pictureInPicturePlaybackEnabled = isPictureInPictureSupported,
    isPictureInPictureSupported = isPictureInPictureSupported,
    audioResourceFileNameTemplate = DownloadFileNameUtils.DEFAULT_AUDIO_TEMPLATE,
    videoResourceFileNameTemplate = DownloadFileNameUtils.DEFAULT_VIDEO_TEMPLATE,
    mixedResourceFileNameTemplate = DownloadFileNameUtils.DEFAULT_MIXED_TEMPLATE,
    audioExportFileNameTemplate = ExportFileNameUtils.DEFAULT_TEMPLATE,
    videoExportFileNameTemplate = ExportFileNameUtils.DEFAULT_TEMPLATE,
    mixedExportFileNameTemplate = ExportFileNameUtils.DEFAULT_TEMPLATE,
)

@Preview(name = "Settings Light", showBackground = true)
@Composable
private fun SettingsLayoutLightPreview() {
    ActivityPreview(darkTheme = false) {
        SettingsLayout(
            state = previewNormalState(),
            onIntent = {},
        )
    }
}

@Preview(name = "Settings Dark", showBackground = true)
@Composable
private fun SettingsLayoutDarkPreview() {
    ActivityPreview(darkTheme = true) {
        SettingsLayout(
            state = previewNormalState(),
            onIntent = {},
        )
    }
}

@Preview(name = "Settings Large Font", showBackground = true, fontScale = 1.5f)
@Composable
private fun SettingsLayoutLargeFontPreview() {
    ActivityPreview(darkTheme = false) {
        SettingsLayout(
            state = previewNormalState(
                downloadPathMode = DownloadPathMode.INTERNAL,
                isPictureInPictureSupported = false,
            ),
            onIntent = {},
        )
    }
}
