package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = PrimaryAmber,
    onPrimary = CharcoalBlack,
    secondary = AccentOrange,
    background = CharcoalBlack,
    surface = CharcoalGray,
    onBackground = SoftWhite,
    onSurface = SoftWhite,
    surfaceVariant = DarkGreyDeck,
    onSurfaceVariant = MutedText
  )

private val LightColorScheme =
  lightColorScheme(
    primary = Color(0xFF118270),
    onPrimary = Color.White,
    secondary = Color(0xFF16A085),
    background = Color.White,
    surface = Color(0xFFF2F2F7),
    onBackground = Color.Black,
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFE5E5EA),
    onSurfaceVariant = Color.DarkGray
  )

@Composable
fun MyApplicationTheme(
  appTheme: Int = 2, // 0: Dark, 1: Light, 2: Adaptive
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is available on Android 12+
  dynamicColor: Boolean = true,
  content: @Composable () -> Unit,
) {
  val isDark = when (appTheme) {
    0 -> true
    1 -> false
    else -> darkTheme
  }

  val colorScheme =
    when {
      appTheme == 2 && dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }
      isDark -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
