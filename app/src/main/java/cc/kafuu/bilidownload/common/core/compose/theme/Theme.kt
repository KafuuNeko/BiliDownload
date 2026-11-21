package cc.kafuu.bilidownload.common.core.compose.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDarkColor,
    onPrimary = OnPrimaryDarkColor,
    primaryContainer = PrimaryContainerDarkColor,
    onPrimaryContainer = OnPrimaryContainerDarkColor,
    secondary = SecondaryDarkColor,
    onSecondary = OnSecondaryDarkColor,
    background = BackgroundDarkColor,
    onBackground = OnBackgroundDarkColor,
    surface = SurfaceDarkColor,
    onSurface = OnSurfaceDarkColor,
    surfaceVariant = SurfaceContainerLowDarkColor,
    onSurfaceVariant = OnSurfaceVariantDarkColor,
    surfaceContainerLowest = SurfaceContainerLowestDarkColor,
    surfaceContainerLow = SurfaceContainerLowDarkColor,
    surfaceContainer = SurfaceContainerDarkColor,
    surfaceContainerHigh = SurfaceContainerHighDarkColor,
    surfaceContainerHighest = SurfaceContainerHighestDarkColor,
    outline = OutlineDarkColor,
    outlineVariant = OutlineVariantDarkColor,
    error = ErrorDarkColor,
    onError = OnErrorDarkColor,
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryColor,
    onPrimary = OnPrimaryColor,
    primaryContainer = PrimaryContainerColor,
    onPrimaryContainer = OnPrimaryContainerColor,
    secondary = SecondaryColor,
    onSecondary = OnSecondaryColor,
    background = BackgroundColor,
    onBackground = OnBackgroundColor,
    surface = SurfaceColor,
    onSurface = OnSurfaceColor,
    surfaceVariant = SurfaceContainerLowColor,
    onSurfaceVariant = OnSurfaceVariantColor,
    surfaceContainerLowest = SurfaceContainerLowestColor,
    surfaceContainerLow = SurfaceContainerLowColor,
    surfaceContainer = SurfaceContainerColor,
    surfaceContainerHigh = SurfaceContainerHighColor,
    surfaceContainerHighest = SurfaceContainerHighestColor,
    outline = OutlineColor,
    outlineVariant = OutlineVariantColor,
    error = ErrorColor,
    onError = OnErrorColor,
)

/**
 * BiliDownload 全局 Compose 主题包装。
 *
 * 默认关闭系统动态取色以与 XML 页面保持统一的品牌主色与浅深色表面层次；
 * 如确需接入系统壁纸色，可由调用方显式传入 [dynamicColor]。
 *
 * @param darkTheme 是否使用深色配色方案，默认跟随系统设置。
 * @param dynamicColor 是否在 Android 12 及以上系统启用动态取色。
 * @param content 主题作用域内的页面内容。
 */
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
