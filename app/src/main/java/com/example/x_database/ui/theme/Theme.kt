package com.example.x_database.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = NotionPrimary,
    secondary = NotionSecondary,
    background = NotionBackground,
    surface = NotionSurface,
    surfaceVariant = NotionSurfaceVariant,
    onPrimary = NotionSurface,
    onSecondary = NotionSurface,
    onBackground = NotionOnSurface,
    onSurface = NotionOnSurface,
    outline = NotionOutline
)

@Composable
fun XdatabaseTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}
