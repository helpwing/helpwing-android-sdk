package ru.fanyagin.helpwing.core

import org.junit.Assert.assertEquals
import org.junit.Test

class PaletteTest {
    @Test
    fun whiteOnDarkAccentInkOnLightOne() {
        assertEquals("#ffffff", Palette.readableOn("#2563eb"))
        assertEquals("#101828", Palette.readableOn("#c6ff3a"))
        assertEquals("#101828", Palette.readableOn("#fff"))
    }

    @Test
    fun invalidAccentFallsBackToDefault() {
        assertEquals(Palette.DEFAULT_ACCENT, Palette.forAccent("not a colour").accent)
        assertEquals(Palette.DEFAULT_ACCENT, Palette.forAccent(null).accent)
    }

    @Test
    fun darkPaletteKeepsTheAccent() {
        val dark = Palette.forAccent("#c6ff3a", dark = true)
        assertEquals("#c6ff3a", dark.accent)
        assertEquals("#0b0f19", dark.background)
        assertEquals("#101828", dark.onAccent)
    }
}
