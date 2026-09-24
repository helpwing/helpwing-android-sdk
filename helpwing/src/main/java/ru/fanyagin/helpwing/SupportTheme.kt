package ru.fanyagin.helpwing

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import ru.fanyagin.helpwing.core.Palette
import ru.fanyagin.helpwing.core.Theme

/** The colours and typeface the components draw with. */
@Immutable
data class SupportTheme(
    val accent: Color,
    val onAccent: Color,
    val background: Color,
    val surface: Color,
    val border: Color,
    val text: Color,
    val mutedText: Color,
    val danger: Color,
    /** Null keeps the platform default. */
    val fontFamily: FontFamily? = null,
) {
    companion object {
        /** The project's palette with the app's overrides on top. */
        fun from(theme: Theme, override: SupportThemeOverride? = null): SupportTheme = SupportTheme(
            accent = override?.accent ?: hex(theme.accent),
            onAccent = override?.onAccent ?: hex(theme.onAccent),
            background = override?.background ?: hex(theme.background),
            surface = override?.surface ?: hex(theme.surface),
            border = override?.border ?: hex(theme.border),
            text = override?.text ?: hex(theme.text),
            mutedText = override?.mutedText ?: hex(theme.mutedText),
            danger = override?.danger ?: hex(theme.danger),
            fontFamily = override?.fontFamily,
        )

        /** `#rgb` or `#rrggbb` as a colour; anything else is unspecified. */
        fun hex(value: String): Color {
            val digits = Palette.expand(value) ?: return Color.Unspecified
            return Color("ff$digits".toLong(16))
        }
    }
}

/** Name only what differs from the project's palette. */
@Immutable
data class SupportThemeOverride(
    val accent: Color? = null,
    val onAccent: Color? = null,
    val background: Color? = null,
    val surface: Color? = null,
    val border: Color? = null,
    val text: Color? = null,
    val mutedText: Color? = null,
    val danger: Color? = null,
    val fontFamily: FontFamily? = null,
)
