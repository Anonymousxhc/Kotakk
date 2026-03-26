package com.akatsuki.trading.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ── Palette matching Go web version ──────────────────────────────────────────
val Bg          = Color(0xFF0A0A0A)
val Bg2         = Color(0xFF111111)
val Bg3         = Color(0xFF1A1A1A)
val Surface2    = Color(0xFF1E1E1E)
val Border      = Color(0xFF252525)
val Border2     = Color(0xFF2E2E2E)

val Txt         = Color(0xFFFFFFFF)
val TxtSec      = Color(0xFF888888)
val TxtMuted    = Color(0xFF555555)

val Green       = Color(0xFF00D084)
val GreenDim    = Color(0x2200D084)
val Red         = Color(0xFFFF4444)
val RedDim      = Color(0x22FF4444)
val Amber       = Color(0xFFFFB800)
val AmberDim    = Color(0x20FFB800)
val Blue        = Color(0xFF4FA3FF)
val BlueDim     = Color(0x204FA3FF)

val Mono = FontFamily.Monospace

private val AkatsukiColors = darkColorScheme(
    primary         = Amber,
    onPrimary       = Color.Black,
    background      = Bg,
    onBackground    = Txt,
    surface         = Bg2,
    onSurface       = Txt,
    surfaceVariant  = Bg3,
    onSurfaceVariant= TxtSec,
    outline         = Border,
    error           = Red,
    onError         = Txt,
)

@Composable
fun AkatsukiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AkatsukiColors,
        content = content,
    )
}

// ── Typography helpers ────────────────────────────────────────────────────────
val MonoSm  = TextStyle(fontFamily = Mono, fontSize = 10.sp)
val MonoMd  = TextStyle(fontFamily = Mono, fontSize = 12.sp, fontWeight = FontWeight.Bold)
val MonoLg  = TextStyle(fontFamily = Mono, fontSize = 14.sp, fontWeight = FontWeight.Bold)
val MonoXl  = TextStyle(fontFamily = Mono, fontSize = 16.sp, fontWeight = FontWeight.Bold)
