package ru.fanyagin.helpwing.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

// Wire shapes as the public widget API returns them.
// Every field has a default, so an older or newer server never fails a decode.

/** `GET /widget/{public_key}/config/` */
@Serializable
data class WidgetConfig(
    @SerialName("is_enabled") val isEnabled: Boolean = false,
    @SerialName("project_name") val projectName: String = "",
    @SerialName("accent_color") val accentColor: String = "",
    /** `system`, `light` or `dark`; resolved on the device. */
    @SerialName("color_scheme") val colorScheme: String = "system",
    @SerialName("launcher_icon") val launcherIcon: String = "",
    @SerialName("launcher_icon_url") val launcherIconUrl: String = "",
    @SerialName("launcher_position") val launcherPosition: String = "bottom_right",
    @SerialName("logo_url") val logoUrl: String = "",
    @SerialName("show_branding") val showBranding: Boolean = false,
    /** Blank when the project wrote no heading of its own. */
    val title: String = "",
    val greeting: String = "",
    @SerialName("offline_message") val offlineMessage: String = "",
    /** Shown while an agent is typing; may contain `{name}`. */
    @SerialName("typing_text") val typingText: String = "",
    /** Title, greeting, offline message and typing text per language code. See [Copy.forLocale]. */
    val translations: Map<String, Map<String, String>> = emptyMap(),
    @SerialName("default_locale") val defaultLocale: String = "",
    @SerialName("require_email") val requireEmail: Boolean = false,
    @SerialName("show_agent_availability") val showAgentAvailability: Boolean = false,
    @SerialName("show_on_desktop") val showOnDesktop: Boolean = true,
    @SerialName("show_on_mobile") val showOnMobile: Boolean = true,
    @SerialName("identity_verification_enabled") val identityVerificationEnabled: Boolean = false,
    @SerialName("is_online") val isOnline: Boolean = true,
    /** Report no support surface at all while outside operating hours. */
    @SerialName("hide_when_closed") val hideWhenClosed: Boolean = false,
)

@Serializable
data class ApiAttachment(
    val id: String = "",
    val filename: String = "",
    @SerialName("content_type") val contentType: String = "",
    @SerialName("size_bytes") val sizeBytes: Long = 0,
    val url: String = "",
    /** Part of the body (`![alt](cid:...)`), not a file sent alongside. */
    @SerialName("is_inline") val isInline: Boolean = false,
    @SerialName("content_id") val contentId: String = "",
)

@Serializable
data class ApiMessage(
    val id: String = "",
    /** `agent`, `customer` or `system`. */
    val author: String = "system",
    @SerialName("author_name") val authorName: String = "",
    @SerialName("author_avatar_url") val authorAvatarUrl: String = "",
    @SerialName("body_text") val bodyText: String = "",
    @SerialName("body_html") val bodyHtml: String = "",
    val attachments: List<ApiAttachment> = emptyList(),
    @SerialName("created_at") val createdAt: String = "",
    /** The id this client gave the message, echoed back; empty on everything else. */
    @SerialName("client_message_id") val clientMessageId: String = "",
)

@Serializable
data class Typing(val name: String = "")

@Serializable
data class ApiConversation(
    @SerialName("ticket_id") val ticketId: String = "",
    @SerialName("ticket_reference") val ticketReference: String = "",
    val status: String = "",
    val subject: String = "",
    @SerialName("visitor_token") val visitorToken: String = "",
    val messages: List<ApiMessage> = emptyList(),
    val typing: Typing? = null,
    val cursor: String = "",
)

@Serializable
data class ApiUpdates(
    val cursor: String = "",
    @SerialName("has_changes") val hasChanges: Boolean = false,
    val status: String = "",
    val messages: List<ApiMessage> = emptyList(),
    val typing: Typing? = null,
)

/** Who the visitor is. Only the fields that are set are sent. */
data class Identity(
    /** Your own user id. At most 120 characters. */
    val id: String? = null,
    val email: String? = null,
    /** At most 150 characters. */
    val name: String? = null,
    /** String, number or boolean values. */
    val metadata: Map<String, Any>? = null,
    /** Hex HMAC-SHA256 of [id] with the identity secret, computed on your server. Never ship the secret. */
    val userHash: String? = null,
)

enum class Author { AGENT, CUSTOMER, SYSTEM }

enum class Delivery { PENDING, SENT, FAILED }

enum class ChatStatus {
    IDLE,
    LOADING,
    READY,

    /** No support surface right now: widget off, or closed and hidden outside hours. */
    UNCONFIGURED,
    ERROR,
}

/** A message as the app shows it, including ones not yet acknowledged by the server. */
data class ChatMessage(
    /** The server's id once there is one, the client id until then. */
    val id: String,
    val author: Author,
    val authorName: String,
    val authorAvatarUrl: String,
    val text: String,
    /** Whether [text] is markdown; false only for legacy HTML-only messages. */
    val markdown: Boolean,
    val attachments: List<ApiAttachment>,
    val createdAt: String,
    val delivery: Delivery,
    /** The id this client made up, so a retry is recognised as the same message. */
    val clientMessageId: String? = null,
)

data class Conversation(
    val ticketId: String,
    val ticketReference: String,
    val status: String,
    val subject: String,
)

data class ChatState(
    val status: ChatStatus = ChatStatus.IDLE,
    val config: WidgetConfig? = null,
    val conversation: Conversation? = null,
    val messages: List<ChatMessage> = emptyList(),
    /** The agent writing a reply right now. */
    val typing: Typing? = null,
    /** Agent messages since the visitor last had the conversation open. */
    val unreadCount: Int = 0,
    /** True between a failed request and the next one that works. */
    val offline: Boolean = false,
    /** The last thing that went wrong, in words a person could be shown. */
    val error: String? = null,
)

data class ChatOptions(
    /** e.g. `https://api.helpwing.app` — the host that serves `/widget.js`. */
    val apiUrl: String,
    /** The project's public key, `pk_...`. Safe to ship in an app. */
    val projectKey: String,
    /** Where the session is kept between launches. The default forgets it on process death. */
    val storage: HelpwingStorage = MemoryStorage(),
    /** Poll interval while the visitor is reading. */
    val pollIntervalMs: Long = 5_000,
    /** Poll interval while the chat is closed but the app is open. Zero stops it. */
    val backgroundPollIntervalMs: Long = 30_000,
    /** Sent when a conversation is opened. */
    val locale: String = "",
    val timezone: String = "",
    val httpClient: HttpClient = UrlConnectionHttpClient(),
    /** Must run one task at a time. Defaults to a single-lane view of `Dispatchers.Default`. */
    val dispatcher: CoroutineDispatcher? = null,
    val uuid: () -> String = { UUID.randomUUID().toString() },
    /** ISO-8601 UTC timestamp for messages drawn before the server has them. */
    val now: () -> String = ::isoNow,
)

internal fun isoNow(): String {
    val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
    format.timeZone = TimeZone.getTimeZone("UTC")
    return format.format(Date())
}
