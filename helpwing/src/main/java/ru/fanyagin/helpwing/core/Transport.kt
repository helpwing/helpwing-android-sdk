package ru.fanyagin.helpwing.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class HttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String>,
    val body: String?,
)

class HttpResponse(val status: Int, val body: String)

/** The one seam to the network. Throw an [IOException] when there was no answer at all. */
interface HttpClient {
    suspend fun execute(request: HttpRequest): HttpResponse
}

/** The default client: `HttpURLConnection` on the IO dispatcher, no extra dependency. */
class UrlConnectionHttpClient(
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 30_000,
) : HttpClient {
    override suspend fun execute(request: HttpRequest): HttpResponse = withContext(Dispatchers.IO) {
        val connection = URL(request.url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = request.method
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")
            for ((name, value) in request.headers) connection.setRequestProperty(name, value)
            val payload = request.body
            if (payload != null) {
                connection.doOutput = true
                connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val stream = if (status >= 400) connection.errorStream else connection.inputStream
            val text = stream?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
            HttpResponse(status, text)
        } finally {
            connection.disconnect()
        }
    }
}

/** A request that failed. `status` is 0 when no answer arrived at all. */
class HelpwingException(
    message: String,
    val status: Int,
    val payload: JsonElement? = null,
) : Exception(message) {
    /** Airplane mode, a dead tunnel, a captive portal. */
    val isNetwork: Boolean get() = status == NETWORK

    /** The visitor token names nothing any more. */
    val isGone: Boolean get() = status == 404

    companion object {
        const val NETWORK = 0
    }
}

internal val HelpwingJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
    encodeDefaults = true
}

/** Every public widget endpoint, and nothing else. */
class Transport(apiUrl: String, projectKey: String, private val client: HttpClient) {
    private val base = "${apiUrl.trimEnd('/')}/widget/${encode(projectKey)}"

    suspend fun config(): WidgetConfig = request("GET", "/config/", WidgetConfig.serializer())

    /** Open a conversation with its first message. Carries no idempotency key: the server takes none. */
    suspend fun start(
        bodyText: String,
        identity: Identity?,
        email: String,
        locale: String,
        timezone: String,
    ): ApiConversation {
        val body = buildJsonObject {
            put("body_text", bodyText)
            if (identity != null) put("identity", identity.toJson())
            if (email.isNotEmpty()) put("email", email)
            if (locale.isNotEmpty()) put("locale", locale)
            if (timezone.isNotEmpty()) put("timezone", timezone)
        }
        return request("POST", "/conversations/", ApiConversation.serializer(), body = body)
    }

    suspend fun conversation(ticketId: String, token: String): ApiConversation =
        request("GET", "/conversations/$ticketId/", ApiConversation.serializer(), token = token)

    /** Idempotent by `client_message_id`: a retry stores nothing twice. */
    suspend fun send(ticketId: String, token: String, bodyText: String, clientMessageId: String): ApiConversation {
        val body = buildJsonObject {
            put("body_text", bodyText)
            put("client_message_id", clientMessageId)
        }
        return request("POST", "/conversations/$ticketId/messages/", ApiConversation.serializer(), token, body)
    }

    suspend fun identify(ticketId: String, token: String, identity: Identity) {
        call("POST", "/conversations/$ticketId/identify/", token, identity.toJson())
    }

    /** What happened since `since`. `present` tells the server the visitor is reading. */
    suspend fun updates(ticketId: String, token: String, since: String, present: Boolean): ApiUpdates {
        val query = "?since=${encode(since)}" + if (present) "&present=1" else ""
        return request("GET", "/conversations/$ticketId/updates/$query", ApiUpdates.serializer(), token)
    }

    private suspend fun <T> request(
        method: String,
        path: String,
        deserializer: DeserializationStrategy<T>,
        token: String? = null,
        body: JsonObject? = null,
    ): T {
        val data = call(method, path, token, body)
        return try {
            HelpwingJson.decodeFromJsonElement(deserializer, data ?: JsonNull)
        } catch (error: Exception) {
            throw HelpwingException("The server sent an answer this client could not read.", 200, data)
        }
    }

    private suspend fun call(method: String, path: String, token: String?, body: JsonObject?): JsonElement? {
        val headers = mutableMapOf<String, String>()
        if (body != null) headers["Content-Type"] = "application/json"
        if (!token.isNullOrEmpty()) headers[VISITOR_HEADER] = token

        val response = try {
            client.execute(HttpRequest(method, base + path, headers, body?.toString()))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            throw HelpwingException(error.message ?: "The network request failed.", HelpwingException.NETWORK)
        }

        val data = if (response.status == 204 || response.body.isBlank()) {
            null
        } else {
            try {
                HelpwingJson.parseToJsonElement(response.body)
            } catch (ignored: Exception) {
                null
            }
        }
        if (response.status !in 200..299) {
            throw HelpwingException(messageIn(data) ?: "HTTP ${response.status}", response.status, data)
        }
        return data
    }

    companion object {
        /** `VISITOR_TOKEN_HEADER` on the server. */
        const val VISITOR_HEADER = "X-Helpwing-Visitor"

        private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

        /** The API's error envelope: `{ error: { message } }`. */
        private fun messageIn(data: JsonElement?): String? {
            val error = (data as? JsonObject)?.get("error") as? JsonObject ?: return null
            val message = (error["message"] as? JsonPrimitive)?.contentOrNull
            return message?.takeIf { it.isNotEmpty() }
        }
    }
}

internal fun Identity.toJson(): JsonObject = buildJsonObject {
    id?.let { put("id", it) }
    email?.let { put("email", it) }
    name?.let { put("name", it) }
    metadata?.let { values ->
        put("metadata", buildJsonObject {
            for ((key, value) in values) {
                when (value) {
                    is Boolean -> put(key, value)
                    is Number -> put(key, value)
                    else -> put(key, value.toString())
                }
            }
        })
    }
    userHash?.let { put("userHash", it) }
}
