package es.niceto.ubersdr.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val UberSdrColorScheme = darkColorScheme(
    primary = CompactActive,
    onPrimary = CompactTextPrimary,
    secondary = CompactSecondary,
    onSecondary = CompactTextPrimary,
    surface = CompactSurface,
    onSurface = CompactTextPrimary,
    surfaceVariant = CompactInactive,
    onSurfaceVariant = CompactTextSecondary,
    background = CompactBackground,
    onBackground = CompactTextPrimary
)

@Composable
fun UberSdrTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = UberSdrColorScheme,
        content = content
    )
}
