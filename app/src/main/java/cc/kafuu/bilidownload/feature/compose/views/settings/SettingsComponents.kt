package cc.kafuu.bilidownload.feature.compose.views.settings

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cc.kafuu.bilidownload.R

private val SectionCardShape = RoundedCornerShape(16.dp)

private val IconBadgeShape = RoundedCornerShape(10.dp)

private val OptionCardShape = RoundedCornerShape(12.dp)

private val PillShape = RoundedCornerShape(8.dp)

private val BadgeShape = RoundedCornerShape(6.dp)

private val TrailingValueMaxWidth = 132.dp

/**
 * 设置页分组容器，由外置分组标题与统一圆角描边卡片组成。
 *
 * @param title 分组名称，展示在卡片外侧左上方以建立清晰的层级边界。
 * @param modifier 外部传入的布局修饰符。
 * @param content 分组卡片内部的设置项与分隔线内容。
 */
@Composable
fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // 分组标题放在卡片外，便于在多个设置主题之间快速定位。
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
        )
        // 卡片统一提供背景与描边，调用方只需组织组内条目。
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = SectionCardShape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
            ),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                content = content,
            )
        }
    }
}

/**
 * 设置项组内左缩进分割线，与条目文本起点对齐以减少视觉切割感。
 */
@Composable
fun SettingsInsetDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 64.dp, end = 16.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

/**
 * 左侧柔和圆角图标徽章，用于提升设置项的快速扫视辨识度。
 *
 * @param iconRes 矢量图标资源 ID。
 * @param highlighted 是否以品牌浅容器高亮强调当前激活项。
 * @param enabled 条目是否处于可用状态。
 */
@Composable
fun SettingsIconBadge(
    @DrawableRes iconRes: Int,
    highlighted: Boolean = false,
    enabled: Boolean = true,
) {
    val isActive = highlighted && enabled
    val containerColor by animateColorAsState(
        targetValue = if (isActive) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = tween(durationMillis = 200),
        label = "settingsBadgeContainer",
    )
    val iconColor by animateColorAsState(
        targetValue = if (isActive) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(durationMillis = 200),
        label = "settingsBadgeIcon",
    )

    // 禁用态与非高亮态回退到中性低对比度容器
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(IconBadgeShape)
            .background(color = containerColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(19.dp),
        )
    }
}

/**
 * 可跳转或弹出底部配置面板的设置摘要行。
 *
 * @param iconRes 左侧图标资源 ID。
 * @param title 设置项主标题。
 * @param onClick 点击条目触发的操作回调。
 * @param modifier 外部布局修饰符。
 * @param subtitle 主标题下方的说明或摘要文案。
 * @param trailingValue 右侧紧邻引导箭头的简短状态值。
 * @param highlighted 是否高亮左侧图标徽章。
 * @param enabled 条目是否可交互。
 * @param extraContent 附加在主体行下方且与文本左对齐的补充区域（如当前路径胶囊）。
 */
@Composable
fun SettingsNavigationItem(
    @DrawableRes iconRes: Int,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailingValue: String? = null,
    highlighted: Boolean = false,
    enabled: Boolean = true,
    extraContent: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.52f)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        // 主行展示图标、标题摘要与受控宽度的右侧状态值，避免大字体下挤压主标题
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 36.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingsIconBadge(iconRes = iconRes, highlighted = highlighted, enabled = enabled)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (!trailingValue.isNullOrBlank()) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = trailingValue,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = TrailingValueMaxWidth),
                )
            }
            if (enabled) {
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    painter = painterResource(id = R.drawable.ic_arrow_right),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        // 存在附加展示区时与主文本列对齐展开
        if (extraContent != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Box(modifier = Modifier.padding(start = 48.dp)) {
                extraContent()
            }
        }
    }
}

/**
 * 整行作为原子无障碍开关的设置条目。
 *
 * 将交互语义统一收敛在整行的 [toggleable] 中，内部 [Switch] 不单独接收回调，
 * 避免双击波纹重叠与屏幕阅读器拆分为两个独立焦点。
 *
 * @param iconRes 左侧图标资源 ID。
 * @param title 开关标题。
 * @param description 开关说明文案。
 * @param checked 当前开关状态。
 * @param onCheckedChange 开关状态变化回调。
 * @param modifier 外部布局修饰符。
 * @param enabled 是否允许切换。
 * @param statusBadge 可选的状态微徽章（如“设备不支持”）。
 */
@Composable
fun SettingsSwitchItem(
    @DrawableRes iconRes: Int,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    statusBadge: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .alpha(if (enabled) 1f else 0.62f)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsIconBadge(iconRes = iconRes, highlighted = checked, enabled = enabled)
        Spacer(modifier = Modifier.width(12.dp))

        // 标题行支持可选状态徽章，副文案保留舒适行高
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (!statusBadge.isNullOrBlank()) {
                    SettingsMicroBadge(
                        text = statusBadge,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // 内部 Switch 仅负责视觉呈现，触控事件由外层 Row 统一接管
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = null,
            thumbContent = if (checked) {
                { SettingsSwitchThumbIcon() }
            } else {
                null
            },
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

/**
 * 底部选择面板中的单选选项卡片。
 *
 * 选中时复用应用品牌浅粉容器与主色描边，并提供清晰的圆环与实心对勾状态切换。
 *
 * @param title 选项主标题。
 * @param description 选项说明文案。
 * @param isSelected 是否为当前选中项。
 * @param onClick 点击选择回调。
 * @param modifier 外部布局修饰符。
 * @param badge 标题旁的可选强调徽章（如“测速优选”）。
 */
@Composable
fun SettingsSelectableOptionCard(
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
) {
    // 根据选中态平滑过渡卡片容器底色与品牌描边色
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        animationSpec = tween(durationMillis = 180),
        label = "optionCardContainer",
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant
        },
        animationSpec = tween(durationMillis = 180),
        label = "optionCardBorder",
    )

    // 整张卡片声明标准单选无障碍语义
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(OptionCardShape)
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick),
        shape = OptionCardShape,
        color = containerColor,
        border = BorderStroke(width = if (isSelected) 1.5.dp else 1.dp, color = borderColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            SettingsRadioIndicator(isSelected = isSelected, modifier = Modifier.padding(top = 1.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                OptionCardHeader(title = title, isSelected = isSelected, badge = badge)
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 以等宽字体展示当前保存路径的轻质信息胶囊。
 *
 * @param label 路径前置短标签（如“目录”）。
 * @param path 当前生效的路径文本。
 * @param modifier 外部布局修饰符。
 */
@Composable
fun SettingsPathPill(
    label: String,
    path: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(PillShape)
            .background(color = MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // 短标签保留自然宽度，避免路径文本挤掉信息类别。
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // 路径使用剩余空间并限制行数，兼顾长目录与列表高度。
        Text(
            text = path,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * 设置页紧凑状态微徽章，用于承载次级属性或能力提示。
 *
 * @param text 徽章文本。
 * @param containerColor 徽章底色。
 * @param contentColor 徽章文字颜色。
 */
@Composable
fun SettingsMicroBadge(
    text: String,
    containerColor: Color,
    contentColor: Color,
) {
    Box(
        modifier = Modifier
            .clip(BadgeShape)
            .background(color = containerColor)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            maxLines = 1,
        )
    }
}

@Composable
private fun SettingsSwitchThumbIcon() {
    Icon(
        painter = painterResource(id = R.drawable.ic_settings_check),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(SwitchDefaults.IconSize),
    )
}

@Composable
private fun OptionCardHeader(
    title: String,
    isSelected: Boolean,
    badge: String?,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 主标题优先占用可用空间，选中时与卡片容器采用配套文字色。
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            ),
            color = if (isSelected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.weight(1f, fill = false),
        )
        // 辅助徽章独立于标题布局，长标题换行时仍能辨识选项属性。
        if (!badge.isNullOrBlank()) {
            SettingsMicroBadge(
                text = badge,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
private fun SettingsRadioIndicator(
    isSelected: Boolean,
    modifier: Modifier = Modifier,
) {
    if (isSelected) {
        // 实心底色与对勾共同表达选中态，避免仅通过颜色传达状态。
        Box(
            modifier = modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(color = MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_settings_check),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(13.dp),
            )
        }
    } else {
        // 空心外环保留未选中项的位置，便于逐行比较选项。
        Box(
            modifier = modifier
                .size(20.dp)
                .clip(CircleShape)
                .border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = CircleShape,
                ),
        )
    }
}
