package com.example.voicenotes.ui.theme

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
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.voicenotes.data.ThemeMode

private val LightColorScheme = lightColorScheme(
    primary = BluePrimaryLight,
    onPrimary = BlueOnPrimaryLight,
    primaryContainer = BluePrimaryContainerLight,
    onPrimaryContainer = BlueOnPrimaryContainerLight,
    secondary = BlueSecondaryLight,
    onSecondary = BlueOnSecondaryLight,
    secondaryContainer = BlueSecondaryContainerLight,
    onSecondaryContainer = BlueOnSecondaryContainerLight,
    tertiary = BlueTertiaryLight,
    onTertiary = BlueOnTertiaryLight,
    tertiaryContainer = BlueTertiaryContainerLight,
    onTertiaryContainer = BlueOnTertiaryContainerLight,
    error = BlueErrorLight,
    onError = BlueOnErrorLight,
    errorContainer = BlueErrorContainerLight,
    onErrorContainer = BlueOnErrorContainerLight,
    background = BlueBackgroundLight,
    onBackground = BlueOnBackgroundLight,
    surface = BlueSurfaceLight,
    onSurface = BlueOnSurfaceLight,
    surfaceVariant = BlueSurfaceVariantLight,
    onSurfaceVariant = BlueOnSurfaceVariantLight,
    outline = BlueOutlineLight,
    outlineVariant = BlueOutlineVariantLight,
    scrim = BlueScrimLight,
    inverseSurface = BlueInverseSurfaceLight,
    inverseOnSurface = BlueInverseOnSurfaceLight,
    inversePrimary = BlueInversePrimaryLight,
    surfaceDim = BlueSurfaceDimLight,
    surfaceBright = BlueSurfaceBrightLight,
    surfaceContainerLowest = BlueSurfaceContainerLowestLight,
    surfaceContainerLow = BlueSurfaceContainerLowLight,
    surfaceContainer = BlueSurfaceContainerLight,
    surfaceContainerHigh = BlueSurfaceContainerHighLight,
    surfaceContainerHighest = BlueSurfaceContainerHighestLight,
)

private val DarkColorScheme = darkColorScheme(
    primary = BluePrimaryDark,
    onPrimary = BlueOnPrimaryDark,
    primaryContainer = BluePrimaryContainerDark,
    onPrimaryContainer = BlueOnPrimaryContainerDark,
    secondary = BlueSecondaryDark,
    onSecondary = BlueOnSecondaryDark,
    secondaryContainer = BlueSecondaryContainerDark,
    onSecondaryContainer = BlueOnSecondaryContainerDark,
    tertiary = BlueTertiaryDark,
    onTertiary = BlueOnTertiaryDark,
    tertiaryContainer = BlueTertiaryContainerDark,
    onTertiaryContainer = BlueOnTertiaryContainerDark,
    error = BlueErrorDark,
    onError = BlueOnErrorDark,
    errorContainer = BlueErrorContainerDark,
    onErrorContainer = BlueOnErrorContainerDark,
    background = BlueBackgroundDark,
    onBackground = BlueOnBackgroundDark,
    surface = BlueSurfaceDark,
    onSurface = BlueOnSurfaceDark,
    surfaceVariant = BlueSurfaceVariantDark,
    onSurfaceVariant = BlueOnSurfaceVariantDark,
    outline = BlueOutlineDark,
    outlineVariant = BlueOutlineVariantDark,
    scrim = BlueScrimDark,
    inverseSurface = BlueInverseSurfaceDark,
    inverseOnSurface = BlueInverseOnSurfaceDark,
    inversePrimary = BlueInversePrimaryDark,
    surfaceDim = BlueSurfaceDimDark,
    surfaceBright = BlueSurfaceBrightDark,
    surfaceContainerLowest = BlueSurfaceContainerLowestDark,
    surfaceContainerLow = BlueSurfaceContainerLowDark,
    surfaceContainer = BlueSurfaceContainerDark,
    surfaceContainerHigh = BlueSurfaceContainerHighDark,
    surfaceContainerHighest = BlueSurfaceContainerHighestDark,
)

/** Dynamic color is only available from Android 12. */
val isDynamicColorSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
fun AIVoiceNotesTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    // Prefer wallpaper / system accent on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = when {
        dynamicColor && isDynamicColorSupported -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        // The app theme can differ from the system one, so system bar icons need to follow it.
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalSpacing provides Spacing()) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = AIVoiceNotesShapes,
            content = content
        )
    }
}
