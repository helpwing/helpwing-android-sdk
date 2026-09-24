package ru.fanyagin.helpwing.core

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HelpwingChatTest {
    private lateinit var server: FakeServer
    private lateinit var storage: MemoryStorage
    private val clients = mutableListOf<HelpwingChat>()
    private var ids = 0

    @Before
    fun setUp() {
        server = FakeServer()
        storage = MemoryStorage()
    }

    /** Intervals long enough that no timer fires; every poll here is asked for. */
    private fun TestScope.chat(): HelpwingChat {
        return HelpwingChat(
            ChatOptions(
                apiUrl = "https://api.helpwing.test",
                projectKey = PROJECT,
                storage = storage,
                pollIntervalMs = 60_000,
                backgroundPollIntervalMs = 60_000,
                httpClient = server,
                dispatcher = UnconfinedTestDispatcher(testScheduler),
                uuid = { "client-${++ids}" },
                // Later than anything the fake server stamps, as a real clock would be.
                now = { "2026-08-10T00:00:00.000Z" },
            ),
        ).also { clients.add(it) }
    }

    /** Stops every client so runTest has no timers left to chase. */
    private fun test(body: suspend TestScope.() -> Unit) = runTest {
        try {
            body()
        } finally {
            clients.forEach { it.destroy() }
            clients.clear()
        }
    }

    private suspend fun stored(): String? = storage.getItem("helpwing:$PROJECT")

    private fun FakeServer.Call.field(name: String): String? =
        (body?.get(name) as? JsonPrimitive)?.contentOrNull

    // -- starting up

    @Test
    fun readyOnceConfigIsLoaded() = test {
        val client = chat()
        client.start()
        assertEquals(ChatStatus.READY, client.current.status)
        assertEquals("Acme Cloud", client.current.config?.projectName)
        assertNull(client.current.conversation)
    }

    @Test
    fun disabledWidgetIsUnconfiguredNotAnError() = test {
        server.config = server.config.copy(isEnabled = false)
        val client = chat()
        client.start()
        assertEquals(ChatStatus.UNCONFIGURED, client.current.status)
        assertNull(client.current.error)
    }

    @Test
    fun hiddenOutsideHoursIsUnconfigured() = test {
        server.config = server.config.copy(isOnline = false, hideWhenClosed = true)
        val client = chat()
        client.start()
        assertEquals(ChatStatus.UNCONFIGURED, client.current.status)
    }

    @Test
    fun closedButNotHiddenStaysUp() = test {
        server.config = server.config.copy(isOnline = false, hideWhenClosed = false)
        val client = chat()
        client.start()
        assertEquals(ChatStatus.READY, client.current.status)
    }

    @Test
    fun unreachableIsReportedWithoutThrowing() = test {
        server.failAll = "network"
        val client = chat()
        client.start()
        assertTrue(client.current.offline)
        assertEquals(ChatStatus.ERROR, client.current.status)
    }

    // -- the first message

    @Test
    fun firstMessageOpensConversationAndKeepsToken() = test {
        val client = chat()
        client.start()
        client.send("The widget throws a CSP error.")

        assertEquals("ticket-1", client.current.conversation?.ticketId)
        assertEquals(listOf("The widget throws a CSP error."), client.current.messages.map { it.text })
        assertEquals(Delivery.SENT, client.current.messages[0].delivery)
        val record = HelpwingJson.parseToJsonElement(stored()!!) as JsonObject
        assertEquals("visitor-token-1", (record["token"] as JsonPrimitive).content)
        assertEquals("ticket-1", (record["ticketId"] as JsonPrimitive).content)
    }

    @Test
    fun firstMessageCarriesNoIdempotencyKey() = test {
        val client = chat()
        client.start()
        client.send("Hello")
        val start = server.calls.first { it.method == "POST" && it.path.endsWith("/conversations/") }
        assertNull(start.body?.get("client_message_id"))
    }

    @Test
    fun failedStartIsShownNotRetried() = test {
        val client = chat()
        client.start()
        server.failNext = "network"
        client.send("Hello")

        assertEquals(1, client.current.messages.size)
        assertEquals(Delivery.FAILED, client.current.messages[0].delivery)
        assertNull(client.current.conversation)
        assertTrue(client.current.offline)
        assertNull(stored())
    }

    @Test
    fun failedStartGoesOutOnRetry() = test {
        val client = chat()
        client.start()
        server.failNext = "network"
        client.send("Hello")

        client.retry(client.current.messages[0].clientMessageId!!)
        assertEquals("ticket-1", client.current.conversation?.ticketId)
        assertEquals(listOf(Delivery.SENT), client.current.messages.map { it.delivery })
    }

    @Test
    fun passesTheEmailTheProjectAskedFor() = test {
        server.config = server.config.copy(requireEmail = true)
        val client = chat()
        client.start()
        client.send("Hello", email = "kim@shop.example.com")
        val start = server.calls.first { it.method == "POST" && it.path.endsWith("/conversations/") }
        assertEquals("kim@shop.example.com", start.field("email"))
    }

    // -- sending on a conversation that exists

    @Test
    fun resendCarriesSameIdAndIsStoredOnce() = test {
        val client = chat()
        client.start()
        client.send("First")
        server.failNext = "network"
        client.send("Second")
        assertEquals(listOf(Delivery.SENT, Delivery.PENDING), client.current.messages.map { it.delivery })

        client.refresh()

        val sends = server.calls.filter { it.path.endsWith("/messages/") }
        assertEquals(2, sends.size)
        assertEquals(sends[0].field("client_message_id"), sends[1].field("client_message_id"))
        assertEquals(1, server.messages.count { it.bodyText == "Second" })
        assertEquals(listOf(Delivery.SENT, Delivery.SENT), client.current.messages.map { it.delivery })
    }

    @Test
    fun unsentMessageSurvivesRelaunch() = test {
        val first = chat()
        first.start()
        first.send("First")
        server.failNext = "network"
        first.send("Second")
        first.destroy()

        val second = chat()
        second.start()
        assertEquals(1, server.messages.count { it.bodyText == "Second" })
        assertEquals(listOf("First", "Second"), second.current.messages.map { it.text })
    }

    @Test
    fun stopsAtFirstUnsendableMessageSoOrderIsKept() = test {
        val client = chat()
        client.start()
        client.send("First")
        server.failAll = "network"
        client.send("Second")
        client.send("Third")
        server.failAll = null
        client.refresh()
        assertEquals(listOf("First", "Second", "Third"), server.messages.map { it.bodyText })
    }

    @Test
    fun refusedMessageIsMarkedFailedAndDropped() = test {
        val client = chat()
        client.start()
        client.send("First")
        server.failNext = 400
        client.send("Second")

        assertEquals(listOf(Delivery.SENT, Delivery.FAILED), client.current.messages.map { it.delivery })
        assertEquals("Refused by the fake server.", client.current.error)
        client.refresh()
        assertEquals(1, server.calls.count { it.path.endsWith("/messages/") })
    }

    // -- following the conversation

    @Test
    fun agentReplyCountsUnreadUntilLookedAt() = test {
        val client = chat()
        client.start()
        client.send("The widget throws a CSP error.")
        server.reply("Add the CSP header.")
        client.refresh()

        assertEquals(listOf("The widget throws a CSP error.", "Add the CSP header."), client.current.messages.map { it.text })
        assertEquals(1, client.current.unreadCount)
        client.setPresent(true)
        assertEquals(0, client.current.unreadCount)
    }

    @Test
    fun htmlOnlyReplyIsNotMarkdown() = test {
        val client = chat()
        client.start()
        client.send("The widget throws a CSP error.")
        server.reply("Add the **CSP** header.")
        server.push(server.message(author = "agent", authorName = "Ada", bodyHtml = "<p>Sent from **my phone**</p>"))
        client.refresh()

        assertEquals(listOf(true, true, false), client.current.messages.map { it.markdown })
        assertEquals("Sent from **my phone**", client.current.messages[2].text)
    }

    @Test
    fun countsWhatArrivedWhileClosed() = test {
        val first = chat()
        first.start()
        first.send("The widget throws a CSP error.")
        first.destroy()
        server.reply("Add the CSP header.")
        server.reply("Did that work?")

        val second = chat()
        second.start()
        assertEquals(2, second.current.unreadCount)
        assertEquals(3, second.current.messages.size)
    }

    @Test
    fun presentOnlyWhileReading() = test {
        val client = chat()
        client.start()
        client.send("Hello")

        client.refresh()
        assertFalse(server.calls.last().path.contains("present=1"))
        client.setPresent(true)
        client.refresh()
        assertTrue(server.calls.last().path.contains("present=1"))
        client.setPresent(false)
        client.refresh()
        assertFalse(server.calls.last().path.contains("present=1"))
    }

    @Test
    fun asksNothingInTheBackground() = test {
        val client = chat()
        client.start()
        client.send("Hello")
        val before = server.calls.size
        client.setActive(false)
        client.refresh()
        assertEquals(before, server.calls.size)
    }

    @Test
    fun showsTypingAndStops() = test {
        val client = chat()
        client.start()
        client.send("Hello")
        server.typing = Typing("Ada")
        client.refresh()
        assertEquals(Typing("Ada"), client.current.typing)
        server.typing = null
        client.refresh()
        assertNull(client.current.typing)
    }

    @Test
    fun neverShowsAMessageTwice() = test {
        val client = chat()
        client.start()
        client.send("First")
        client.send("Second")
        client.refresh()
        client.refresh()
        assertEquals(listOf("First", "Second"), client.current.messages.map { it.text })
    }

    // -- a conversation the server no longer knows

    @Test
    fun goneConversationIsLetGo() = test {
        val client = chat()
        client.start()
        client.send("Hello")
        server.conversationGone = true
        client.refresh()
        server.conversationGone = false

        assertNull(client.current.conversation)
        assertTrue(client.current.messages.isEmpty())
        assertNull(stored())
        client.send("Hello again")
        assertEquals("ticket-1", client.current.conversation?.ticketId)
    }

    @Test
    fun goneConversationIsNotRestored() = test {
        val first = chat()
        first.start()
        first.send("Hello")
        first.destroy()
        server.conversationGone = true

        val second = chat()
        second.start()
        assertEquals(ChatStatus.READY, second.current.status)
        assertNull(second.current.conversation)
    }

    // -- who the visitor is

    @Test
    fun identityKnownBeforehandGoesWithTheStart() = test {
        val client = chat()
        client.start()
        client.identify(Identity(id = "user-42", email = "kim@shop.example.com", userHash = "abc"))
        client.send("Hello")

        val start = server.calls.first { it.method == "POST" && it.path.endsWith("/conversations/") }
        val identity = start.body?.get("identity") as JsonObject
        assertEquals(setOf("id", "email", "userHash"), identity.keys)
        assertEquals("user-42", (identity["id"] as JsonPrimitive).content)
    }

    @Test
    fun identityIsAttachedToAStartedConversation() = test {
        val client = chat()
        client.start()
        client.send("Hello")
        client.identify(Identity(id = "user-42", userHash = "abc"))
        assertTrue(server.calls.any { it.path.endsWith("/identify/") })
    }

    @Test
    fun identifyNeverFailsTheApp() = test {
        val client = chat()
        client.start()
        client.send("Hello")
        server.failNext = 500
        client.identify(Identity(id = "user-42"))
        assertEquals(1, client.current.messages.size)
    }

    @Test
    fun somebodyElseSigningInTakesTheConversationAway() = test {
        val client = chat()
        client.start()
        client.identify(Identity(id = "user-a"))
        client.send("Our invoices are wrong")
        client.identify(Identity(id = "user-b"))

        assertFalse(server.calls.any { it.path.endsWith("/identify/") })
        assertNull(client.current.conversation)
        assertTrue(client.current.messages.isEmpty())
        assertNull(stored())
    }

    @Test
    fun conflictFromServerDropsTheConversation() = test {
        val client = chat()
        client.start()
        client.send("Hello")
        server.failNext = 409
        client.identify(Identity(id = "user-b"))

        assertNull(client.current.conversation)
        assertTrue(client.current.messages.isEmpty())
        assertNull(stored())
    }

    @Test
    fun sameVisitorNamedAgainStaysAttached() = test {
        val client = chat()
        client.start()
        client.identify(Identity(id = "user-a"))
        client.send("Hello")
        client.identify(Identity(id = "user-a", name = "Ada"))

        assertEquals(1, client.current.messages.size)
        assertTrue(server.calls.any { it.path.endsWith("/identify/") })
    }

    @Test
    fun resetForgetsTheConversation() = test {
        val client = chat()
        client.start()
        client.send("Hello")
        client.reset()

        assertNull(client.current.conversation)
        assertTrue(client.current.messages.isEmpty())
        assertNull(stored())
    }

    // -- what subscribers see

    @Test
    fun subscribersGetNowThenEveryChange() = test {
        val seen = mutableListOf<ChatState>()
        val client = chat()
        val stop = client.subscribe { seen.add(it) }
        assertEquals(1, seen.size)
        assertEquals(ChatStatus.IDLE, seen[0].status)

        client.start()
        assertEquals(ChatStatus.READY, seen.last().status)

        stop()
        val count = seen.size
        client.send("Hello")
        assertEquals(count, seen.size)
    }

    private companion object {
        const val PROJECT = "pk_test"
    }
}
