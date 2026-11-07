package com.macroflow.automation.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.macroflow.automation.domain.model.ThemeMode

val PrimaryBlue = Color(0xFF398BFF)
val AccentPurple = Color(0xFF875BFF)
val SuccessGreen = Color(0xFF3DDC97)
val WarningAmber = Color(0xFFFFB454)
val ErrorRed = Color(0xFFFF5C6C)

private val DarkColors = darkColorScheme(
    primary = PrimaryBlue,
    secondary = AccentPurple,
    tertiary = SuccessGreen,
    background = Color(0xFF090D18),
    surface = Color(0xFF0F1524),
    surfaceVariant = Color(0xFF161F33),
    onPrimary = Color.White,
    onBackground = Color(0xFFF2F5FF),
    onSurface = Color(0xFFF2F5FF),
    onSurfaceVariant = Color(0xFFA3AEC8),
    error = ErrorRed,
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1F6FE0),
    secondary = Color(0xFF6B3FE6),
    tertiary = Color(0xFF12A56B),
    background = Color(0xFFF5F7FD),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE9EEFA),
    onPrimary = Color.White,
    onBackground = Color(0xFF111827),
    onSurface = Color(0xFF111827),
    onSurfaceVariant = Color(0xFF53607A),
    error = Color(0xFFD32F45),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
)

@Composable
fun MacroFlowTheme(themeMode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        shapes = AppShapes,
        content = content,
    )
}
