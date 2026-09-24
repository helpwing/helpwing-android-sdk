package ru.fanyagin.helpwing.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.CopyOnWriteArraySet

/**
 * The conversation, with no opinion about how it is drawn.
 * Polls rather than holding a request open; retries sends but never the first message.
 * All state is touched on one single-lane dispatcher, so nothing here needs a lock.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HelpwingChat(private val options: ChatOptions) {
    private val transport = Transport(options.apiUrl, options.projectKey, options.httpClient)
    private val store = SessionStore(options.storage, options.projectKey)
    private val dispatcher = options.dispatcher ?: Dispatchers.Default.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val mutableState = MutableStateFlow(ChatState())
    private val listeners = CopyOnWriteArraySet<(ChatState) -> Unit>()

    private var session: StoredSession? = null
    private var claimed: Identity? = null
    private var present = false
    private var active = true
    private var timer: Job? = null
    private var inFlight: Job? = null
    private var flushing = false

    @Volatile
    private var destroyed = false

    /** Everything true about the conversation, as it changes. */
    val state: StateFlow<ChatState> = mutableState.asStateFlow()

    /** The state right now. */
    val current: ChatState get() = mutableState.value

    /** Called immediately, then on every change. Returns the unsubscribe. */
    fun subscribe(listener: (ChatState) -> Unit): () -> Unit {
        listeners.add(listener)
        listener(current)
        return { listeners.remove(listener) }
    }

    /** Load the config and any stored conversation. Never throws; failures land in `state.error`. */
    suspend fun start(): Unit = withContext(dispatcher) {
        patch { copy(status = if (config != null) status else ChatStatus.LOADING, error = null) }
        val loaded = try {
            transport.config()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            fail(error)
            return@withContext
        }
        patch { copy(config = loaded, offline = false) }
        if (!loaded.isEnabled || (!loaded.isOnline && loaded.hideWhenClosed)) {
            patch { copy(status = ChatStatus.UNCONFIGURED) }
            return@withContext
        }

        session = store.read()
        if (session != null) restore()
        patch { copy(status = ChatStatus.READY) }
        schedule()
    }

    /**
     * Say who the visitor is, now or for the conversation that has not started yet.
     * Naming somebody else drops the stored conversation: it stays with whoever opened it.
     */
    suspend fun identify(identity: Identity?): Unit = withContext(dispatcher) {
        val key = identityKey(identity)
        val stored = session
        if (key.isNotEmpty() && stored != null && stored.identityKey.isNotEmpty() && key != stored.identityKey) {
            dropConversation()
        }
        claimed = identity
        val held = session ?: return@withContext
        if (identity == null) return@withContext
        try {
            transport.identify(held.ticketId, held.token, identity)
            held.identityKey = key
            persist()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (error is HelpwingException && error.status == 409) {
                dropConversation()
                return@withContext
            }
            // Never shown: attaching a name is not worth an error on screen.
            note(error)
        }
    }

    /** Send a message, drawn before it has gone anywhere. `email` is only read when opening. */
    suspend fun send(text: String, email: String = ""): Unit = withContext(dispatcher) {
        val body = text.trim()
        if (body.isEmpty()) return@withContext

        val pending = PendingMessage(options.uuid(), body, options.now())
        patch { copy(messages = messages + draw(pending), error = null) }

        val held = session
        if (held == null) {
            openConversation(pending, email.trim())
            return@withContext
        }
        held.pending = held.pending + pending
        persist()
        flush()
    }

    /** Try a failed message again. Free on an open conversation; a new start otherwise. */
    suspend fun retry(clientMessageId: String): Unit = withContext(dispatcher) {
        val failed = current.messages.firstOrNull {
            it.clientMessageId == clientMessageId && it.delivery == Delivery.FAILED
        } ?: return@withContext

        val pending = PendingMessage(clientMessageId, failed.text, failed.createdAt)
        replace(clientMessageId, failed.copy(delivery = Delivery.PENDING))

        val held = session
        if (held == null) {
            openConversation(pending, "")
            return@withContext
        }
        if (held.pending.none { it.clientMessageId == clientMessageId }) {
            held.pending = held.pending + pending
            persist()
        }
        flush()
    }

    /** Whether the visitor is looking at the conversation. Drives poll rate and `present=1`. */
    fun setPresent(value: Boolean) {
        scope.launch {
            if (present == value) return@launch
            present = value
            if (value) {
                markReadNow()
                tick()
            } else {
                schedule()
            }
        }
    }

    /** Whether the app is in the foreground. Polling stops entirely when it is not. */
    fun setActive(value: Boolean) {
        scope.launch {
            if (active == value) return@launch
            active = value
            if (value) tick() else clearTimer()
        }
    }

    /** Clear the unread count. */
    fun markRead() {
        scope.launch { markReadNow() }
    }

    /** Ask now rather than at the next tick; returns after a round that started after this call. */
    suspend fun refresh(): Unit = withContext(dispatcher) {
        inFlight?.join()
        tick().join()
    }

    /** Forget the visitor and the conversation on this device. For sign-out. */
    suspend fun reset(): Unit = withContext(dispatcher) {
        claimed = null
        dropConversation()
    }

    /** Run `block` on this client's own scope, which outlives any screen that asked. */
    fun launch(block: suspend HelpwingChat.() -> Unit): Job = scope.launch { block(this@HelpwingChat) }

    /** Stop everything. The object is not usable afterwards. */
    fun destroy() {
        destroyed = true
        listeners.clear()
        scope.cancel()
    }

    // -- the conversation ------------------------------------------------------------

    private suspend fun dropConversation() {
        clearTimer()
        session = null
        store.clear()
        patch { copy(conversation = null, messages = emptyList(), typing = null, unreadCount = 0, error = null) }
        schedule()
    }

    private suspend fun restore() {
        val held = session ?: return
        try {
            val conversation = transport.conversation(held.ticketId, held.token)
            held.cursor = conversation.cursor
            persist()
            adopt(conversation, replaceTranscript = true)
            flush()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            handle(error)
        }
    }

    private suspend fun openConversation(pending: PendingMessage, email: String) {
        try {
            val conversation = transport.start(
                bodyText = pending.text,
                identity = claimed,
                email = email,
                locale = options.locale,
                timezone = options.timezone,
            )
            session = StoredSession(
                token = conversation.visitorToken,
                ticketId = conversation.ticketId,
                identityKey = identityKey(claimed),
                cursor = conversation.cursor,
                // Read up to here: the only message on it is the one just written.
                lastReadCursor = conversation.cursor,
            )
            persist()
            // The start endpoint takes no client id, so the early copy is dropped by hand.
            forget(pending.clientMessageId)
            adopt(conversation, replaceTranscript = true)
            patch { copy(offline = false) }
            schedule()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            replace(pending.clientMessageId, draw(pending).copy(delivery = Delivery.FAILED))
            fail(error, keepStatus = true)
        }
    }

    /** Send what is queued, oldest first, stopping at the first that cannot go. */
    private suspend fun flush() {
        val held = session ?: return
        if (flushing) return
        flushing = true
        try {
            while (held.pending.isNotEmpty()) {
                val next = held.pending.first()
                try {
                    val conversation = transport.send(held.ticketId, held.token, next.text, next.clientMessageId)
                    held.pending = held.pending.filter { it.clientMessageId != next.clientMessageId }
                    persist()
                    forget(next.clientMessageId)
                    adopt(conversation)
                    patch { copy(offline = false) }
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    if (handleSendFailure(error, next)) break
                }
            }
        } finally {
            flushing = false
        }
    }

    /** Network errors keep the message queued; a refusal marks it failed. Returns whether to stop. */
    private suspend fun handleSendFailure(error: Exception, message: PendingMessage): Boolean {
        if (error is HelpwingException && error.isGone) {
            handle(error)
            return true
        }
        if (error is HelpwingException && error.isNetwork) {
            patch { copy(offline = true) }
            return true
        }
        session?.let { held ->
            held.pending = held.pending.filter { it.clientMessageId != message.clientMessageId }
            persist()
        }
        replace(message.clientMessageId, draw(message).copy(delivery = Delivery.FAILED))
        fail(error, keepStatus = true)
        return false
    }

    // -- polling ---------------------------------------------------------------------

    private fun schedule() {
        clearTimer()
        if (destroyed || session == null || !active) return
        val interval = if (present) options.pollIntervalMs else options.backgroundPollIntervalMs
        if (interval <= 0) return
        timer = scope.launch {
            delay(interval)
            tick()
        }
    }

    /** One round: flush, then poll. Never two at once. */
    private fun tick(): Job {
        inFlight?.let { return it }
        if (destroyed || session == null || !active) return Job().apply { complete() }
        // Lazy, so `inFlight` is set before a dispatcher that runs eagerly can finish the round.
        val round = scope.launch(start = CoroutineStart.LAZY) {
            try {
                flush()
                poll()
            } finally {
                inFlight = null
                schedule()
            }
        }
        inFlight = round
        round.start()
        return round
    }

    private suspend fun poll() {
        val held = session ?: return
        try {
            val updates = transport.updates(held.ticketId, held.token, held.cursor, present)
            held.cursor = updates.cursor
            persist()
            merge(updates.messages, updates.typing, updates.status)
            if (current.offline) patch { copy(offline = false) }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            handle(error)
        }
    }

    // -- state -----------------------------------------------------------------------

    private suspend fun adopt(conversation: ApiConversation, replaceTranscript: Boolean = false) {
        patch {
            copy(
                conversation = Conversation(
                    ticketId = conversation.ticketId,
                    ticketReference = conversation.ticketReference,
                    status = conversation.status,
                    subject = conversation.subject,
                ),
            )
        }
        if (replaceTranscript) {
            // Keep only what is still trying to be sent; the server cannot return it.
            patch { copy(messages = messages.filter { it.delivery != Delivery.SENT }) }
        }
        merge(conversation.messages, conversation.typing, conversation.status)
    }

    /** Fold server messages into the transcript, replacing any copy drawn early. */
    private suspend fun merge(incoming: List<ApiMessage>, typing: Typing?, status: String) {
        val byId = LinkedHashMap<String, ChatMessage>()
        for (message in current.messages) byId[message.id] = message
        val drawnEarly = HashSet<String>()
        for (message in incoming) {
            if (message.clientMessageId.isNotEmpty()) drawnEarly.add(message.clientMessageId)
            byId[message.id] = received(message)
        }

        val messages = byId.values
            .filterNot { it.clientMessageId != null && it.clientMessageId in drawnEarly && it.delivery != Delivery.SENT }
            .sortedBy { it.createdAt }

        patch {
            copy(
                messages = messages,
                typing = typing,
                conversation = if (status.isNotEmpty()) conversation?.copy(status = status) else conversation,
            )
        }
        recount()
    }

    private fun forget(clientMessageId: String) {
        patch {
            copy(messages = messages.filter { it.clientMessageId != clientMessageId || it.delivery == Delivery.SENT })
        }
    }

    private fun replace(clientMessageId: String, message: ChatMessage) {
        patch {
            copy(
                messages = messages.map {
                    if (it.clientMessageId == clientMessageId && it.delivery != Delivery.SENT) message else it
                },
            )
        }
    }

    /** Agent messages newer than the last-read cursor; zero while the visitor is looking. */
    private suspend fun recount() {
        if (present) {
            markReadNow()
            return
        }
        val since = session?.lastReadCursor ?: ""
        val unread = current.messages.count {
            it.author == Author.AGENT && it.delivery == Delivery.SENT && it.createdAt > since
        }
        if (unread != current.unreadCount) patch { copy(unreadCount = unread) }
    }

    private suspend fun markReadNow() {
        session?.let {
            it.lastReadCursor = it.cursor
            persist()
        }
        if (current.unreadCount != 0) patch { copy(unreadCount = 0) }
    }

    private fun patch(change: ChatState.() -> ChatState) {
        val next = mutableState.value.change()
        mutableState.value = next
        for (listener in listeners) listener(next)
    }

    private suspend fun persist() {
        session?.let { store.write(it) }
    }

    // -- failure ---------------------------------------------------------------------

    /** A 404 means the token names nothing any more: drop the session for a fresh chat. */
    private suspend fun handle(error: Exception) {
        if (error is HelpwingException && error.isGone) {
            session = null
            store.clear()
            clearTimer()
            patch { copy(conversation = null, messages = emptyList(), typing = null, unreadCount = 0) }
            return
        }
        note(error)
    }

    private fun note(error: Exception) {
        if (error is HelpwingException && error.isNetwork) {
            if (!current.offline) patch { copy(offline = true) }
            return
        }
        patch { copy(error = error.message ?: "Something went wrong.") }
    }

    private fun fail(error: Exception, keepStatus: Boolean = false) {
        note(error)
        if (!keepStatus && current.status != ChatStatus.READY) patch { copy(status = ChatStatus.ERROR) }
    }

    private fun clearTimer() {
        timer?.cancel()
        timer = null
    }

    companion object {
        /** Who an identity names, as one comparable string. */
        internal fun identityKey(identity: Identity?): String {
            val id = identity?.id?.trim().orEmpty()
            if (id.isNotEmpty()) return "id:$id"
            val email = identity?.email?.trim()?.lowercase().orEmpty()
            return if (email.isNotEmpty()) "email:$email" else ""
        }

        private fun draw(pending: PendingMessage) = ChatMessage(
            id = pending.clientMessageId,
            author = Author.CUSTOMER,
            authorName = "",
            authorAvatarUrl = "",
            text = pending.text,
            markdown = true,
            attachments = emptyList(),
            createdAt = pending.createdAt,
            delivery = Delivery.PENDING,
            clientMessageId = pending.clientMessageId,
        )

        private fun received(message: ApiMessage) = ChatMessage(
            id = message.id,
            author = when (message.author) {
                "agent" -> Author.AGENT
                "customer" -> Author.CUSTOMER
                else -> Author.SYSTEM
            },
            authorName = message.authorName,
            authorAvatarUrl = message.authorAvatarUrl,
            text = message.bodyText.ifEmpty { HtmlText.of(message.bodyHtml) },
            markdown = message.bodyText.isNotEmpty(),
            attachments = message.attachments,
            createdAt = message.createdAt,
            delivery = Delivery.SENT,
            clientMessageId = message.clientMessageId.ifEmpty { null },
        )
    }
}

/** A crude reading of HTML for legacy messages that have no `body_text`. */
object HtmlText {
    private val BREAK = Regex("<\\s*br\\s*/?\\s*>", RegexOption.IGNORE_CASE)
    private val BLOCK_END = Regex("<\\s*/\\s*(p|div|li|tr|h[1-6])\\s*>", RegexOption.IGNORE_CASE)
    private val TAG = Regex("<[^>]*>")
    private val BLANK_LINES = Regex("\\n{3,}")

    fun of(html: String): String {
        if (html.isEmpty()) return ""
        return html
            .replace(BREAK, "\n")
            .replace(BLOCK_END, "\n")
            .replace(TAG, "")
            .replace("&nbsp;", " ", ignoreCase = true)
            .replace("&lt;", "<", ignoreCase = true)
            .replace("&gt;", ">", ignoreCase = true)
            .replace("&quot;", "\"", ignoreCase = true)
            .replace("&#39;", "'")
            .replace("&amp;", "&", ignoreCase = true)
            .replace(BLANK_LINES, "\n\n")
            .trim()
    }
}
