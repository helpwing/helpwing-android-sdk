package ru.fanyagin.helpwing.core

/** The strings a project writes in the dashboard. */
enum class CopyField(val key: String) {
    TITLE("title"),
    GREETING("greeting"),
    OFFLINE_MESSAGE("offline_message"),
}

/** The project's own words in the app's language, falling back to what it wrote untranslated. */
object Copy {
    fun forLocale(config: WidgetConfig?, field: CopyField, locale: String? = null): String {
        if (config == null) return ""
        val matched = match(locale, config.translations.keys)
        val translated = if (matched.isNotEmpty()) config.translations[matched]?.get(field.key).orEmpty() else ""
        return translated.ifEmpty {
            when (field) {
                CopyField.TITLE -> config.title
                CopyField.GREETING -> config.greeting
                CopyField.OFFLINE_MESSAGE -> config.offlineMessage
            }
        }
    }

    /** The available tag `locale` asks for: exact first, then `pt-BR` → `pt`. */
    fun match(locale: String?, available: Collection<String>): String {
        val tag = locale.orEmpty().trim().replace('_', '-').lowercase()
        if (tag.isEmpty()) return ""
        if (tag in available) return tag
        val primary = tag.substringBefore('-')
        return if (primary in available) primary else ""
    }
}
