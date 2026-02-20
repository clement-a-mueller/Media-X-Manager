package com.example.mediaxmanager.ui.theme

import android.os.Build
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val AppTypography = Typography()

@Composable
fun MediaControllerTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            dynamicDarkColorScheme(context)
        } else {
            darkColorScheme()
        }
    MaterialTheme(
        colorScheme = colorScheme.copy(background = Color.Transparent),
        typography = AppTypography,
        content = content
    )
}