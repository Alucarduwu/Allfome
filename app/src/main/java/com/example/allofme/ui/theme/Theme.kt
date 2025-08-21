package com.example.allofme.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = RositaPastel,
    onPrimary = White,
    secondary = LilaSuave,
    onSecondary = GrisSuave,
    background = White,
    onBackground = GrisSuave,
    surface = RositaPastel,
    onSurface = GrisSuave,
)

@Composable
fun AllofMeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
