package ru.fanyagin.helpwing.core

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.IOException
import java.net.URI
import java.net.URLDecoder

/** A stand-in for the public widget API. */
class FakeServer : HttpClient {
    class Call(val method: String, val path: String, val token: String?, val body: JsonObject?)

    val calls = mutableListOf<Call>()
    var config = WidgetConfig(
        isEnabled = true,
        projectName = "Acme Cloud",
        accentColor = "#2563eb",
        launcherIcon = "chat",
        launcherPosition = "bottom_right",
        showBranding = true,
        greeting = "Hi! How can we help?",
        offlineMessage = "We are offline right now.",
        requireEmail = false,
        showAgentAvailability = true,
        isOnline = true,
    )
    val messages = mutableListOf<ApiMessage>()
    var typing: Typing? = null

    /** `"network"` throws, a number answers with that status. */
    var failNext: Any? = null
    var failAll: Any? = null
    var conversationGone = false

    private var clock = 0

    override suspend fun execute(request: HttpRequest): HttpResponse {
        val uri = URI(request.url)
        val path = uri.rawPath + (uri.rawQuery?.let { "?$it" } ?: "")
        val body = request.body?.let { HelpwingJson.parseToJsonElement(it) as JsonObject }
        calls.add(Call(request.method, path, request.headers[Transport.VISITOR_HEADER], body))

        val failure = failNext ?: failAll
        failNext = null
        if (failure == "network") throw IOException("Network request failed")
        if (failure is Int) return answer(failure, """{"error":{"message":"Refused by the fake server."}}""")

        return route(uri, request.method, body)
    }

    fun reply(text: String, name: String = "Ada"): ApiMessage {
        val message = message(author = "agent", authorName = name, bodyText = text)
        messages.add(message)
        return message
    }

    fun push(message: ApiMessage) {
        messages.add(message)
    }

    private fun route(uri: URI, method: String, body: JsonObject?): HttpResponse {
        val path = uri.rawPath

        if (path.endsWith("/config/")) return answer(200, HelpwingJson.encodeToString(WidgetConfig.serializer(), config))

        if (conversationGone && path.contains("/conversations/") && !path.endsWith("/conversations/")) {
            return answer(404, """{"error":{"message":"No conversation was found for that visitor token."}}""")
        }

        if (path.endsWith("/conversations/") && method == "POST") {
            messages.add(message(author = "customer", bodyText = body.string("body_text")))
            return conversation(token = "visitor-token-1", only = messages.takeLast(1))
        }

        if (path.endsWith("/messages/") && method == "POST") {
            val clientMessageId = body.string("client_message_id")
            val already = messages.firstOrNull { clientMessageId.isNotEmpty() && it.clientMessageId == clientMessageId }
            val stored = already ?: message(
                author = "customer",
                bodyText = body.string("body_text"),
                clientMessageId = clientMessageId,
            )
            if (already == null) messages.add(stored)
            return conversation(only = listOf(stored), status = 201)
        }

        if (path.endsWith("/identify/")) return answer(200, """{"customer_id":"c1","is_identity_verified":false}""")

        if (path.endsWith("/updates/")) {
            val since = uri.rawQuery.orEmpty().split("&")
                .firstOrNull { it.startsWith("since=") }
                ?.let { URLDecoder.decode(it.removePrefix("since="), "UTF-8") }
                .orEmpty()
            val fresh = messages.filter { it.createdAt > since }
            val updates = ApiUpdates(cursor = now(), hasChanges = fresh.isNotEmpty(), status = "open", messages = fresh, typing = typing)
            return answer(200, HelpwingJson.encodeToString(ApiUpdates.serializer(), updates))
        }

        if (method == "GET") return conversation()
        return answer(404, """{"error":{"message":"No such thing."}}""")
    }

    private fun conversation(token: String = "", only: List<ApiMessage>? = null, status: Int = 201): HttpResponse {
        val payload = ApiConversation(
            ticketId = "ticket-1",
            ticketReference = "k7m2p9qx3r",
            status = "open",
            subject = "The widget throws a CSP error",
            visitorToken = token,
            messages = only ?: messages.toList(),
            typing = typing,
            cursor = now(),
        )
        return answer(status, HelpwingJson.encodeToString(ApiConversation.serializer(), payload))
    }

    fun message(
        author: String = "customer",
        authorName: String = "",
        bodyText: String = "",
        bodyHtml: String = "",
        clientMessageId: String = "",
    ) = ApiMessage(
        id = "m${messages.size + 1}",
        author = author,
        authorName = authorName,
        bodyText = bodyText,
        bodyHtml = bodyHtml,
        createdAt = now(),
        clientMessageId = clientMessageId,
    )

    /** A clock that only goes forwards, so cursors order the way real timestamps do. */
    private fun now(): String {
        clock += 1
        return "2026-08-09T12:%02d:%02d.000Z".format(clock / 60, clock % 60)
    }

    private fun answer(status: Int, json: String) = HttpResponse(status, json)

    private fun JsonObject?.string(name: String): String =
        (this?.get(name) as? JsonPrimitive)?.contentOrNull.orEmpty()
}
