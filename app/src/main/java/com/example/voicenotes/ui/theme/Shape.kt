package com.example.voicenotes.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Material 3 Expressive-leaning shapes: softer chips, roomier cards, bold FAB/sheets.
 */
val VoiceNotesShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

/** Full-pill for FABs, recording affordances, and expressive CTAs. */
val VoiceNotesPillShape = RoundedCornerShape(percent = 50)

/** Bottom sheets / player bars — soft top corners, flat bottom. */
val VoiceNotesSheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
