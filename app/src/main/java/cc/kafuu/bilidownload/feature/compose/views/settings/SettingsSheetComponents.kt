package cc.kafuu.bilidownload.feature.compose.views.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import cc.kafuu.bilidownload.R

private val InputFieldShape = RoundedCornerShape(12.dp)

private val ChipShape = RoundedCornerShape(10.dp)

/**
 * 通用设置底部面板容器，统一处理圆角、滚动、导航栏留白与软键盘避让。
 *
 * @param onDismiss 关闭面板回调。
 * @param content 面板主体内容。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsBottomSheetFrame(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    // 直接展开到可用高度，避免输入型面板停留在遮挡字段的半展开状态。
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        // 在系统栏和键盘留白内滚动内容，保证底部输入框仍可到达。
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(state = rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        ) {
            content()
        }
    }
}

/**
 * 底部配置面板标准头部，包含面板标题、可选右侧辅助动作及关闭按钮。
 *
 * @param title 面板标题。
 * @param onDismiss 关闭面板回调。
 * @param actionText 可选的右侧文字动作标签（如“恢复默认”）。
 * @param onActionClick 右侧文字动作点击回调。
 */
@Composable
fun SettingsSheetHeader(
    title: String,
    onDismiss: () -> Unit,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // 头部左侧展示面板标题，右侧按需排列重置动作与关闭图标
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (!actionText.isNullOrBlank() && onActionClick != null) {
                TextButton(onClick = onActionClick) {
                    Text(
                        text = actionText,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_action_close),
                    contentDescription = stringResource(
                        id = R.string.settings_close_sheet_content_description,
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        // 分隔线保持在所有头部操作下方，明确面板内容的起点。
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

/**
 * 设置面板共用的单行输入框，统一清空操作、键盘动作与等宽文本样式。
 *
 * @param value 当前输入值，由调用方维护。
 * @param label 输入框类别标签。
 * @param placeholder 空输入时的格式提示。
 * @param imeAction 软键盘显示的操作类型。
 * @param onValueChange 回传编辑或清空后的完整输入值。
 * @param modifier 调用方提供的布局与焦点修饰符。
 * @param onImeAction 点击软键盘 Done 时执行的回调。
 */
@Composable
internal fun SettingsOutlinedInput(
    value: String,
    label: String,
    placeholder: String,
    imeAction: ImeAction,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    onImeAction: () -> Unit = {},
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        shape = InputFieldShape,
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        label = { Text(text = label) },
        placeholder = {
            // 空态提示沿用输入区域的等宽字体，便于识别域名和模板符号。
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        },
        // 只对非空输入提供清空操作，变更仍走页面的统一设置回调。
        trailingIcon = if (value.isNotEmpty()) {
            {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_action_close),
                        contentDescription = stringResource(
                            id = R.string.settings_clear_input_content_description,
                        ),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        } else {
            null
        },
        // Next 沿用默认焦点遍历；Done 由调用方按面板需要清理焦点。
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onImeAction() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * 展示可插入的模板变量，由编辑器决定插入目标与连接规则。
 *
 * @param token 原样展示的变量占位符。
 * @param onClick 请求向当前模板插入该变量。
 */
@Composable
internal fun SettingsTemplateTokenChip(
    token: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(ChipShape)
            .background(color = MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = stringResource(id = R.string.settings_file_name_insert_token, token),
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

/**
 * 展示预设节点标签与选中状态，点击后由调用方回填节点配置。
 *
 * @param label 本地化的节点名称。
 * @param isSelected 当前配置是否使用此节点。
 * @param onClick 请求选择该节点。
 */
@Composable
internal fun SettingsPresetNodeChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .clip(ChipShape)
            .clickable(onClick = onClick),
        shape = ChipShape,
        // 容器色与描边共同标记当前 Host，保持未选中节点的视觉层级。
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = if (isSelected) {
            BorderStroke(width = 1.dp, color = MaterialTheme.colorScheme.primary)
        } else {
            null
        },
    ) {
        // 标签字重与容器状态同步，回填后仍能快速定位当前节点。
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            ),
            color = if (isSelected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}
