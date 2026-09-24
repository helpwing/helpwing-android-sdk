package ru.fanyagin.helpwing.core

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Where the session survives the app being closed. Anything key/value will do. */
interface HelpwingStorage {
    suspend fun getItem(key: String): String?
    suspend fun setItem(key: String, value: String)
    suspend fun removeItem(key: String)
}

/** Forgets everything when the process dies. For tests and previews. */
class MemoryStorage : HelpwingStorage {
    private val values = java.util.concurrent.ConcurrentHashMap<String, String>()

    override suspend fun getItem(key: String): String? = values[key]

    override suspend fun setItem(key: String, value: String) {
        values[key] = value
    }

    override suspend fun removeItem(key: String) {
        values.remove(key)
    }
}

@Serializable
data class PendingMessage(
    val clientMessageId: String,
    val text: String,
    val createdAt: String = "",
)

/** The stored record, in the same JSON shape every Helpwing SDK writes. */
@Serializable
data class StoredSession(
    /** The only credential this client has, good for one conversation. */
    val token: String,
    val ticketId: String,
    /** Who opened the conversation, as `identify()` named them. Empty when anonymous. */
    var identityKey: String = "",
    /** Where the update feed had got to. */
    var cursor: String = "",
    /** The cursor when the visitor last looked; newer agent messages are unread. */
    var lastReadCursor: String = "",
    /** Sent but unacknowledged messages on an existing conversation, oldest first. */
    var pending: List<PendingMessage> = emptyList(),
)

/** One record per project key, under `helpwing:{projectKey}`. */
class SessionStore(private val storage: HelpwingStorage, projectKey: String) {
    private val key = "helpwing:$projectKey"

    /** Unreadable records read as nothing stored: losing a chat beats refusing to start. */
    suspend fun read(): StoredSession? {
        val raw = try {
            storage.getItem(key)
        } catch (error: CancellationException) {
            throw error
        } catch (ignored: Exception) {
            return null
        }
        if (raw.isNullOrEmpty()) return null
        return try {
            val record = HelpwingJson.parseToJsonElement(raw) as? JsonObject ?: return null
            val token = record.string("token") ?: return null
            val ticketId = record.string("ticketId") ?: return null
            StoredSession(
                token = token,
                ticketId = ticketId,
                identityKey = record.string("identityKey") ?: "",
                cursor = record.string("cursor") ?: "",
                lastReadCursor = record.string("lastReadCursor") ?: "",
                pending = (record["pending"] as? JsonArray).orEmpty().mapNotNull { item ->
                    val entry = item as? JsonObject ?: return@mapNotNull null
                    val id = entry.string("clientMessageId") ?: return@mapNotNull null
                    val text = entry.string("text") ?: return@mapNotNull null
                    PendingMessage(id, text, entry.string("createdAt") ?: "")
                },
            )
        } catch (ignored: Exception) {
            null
        }
    }

    suspend fun write(session: StoredSession) {
        try {
            storage.setItem(key, HelpwingJson.encodeToString(StoredSession.serializer(), session))
        } catch (error: CancellationException) {
            throw error
        } catch (ignored: Exception) {
            // A full disk: the chat still works for this run.
        }
    }

    suspend fun clear() {
        try {
            storage.removeItem(key)
        } catch (error: CancellationException) {
            throw error
        } catch (ignored: Exception) {
            // Nothing here is worth failing a conversation over.
        }
    }

    private fun JsonObject.string(name: String): String? {
        val value = this[name] as? JsonPrimitive ?: return null
        return if (value.isString) value.contentOrNull else null
    }
}
