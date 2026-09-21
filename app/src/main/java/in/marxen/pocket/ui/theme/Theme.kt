package `in`.marxen.pocket.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = PocketGreen,
    onPrimary = PocketWhite,
    primaryContainer = PocketGreenLight,
    onPrimaryContainer = PocketWhite,
    secondary = PocketGreenLight,
    onSecondary = PocketWhite,
    background = PocketBeige,
    onBackground = PocketText,
    surface = PocketWhite,
    onSurface = PocketText,
    surfaceVariant = PocketBeige,
    onSurfaceVariant = PocketTextSecondary,
    outline = PocketTextSecondary,
    outlineVariant = PocketTextTertiary,
    error = Color(0xFFD32F2F),
    onError = PocketWhite,
)

private val DarkColors = darkColorScheme(
    primary = PocketGreenLight,
    onPrimary = PocketWhite,
    primaryContainer = PocketGreenDark,
    onPrimaryContainer = PocketWhite,
    secondary = PocketGreenLight,
    onSecondary = PocketWhite,
    background = Color(0xFF1A2E23),
    onBackground = PocketBeige,
    surface = Color(0xFF1A2E23),
    onSurface = PocketBeige,
    surfaceVariant = Color(0xFF1A2E23),
    onSurfaceVariant = PocketTextSecondary,
    outline = PocketTextSecondary,
    outlineVariant = PocketTextTertiary,
    error = Color(0xFFEF5350),
    onError = Color(0xFF1A2E23),
)

@Composable
fun PocketTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
