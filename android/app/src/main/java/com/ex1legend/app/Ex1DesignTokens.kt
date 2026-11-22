package com.ex1legend.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Source-derived EX1 visual tokens.
 *
 * These are the stable base surfaces/accent values found in the current HTML clients.
 * The player also supports runtime theme overrides; those must be modeled as a separate
 * state layer rather than silently replacing these defaults.
 */
object Ex1DesignTokens {
    object Player {
        val Background = Color(0xFF0B0B12)
        val Surface = Color(0xFF171725)
        val Text = Color(0xFFFFFFFF)
        val SecondaryText = Color(0xFFBBA8D8)
        val Muted = Color(0xFFA6A6B8)
        val Card = Color(0xFF191426)
        val CardAlt = Color(0xFF171128)
        val Header = Color(0xF00A0818)
        val Error = Color(0xFFFFA2A2)
        val Success = Color(0xFF45D8A1)
        val Violet = Color(0xFF8B5CF6)
        val Neon = Color(0xFFB66CFF)
        val Cyan = Color(0xFF27E6F2)
        val Pink = Color(0xFFEC4FFF)
        val Blue = Color(0xFF536DFF)
        val Glow = Color(0x805B5CFF)
        val Gold = Color(0xFFD9B45B)
        val Stroke = Color(0xFF403957)
    }

    object ControlPanel {
        val Background = Color(0xFF0B0B12)
        val Panel = Color(0xFF171725)
        val Line = Color(0xFF27273A)
        val Text = Color(0xFFF4F3FF)
        val Muted = Color(0xFFA6A6B8)
        val Purple = Color(0xFF8B5CF6)
        val Cyan = Color(0xFF27E6F2)
        val Green = Color(0xFF34D399)
        val PurpleGlow = Color(0x578B5CF6)
        val CyanGlow = Color(0x3822D3EE)
    }

    object Spacing {
        val XSmall = 4.dp
        val Small = 8.dp
        val Medium = 12.dp
        val Large = 16.dp
        val XLarge = 20.dp
        val XXLarge = 24.dp
    }

    object Corners {
        val Small = 10.dp
        val Button = 12.dp
        val Medium = 14.dp
        val Card = 20.dp
        val Pill = 999.dp
    }

    object Type {
        // HTML uses Tahoma/Arial for the player and system-ui/Tahoma/Arial for controls.
        // Android's SansSerif is a platform fallback, not a pixel-identical Tahoma port.
        val DefaultFamily = FontFamily.SansSerif
        val Caption = 10.sp
        val Small = 12.sp
        val Body = 14.sp
        val BodyLarge = 16.sp
        val Title = 20.sp
        val Heading = 24.sp
    }
}
