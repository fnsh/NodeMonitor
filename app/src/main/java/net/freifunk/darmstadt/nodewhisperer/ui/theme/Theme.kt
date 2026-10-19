package net.freifunk.darmstadt.nodewhisperer.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Provider for colors that are not in the standard material 3 theme.
 */
@Immutable
data class CustomColorsPalette(
    val successColor: Color = Color.Unspecified,
    val warningColor: Color = Color.Unspecified
)

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)
private val DarkCustomColorsPalette = CustomColorsPalette(
    successColor = Green80,
    warningColor = Yellow80,
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)
private val LightCustomColorsPalette = CustomColorsPalette(
    successColor = Green40,
    warningColor = Yellow40,
)

// make colors usable in @Composable functions, see https://developer.android.com/develop/ui/compose/compositionlocal
val LocalCustomColorsPalette = staticCompositionLocalOf { CustomColorsPalette() }


@Composable
fun NodeWhispererTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val customPalette = if (darkTheme) DarkCustomColorsPalette else LightCustomColorsPalette

    CompositionLocalProvider(LocalCustomColorsPalette provides customPalette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}