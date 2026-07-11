package world.larutan.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColors = darkColorScheme(
    primary = Ember,
    onPrimary = Ink,
    secondary = Tide,
    onSecondary = Ink,
    background = Ink,
    onBackground = TextHigh,
    surface = Slate,
    onSurface = TextHigh,
    surfaceVariant = SlateRaised,
    onSurfaceVariant = TextMid,
    outline = Line,
    error = Clay,
    onError = Ink,
)

private val LightColors = lightColorScheme(
    primary = EmberDeep,
    onPrimary = PaperSurface,
    secondary = TideDeep,
    onSecondary = PaperSurface,
    background = PaperBg,
    onBackground = PaperTextHigh,
    surface = PaperSurface,
    onSurface = PaperTextHigh,
    surfaceVariant = PaperRaised,
    onSurfaceVariant = PaperTextMid,
    outline = PaperLine,
    error = Clay,
    onError = PaperSurface,
)

/**
 * Larutan is a dark-mode-first app: unless the device explicitly asks for light,
 * it stays in the calm night palette. We deliberately do NOT follow Material You
 * dynamic colour — the world should look the same on every phone.
 */
@Composable
fun LarutanTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colors.background.toArgb()
            window.navigationBarColor = colors.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    MaterialTheme(
        colorScheme = colors,
        typography = LarutanTypography,
        content = content,
    )
}
