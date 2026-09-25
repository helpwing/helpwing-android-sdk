package ru.fanyagin.helpwing.core

import org.junit.Assert.assertEquals
import org.junit.Test

class CopyTest {
    private val translated = WidgetConfig(
        greeting = "Hi! How can we help?",
        offlineMessage = "We are offline right now.",
        typingText = "{name} is typing",
        translations = mapOf(
            "ru" to mapOf(
                "greeting" to "Здравствуйте! Чем можем помочь?",
                "typing_text" to "{name} печатает",
            ),
        ),
    )

    @Test
    fun translationForTheLanguage() {
        assertEquals("Здравствуйте! Чем можем помочь?", Copy.forLocale(translated, CopyField.GREETING, "ru"))
        assertEquals("Здравствуйте! Чем можем помочь?", Copy.forLocale(translated, CopyField.GREETING, "ru-RU"))
        assertEquals("{name} печатает", Copy.forLocale(translated, CopyField.TYPING_TEXT, "ru"))
    }

    @Test
    fun projectsOwnWordsWhenUntranslated() {
        assertEquals("Hi! How can we help?", Copy.forLocale(translated, CopyField.GREETING, "de"))
        assertEquals("We are offline right now.", Copy.forLocale(translated, CopyField.OFFLINE_MESSAGE, "ru"))
        assertEquals("Hi! How can we help?", Copy.forLocale(translated, CopyField.GREETING))
        assertEquals("Hi! How can we help?", Copy.forLocale(translated.copy(translations = emptyMap()), CopyField.GREETING, "ru"))
        assertEquals("{name} is typing", Copy.forLocale(translated, CopyField.TYPING_TEXT, "de"))
    }

    @Test
    fun blankByDefault() {
        assertEquals("", Copy.forLocale(WidgetConfig(), CopyField.TYPING_TEXT, "ru"))
    }

    @Test
    fun emptyBeforeConfigArrives() {
        assertEquals("", Copy.forLocale(null, CopyField.GREETING, "ru"))
    }

    @Test
    fun matchesLoosely() {
        val cases = listOf(
            Triple("ru", listOf("ru", "en"), "ru"),
            Triple("ru-RU", listOf("ru"), "ru"),
            Triple("ru_RU", listOf("ru"), "ru"),
            Triple("RU", listOf("ru"), "ru"),
            Triple("pt-BR", listOf("pt"), "pt"),
            Triple("pt-BR", listOf("pt-br", "pt"), "pt-br"),
            Triple("de", listOf("ru", "en"), ""),
            Triple("", listOf("ru"), ""),
            Triple(null, listOf("ru"), ""),
        )
        for ((locale, available, expected) in cases) {
            assertEquals("$locale against $available", expected, Copy.match(locale, available))
        }
    }
}
