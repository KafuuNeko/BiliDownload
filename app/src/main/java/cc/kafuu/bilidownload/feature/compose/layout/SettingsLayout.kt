package cc.kafuu.bilidownload.feature.compose.layout

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.kafuu.bilidownload.R
import cc.kafuu.bilidownload.common.model.DownloadPathMode
import cc.kafuu.bilidownload.common.model.DownloadSourceMode
import cc.kafuu.bilidownload.common.model.DownloadSourcePreset
import cc.kafuu.bilidownload.feature.compose.viewmodel.settings.SettingsUiIntent
import cc.kafuu.bilidownload.feature.compose.viewmodel.settings.SettingsUiState
import cc.kafuu.bilidownload.feature.compose.views.AppTopBar

@Composable
fun SettingsLayout(
    state: SettingsUiState,
    onIntent: (SettingsUiIntent) -> Unit
) {
    when (state) {
        SettingsUiState.Loading -> LoadingView()
        is SettingsUiState.Normal -> SettingsContent(state, onIntent)
    }
}

@Composable
private fun LoadingView() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(R.color.general_window_background_color)),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun SettingsContent(
    state: SettingsUiState.Normal,
    onIntent: (SettingsUiIntent) -> Unit
) {
    Column(
        modifier = Modifier
            .statusBarsPadding()
            .fillMaxSize()
            .background(colorResource(R.color.general_window_background_color))
    ) {
        AppTopBar(title = stringResource(R.string.text_settings)) {
            onIntent(SettingsUiIntent.GoBack)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // 下载路径设置卡片
            DownloadPathCard(state, onIntent)

            if (state.downloadPathMode != DownloadPathMode.INTERNAL) {
                Spacer(modifier = Modifier.height(16.dp))

                ResourceFileNameCard(state, onIntent)
            }

            Spacer(modifier = Modifier.height(16.dp))

            DownloadSourceCard(state, onIntent)

            Spacer(modifier = Modifier.height(16.dp))

            MergeSettingsCard(state, onIntent)

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun MergeSettingsCard(
    state: SettingsUiState.Normal,
    onIntent: (SettingsUiIntent) -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            SettingsSwitchOption(
                title = stringResource(R.string.settings_delete_source_files_after_merge),
                description = stringResource(
                    R.string.settings_delete_source_files_after_merge_desc
                ),
                checked = state.deleteSourceFilesAfterMerge,
                onCheckedChange = {
                    onIntent(SettingsUiIntent.SetDeleteSourceFilesAfterMerge(it))
                }
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = colorResource(R.color.view_split_color),
                thickness = 0.5.dp
            )

            SettingsSwitchOption(
                title = stringResource(R.string.settings_auto_remux_audio_after_download),
                description = stringResource(
                    R.string.settings_auto_remux_audio_after_download_desc
                ),
                checked = state.autoRemuxAudioAfterDownload,
                onCheckedChange = {
                    onIntent(SettingsUiIntent.SetAutoRemuxAudioAfterDownload(it))
                }
            )
        }
    }
}

@Composable
private fun SettingsSwitchOption(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun DownloadPathCard(
    state: SettingsUiState.Normal,
    onIntent: (SettingsUiIntent) -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // 标题
            Text(
                text = stringResource(R.string.settings_download_path),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
            )

            HorizontalDivider(
                color = colorResource(R.color.view_split_color),
                thickness = 0.5.dp
            )

            // 内部存储选项
            SettingsRadioOption(
                title = stringResource(R.string.settings_download_path_internal),
                description = stringResource(R.string.settings_download_path_internal_desc),
                isSelected = state.downloadPathMode == DownloadPathMode.INTERNAL,
                onClick = { onIntent(SettingsUiIntent.SetDownloadPathMode(DownloadPathMode.INTERNAL)) }
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = colorResource(R.color.view_split_color),
                thickness = 0.5.dp
            )

            // 外部存储选项
            SettingsRadioOption(
                title = stringResource(R.string.settings_download_path_external),
                description = stringResource(R.string.settings_download_path_external_desc),
                isSelected = state.downloadPathMode == DownloadPathMode.EXTERNAL,
                onClick = { onIntent(SettingsUiIntent.SetDownloadPathMode(DownloadPathMode.EXTERNAL)) }
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = colorResource(R.color.view_split_color),
                thickness = 0.5.dp
            )

            SettingsRadioOption(
                title = stringResource(R.string.settings_download_path_external_media),
                description = stringResource(R.string.settings_download_path_external_media_desc),
                isSelected = state.downloadPathMode == DownloadPathMode.EXTERNAL_MEDIA,
                onClick = {
                    onIntent(
                        SettingsUiIntent.SetDownloadPathMode(DownloadPathMode.EXTERNAL_MEDIA)
                    )
                }
            )

            HorizontalDivider(
                color = colorResource(R.color.view_split_color),
                thickness = 0.5.dp
            )

            // 当前路径显示
            Text(
                text = stringResource(R.string.settings_download_path_current, state.currentPathDisplay),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
    }
}

@Composable
private fun ResourceFileNameCard(
    state: SettingsUiState.Normal,
    onIntent: (SettingsUiIntent) -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.settings_resource_file_name),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
            )

            HorizontalDivider(
                color = colorResource(R.color.view_split_color),
                thickness = 0.5.dp
            )

            Text(
                text = stringResource(R.string.settings_resource_file_name_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )

            ResourceFileNameTextField(
                value = state.audioResourceFileNameTemplate,
                label = stringResource(R.string.settings_resource_file_name_audio),
                onValueChange = {
                    onIntent(SettingsUiIntent.SetAudioResourceFileNameTemplate(it))
                }
            )

            ResourceFileNameTextField(
                value = state.videoResourceFileNameTemplate,
                label = stringResource(R.string.settings_resource_file_name_video),
                onValueChange = {
                    onIntent(SettingsUiIntent.SetVideoResourceFileNameTemplate(it))
                }
            )

            ResourceFileNameTextField(
                value = state.mixedResourceFileNameTemplate,
                label = stringResource(R.string.settings_resource_file_name_mixed),
                onValueChange = {
                    onIntent(SettingsUiIntent.SetMixedResourceFileNameTemplate(it))
                }
            )

            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun ResourceFileNameTextField(
    value: String,
    label: String,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        label = { Text(text = label) },
        placeholder = {
            Text(text = stringResource(R.string.settings_resource_file_name_hint))
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun DownloadSourceCard(
    state: SettingsUiState.Normal,
    onIntent: (SettingsUiIntent) -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(R.string.settings_download_source),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
            )

            HorizontalDivider(
                color = colorResource(R.color.view_split_color),
                thickness = 0.5.dp
            )

            SettingsRadioOption(
                title = stringResource(R.string.settings_download_source_default),
                description = stringResource(R.string.settings_download_source_default_desc),
                isSelected = state.downloadSourceMode == DownloadSourceMode.DEFAULT,
                onClick = {
                    onIntent(
                        SettingsUiIntent.SetDownloadSourceMode(
                            DownloadSourceMode.DEFAULT
                        )
                    )
                }
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = colorResource(R.color.view_split_color),
                thickness = 0.5.dp
            )

            SettingsRadioOption(
                title = stringResource(R.string.settings_download_source_auto_probe),
                description = stringResource(R.string.settings_download_source_auto_probe_desc),
                isSelected = state.downloadSourceMode == DownloadSourceMode.AUTO_PROBE,
                onClick = {
                    onIntent(
                        SettingsUiIntent.SetDownloadSourceMode(
                            DownloadSourceMode.AUTO_PROBE
                        )
                    )
                }
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = colorResource(R.color.view_split_color),
                thickness = 0.5.dp
            )

            SettingsRadioOption(
                title = stringResource(R.string.settings_download_source_custom_host),
                description = stringResource(R.string.settings_download_source_custom_host_desc),
                isSelected = state.downloadSourceMode == DownloadSourceMode.CUSTOM_HOST,
                onClick = {
                    onIntent(
                        SettingsUiIntent.SetDownloadSourceMode(
                            DownloadSourceMode.CUSTOM_HOST
                        )
                    )
                }
            )

            if (state.downloadSourceMode == DownloadSourceMode.CUSTOM_HOST) {
                var isPresetHostExpanded by remember { mutableStateOf(false) }

                OutlinedTextField(
                    value = state.downloadSourceCustomHost,
                    onValueChange = {
                        onIntent(SettingsUiIntent.SetDownloadSourceCustomHost(it))
                    },
                    singleLine = true,
                    label = {
                        Text(
                            text = stringResource(
                                R.string.settings_download_source_custom_host_label
                            )
                        )
                    },
                    placeholder = {
                        Text(
                            text = stringResource(
                                R.string.settings_download_source_custom_host_hint
                            )
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isPresetHostExpanded = !isPresetHostExpanded }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.settings_download_source_preset_hosts),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = if (isPresetHostExpanded) "-" else "+",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                if (isPresetHostExpanded) {
                    DownloadSourcePreset.entries.forEachIndexed { index, preset ->
                        val host = preset.host
                        SettingsRadioOption(
                            title = host,
                            description = stringResource(
                                R.string.settings_download_source_preset_host_desc
                            ),
                            isSelected = state.downloadSourceCustomHost == host,
                            onClick = {
                                onIntent(SettingsUiIntent.SetDownloadSourceCustomHost(host))
                            }
                        )

                        if (index != DownloadSourcePreset.entries.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = colorResource(R.color.view_split_color),
                                thickness = 0.5.dp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsRadioOption(
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val indicatorColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
        animationSpec = tween(durationMillis = 250),
        label = "indicatorColor"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 自定义选中指示器
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(
                    color = indicatorColor.copy(alpha = 0.15f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape
                        )
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}
