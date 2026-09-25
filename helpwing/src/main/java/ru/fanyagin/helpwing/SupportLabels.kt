package ru.fanyagin.helpwing

/** Every string the chat's own chrome draws. No translations ship; pass your own. */
data class SupportLabels(
    val placeholder: String = "Write a message…",
    val emailPlaceholder: String = "Your email address",
    val send: String = "Send",
    val sending: String = "Sending…",
    val failed: String = "Not sent.",
    val retry: String = "Tap to try again",
    val offline: String = "No connection. Your messages will be sent when it comes back.",
    val online: String = "We are online",
    val away: String = "We are away right now",
    /** Used only when the project has not written its own typing text. */
    val typing: (String) -> String = { name -> "$name is typing…" },
    val branding: String = "Powered by Helpwing",
    val loading: String = "Loading…",
    val unavailable: String = "Support chat is not available right now.",
    val close: String = "Close",
    val launcher: String = "Chat",
)
