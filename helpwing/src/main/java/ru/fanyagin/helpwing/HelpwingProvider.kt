package ru.fanyagin.helpwing

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.compositionLocalOf
import ru.fanyagin.helpwing.core.ChatMessage
import ru.fanyagin.helpwing.core.ChatState
import ru.fanyagin.helpwing.core.ChatStatus
import ru.fanyagin.helpwing.core.Copy
import ru.fanyagin.helpwing.core.CopyField
import ru.fanyagin.helpwing.core.HelpwingChat
import ru.fanyagin.helpwing.core.Palette
import ru.fanyagin.helpwing.core.Typing
import ru.fanyagin.helpwing.core.WidgetConfig

/** The project's header, greeting, offline message and typing text, resolved against the locale. */
data class SupportCopy(val title: String, val greeting: String, val offlineMessage: String, val typingText: String = "")

/** Everything the chat can do, as seen from a composable. */
class Support internal constructor(
    /** The client itself, for anything these components do not draw. */
    val chat: HelpwingChat,
    val state: ChatState,
    val theme: SupportTheme,
    val copy: SupportCopy,
    val labels: SupportLabels,
    /** Whether [SupportSheet] is showing. */
    val isOpen: Boolean,
    private val setOpen: (Boolean) -> Unit,
) {
    val config: WidgetConfig? get() = state.config
    val messages: List<ChatMessage> get() = state.messages
    val unreadCount: Int get() = state.unreadCount
    val typing: Typing? get() = state.typing
    val offline: Boolean get() = state.offline

    /** The project has a widget and it is turned on. */
    val isReady: Boolean get() = state.status == ChatStatus.READY

    fun open() {
        setOpen(true)
        chat.setPresent(true)
    }

    fun close() {
        setOpen(false)
        chat.setPresent(false)
    }

    // These run on the chat's own scope, so leaving the screen never cancels a send halfway.

    /** Send a message. `email` is only read when the conversation is being opened. */
    fun send(text: String, email: String = "") {
        chat.launch { chat.send(text, email) }
    }

    fun retry(clientMessageId: String) {
        chat.launch { chat.retry(clientMessageId) }
    }

    fun refresh() {
        chat.launch { chat.refresh() }
    }
}

internal val LocalSupport = compositionLocalOf<Support?> { null }

/**
 * Makes the chat available to [SupportChat], [SupportLauncher] and [SupportSheet] below it.
 * Defaults come from [Helpwing.initialize]; pass `chat` to use a client of your own.
 */
@Composable
fun HelpwingProvider(
    chat: HelpwingChat = Helpwing.chat,
    dark: Boolean? = Helpwing.settings?.dark,
    theme: SupportThemeOverride? = Helpwing.settings?.theme,
    labels: SupportLabels = Helpwing.settings?.labels ?: SupportLabels(),
    locale: String = Helpwing.settings?.locale.orEmpty(),
    content: @Composable () -> Unit,
) {
    val state by chat.state.collectAsState()
    var isOpen by remember(chat) { mutableStateOf(false) }

    // Closing the provider while the sheet is up must not leave the visitor marked as reading.
    DisposableEffect(chat) {
        onDispose { if (isOpen) chat.setPresent(false) }
    }

    val systemDark = isSystemInDarkTheme()
    val scheme = state.config?.colorScheme
    val isDark = dark ?: (scheme == "dark" || (scheme != "light" && systemDark))
    val accent = state.config?.accentColor
    val resolved = remember(accent, isDark, theme) { SupportTheme.from(Palette.forAccent(accent, isDark), theme) }
    val config = state.config
    val copy = remember(config, locale) {
        SupportCopy(
            title = Copy.forLocale(config, CopyField.TITLE, locale),
            greeting = Copy.forLocale(config, CopyField.GREETING, locale),
            offlineMessage = Copy.forLocale(config, CopyField.OFFLINE_MESSAGE, locale),
            typingText = Copy.forLocale(config, CopyField.TYPING_TEXT, locale),
        )
    }

    val support = Support(chat, state, resolved, copy, labels, isOpen) { isOpen = it }
    CompositionLocalProvider(LocalSupport provides support) {
        content()
    }
}

/** The chat from the nearest [HelpwingProvider]. */
@Composable
fun rememberSupport(): Support =
    LocalSupport.current ?: error("rememberSupport() was called outside a HelpwingProvider.")

/** Marks the visitor as reading while this is in composition. [SupportChat] already does it. */
@Composable
fun PresenceEffect() {
    val chat = rememberSupport().chat
    DisposableEffect(chat) {
        chat.setPresent(true)
        onDispose { chat.setPresent(false) }
    }
}
