package ru.fanyagin.helpwing.core

import kotlin.math.pow

/** The chat's colours as `#rrggbb`, all derived from the project's accent. */
data class Theme(
    val accent: String,
    val onAccent: String,
    val background: String,
    val surface: String,
    val border: String,
    val text: String,
    val mutedText: String,
    val danger: String,
)

object Palette {
    const val DEFAULT_ACCENT = "#2563eb"

    fun forAccent(accent: String?, dark: Boolean = false): Theme {
        val chosen = if (accent != null && expand(accent) != null) accent else DEFAULT_ACCENT
        return if (dark) {
            Theme(
                accent = chosen,
                onAccent = readableOn(chosen),
                background = "#0b0f19",
                surface = "#1a2032",
                border = "#2a3346",
                text = "#f4f6fb",
                mutedText = "#98a2b3",
                danger = "#f97066",
            )
        } else {
            Theme(
                accent = chosen,
                onAccent = readableOn(chosen),
                background = "#ffffff",
                surface = "#f2f4f7",
                border = "#e4e7ec",
                text = "#101828",
                mutedText = "#667085",
                danger = "#d92d20",
            )
        }
    }

    /** White or ink, whichever stays readable on `color` (relative luminance, as widget.js). */
    fun readableOn(color: String): String {
        val hex = expand(color) ?: return "#ffffff"
        val channels = (0 until 3).map { index ->
            val value = hex.substring(index * 2, index * 2 + 2).toInt(16) / 255.0
            if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
        }
        val luminance = 0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2]
        return if (luminance > 0.55) "#101828" else "#ffffff"
    }

    /** `rrggbb` for `#rgb` or `#rrggbb`, or null for anything else. */
    fun expand(color: String): String? {
        var hex = color.replace("#", "")
        if (hex.length == 3) hex = hex.map { "$it$it" }.joinToString("")
        return if (Regex("^[0-9a-fA-F]{6}$").matches(hex)) hex else null
    }
}
