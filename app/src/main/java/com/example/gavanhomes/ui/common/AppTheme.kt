package com.example.gavanhomes.ui.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * The app's light / dark choice: "light", "dark" or "system" (follow the phone). Every screen reads its colours from the tokens
 * below, so changing [mode] repaints the whole app at once. The choice is saved on the phone (SessionStore.theme).
 */
object AppTheme {
    var mode by mutableStateOf("system")
    /** What the phone itself is set to — kept up to date by the activity. */
    var systemDark by mutableStateOf(false)
    val dark: Boolean get() = mode == "dark" || (mode == "system" && systemDark)
}

/** A card, sheet or panel: white, or a deep warm grey at night. */
internal val KitCard: Color get() = if (AppTheme.dark) Color(0xFF1F1E1C) else Color.White

/** A soft inset inside a card (rows, chips, tiles). */
internal val KitSoft: Color get() = if (AppTheme.dark) Color(0xFF2A2926) else Color(0xFFF7F5F1)

/** Dividers, borders and the empty part of a bar. */
internal val KitLine: Color get() = if (AppTheme.dark) Color(0xFF3A3834) else Color(0xFFE8E5DE)

/** The brand colour for text and tags: its dark shade on light pages, its bright shade on dark ones (dark-on-dark can't be read). */
internal val Branding.accentText: Color get() = if (AppTheme.dark) base else dark
