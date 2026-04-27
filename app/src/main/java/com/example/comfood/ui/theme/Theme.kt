package com.example.comfood.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = SpotifyGreen,
    secondary = LightGreen,
    tertiary = ElectricGreen,
    background = NearBlackGreen,
    surface = DarkCardGreen,
    surfaceVariant = ElevatedGreen,
    onPrimary = SoftWhite,
    onSecondary = NearBlackGreen,
    onTertiary = NearBlackGreen,
    onBackground = SoftWhite,
    onSurface = SoftWhite,
    onSurfaceVariant = MutedSage,
    outline = DarkSage
)

@Composable
fun ComFoodTheme(
    darkTheme: Boolean = true,
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && darkTheme && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            dynamicDarkColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> DarkColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
