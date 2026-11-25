package com.fvprofile.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Red = Color(0xFFD32F2F)
val RedBright = Color(0xFFFF5252)
val RedDark = Color(0xFF7F1414)
val Bg = Color(0xFF0A0A0C)
val Surface1 = Color(0xFF151518)
val Surface2 = Color(0xFF1F1F25)
val Outline = Color(0xFF3F3F48)
val TextHi = Color(0xFFF7F7F8)
val TextMid = Color(0xFFC9C9D1)
val Ok = Color(0xFF66BB6A)
val Warn = Color(0xFFFFB74D)

@Composable
fun FVTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Red, onPrimary = Color.White, primaryContainer = RedDark, onPrimaryContainer = TextHi,
            secondary = RedBright, onSecondary = Color.Black, background = Bg, onBackground = TextHi,
            surface = Surface1, onSurface = TextHi, surfaceVariant = Surface2, onSurfaceVariant = TextMid,
            outline = Outline, error = RedBright, onError = Color.Black
        ),
        content = content
    )
}
