package team.momentlog.app.config.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val colorScheme = lightColorScheme(
    primary = color2F6FDE,
    onPrimary = colorFFFFFF,
    primaryContainer = colorEAF1FF,
    error = colorD93B3B,
    errorContainer = colorFDECEC,
    background = colorFFFFFF,
    surface = colorFFFFFF,
    surfaceVariant = colorF5F5F5,
    onSurface = color111111,
    onSurfaceVariant = color6B6B6B,
    outline = colorE5E5E5,
)

@Composable
fun MomentlogTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colorScheme, content = content)
}
