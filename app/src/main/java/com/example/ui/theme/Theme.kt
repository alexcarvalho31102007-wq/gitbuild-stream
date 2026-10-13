package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = GitHubBlueAccent,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF1F4C82),
    onPrimaryContainer = Color(0xFFDDF4FF),
    secondary = GitHubGreenAccent,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF1B4728),
    onSecondaryContainer = Color(0xFFAFF5B4),
    tertiary = GitHubPurple,
    background = GitHubDarkBg,
    onBackground = GitHubTextPrimary,
    surface = GitHubCardBg,
    onSurface = GitHubTextPrimary,
    surfaceVariant = GitHubCardElevated,
    onSurfaceVariant = GitHubTextSecondary,
    outline = GitHubBorder,
    error = GitHubRedAccent,
    onError = Color.Black
)

private val LightColorScheme = lightColorScheme(
    primary = GitHubBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDF4FF),
    onPrimaryContainer = Color(0xFF0C2D6B),
    secondary = GitHubGreen,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFAFF5B4),
    onSecondaryContainer = Color(0xFF0F3818),
    tertiary = GitHubPurple,
    background = GitHubLightBg,
    onBackground = GitHubLightTextPrimary,
    surface = GitHubLightCard,
    onSurface = GitHubLightTextPrimary,
    surfaceVariant = Color(0xFFEAEFF5),
    onSurfaceVariant = GitHubLightTextSecondary,
    outline = GitHubLightBorder,
    error = GitHubRed,
    onError = Color.White
)

@Composable
fun GitCompilerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep developer brand consistency
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> DarkColorScheme // By default modern dark developer aesthetic
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
