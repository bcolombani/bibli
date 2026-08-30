package fr.bcolombani.bibli.ui.theme

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

// Palette de repli pour les appareils antérieurs à Android 12 (pas de couleur dynamique).
private val Seed = Color(0xFF3F6B4B)

private val LightColors = lightColorScheme(
    primary = Seed,
    secondary = Color(0xFF52634F),
    tertiary = Color(0xFF7A5A2F),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA5D2A8),
    secondary = Color(0xFFB9CCB3),
    tertiary = Color(0xFFE9C08A),
)

/** Material 3, couleur dynamique quand la plateforme la propose, mode sombre suivi. */
@Composable
fun BibliTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(colorScheme = colorScheme, content = content)
}
