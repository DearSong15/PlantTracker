package com.planttracker.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF4CAF50),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8E6C9),
    onPrimaryContainer = Color(0xFF1B5E20),
    secondary = Color(0xFF8BC34A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCEDC8),
    onSecondaryContainer = Color(0xFF33691E),
    tertiary = Color(0xFFFF9800),
    onTertiary = Color.White,
    background = Color(0xFFF1F8E9),
    onBackground = Color(0xFF212121),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF212121),
    surfaceVariant = Color(0xFFF5F5F5),
    outline = Color(0xFFBDBDBD),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF66BB6A),
    onPrimary = Color(0xFF1B5E20),
    primaryContainer = Color(0xFF2E7D32),
    onPrimaryContainer = Color(0xFFC8E6C9),
    secondary = Color(0xFF9CCC65),
    onSecondary = Color(0xFF1B5E20),
    secondaryContainer = Color(0xFF33691E),
    onSecondaryContainer = Color(0xFFDCEDC8),
    tertiary = Color(0xFFFFB74D),
    onTertiary = Color(0xFFE65100),
    background = Color(0xFF1B1B1B),
    onBackground = Color(0xFFE0E0E0),
    surface = Color(0xFF2E2E2E),
    onSurface = Color(0xFFE0E0E0),
    outline = Color(0xFF616161),
)

@Composable
fun PlantTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // 关闭 Material You 动态色，保持绿色主题
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
