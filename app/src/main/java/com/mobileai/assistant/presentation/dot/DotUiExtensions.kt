package com.mobileai.assistant.presentation.dot

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.mobileai.assistant.domain.dot.DotAvatarPreset
import com.mobileai.assistant.domain.dot.DotProfile

/**
 * Parses the [DotProfile.colorHex] string into a Compose [Color].
 * Supports standard "#RRGGBB" and "#AARRGGBB" hex formats with pure JVM fallback.
 */
fun DotProfile.parseThemeColor(): Color {
    val hex = colorHex.trim().removePrefix("#")
    return try {
        when (hex.length) {
            6 -> {
                val r = hex.substring(0, 2).toInt(16)
                val g = hex.substring(2, 4).toInt(16)
                val b = hex.substring(4, 6).toInt(16)
                Color(red = r / 255f, green = g / 255f, blue = b / 255f, alpha = 1f)
            }
            8 -> {
                val a = hex.substring(0, 2).toInt(16)
                val r = hex.substring(2, 4).toInt(16)
                val g = hex.substring(4, 6).toInt(16)
                val b = hex.substring(6, 8).toInt(16)
                Color(red = r / 255f, green = g / 255f, blue = b / 255f, alpha = a / 255f)
            }
            else -> Color(0xFF4F46E5)
        }
    } catch (_: Throwable) {
        Color(0xFF4F46E5)
    }
}

/**
 * Maps a [DotAvatarPreset] to its corresponding Material [ImageVector] icon.
 */
fun DotAvatarPreset.toIcon(): ImageVector {
    return when (this) {
        DotAvatarPreset.ROBOT -> Icons.Default.SmartToy
        DotAvatarPreset.CODE -> Icons.Default.Code
        DotAvatarPreset.ANALYTICS -> Icons.Default.Analytics
        DotAvatarPreset.WRITER -> Icons.Default.EditNote
        DotAvatarPreset.SEARCH -> Icons.Default.Search
        DotAvatarPreset.BRAIN -> Icons.Default.Psychology
        DotAvatarPreset.TERMINAL -> Icons.Default.Terminal
        DotAvatarPreset.CUSTOM -> Icons.Default.Person
    }
}
