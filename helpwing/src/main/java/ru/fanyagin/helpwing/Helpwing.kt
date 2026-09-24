package ru.fanyagin.helpwing

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.Job
import ru.fanyagin.helpwing.core.ChatOptions
import ru.fanyagin.helpwing.core.HelpwingChat
import ru.fanyagin.helpwing.core.Identity
import java.util.Locale
import java.util.TimeZone

data class HelpwingSettings(
    /** e.g. `https://api.helpwing.app`, the host that serves `/widget.js`. */
    val apiUrl: String,
    /** `pk_...`. Public, and safe to ship in the app. */
    val projectKey: String,
    /** Picks the project's translated copy, and is sent when a conversation opens. */
    val locale: String = Locale.getDefault().toLanguageTag(),
    val timezone: String = TimeZone.getDefault().id,
    val pollIntervalMs: Long = 5_000,
    /** Zero stops polling while the chat is closed. */
    val backgroundPollIntervalMs: Long = 30_000,
    /** Force dark on or off. Null lets the project's colour scheme decide (`Auto` follows the device). */
    val dark: Boolean? = null,
    val theme: SupportThemeOverride? = null,
    val labels: SupportLabels = SupportLabels(),
)

/** The process-wide chat. Initialise once, usually in `Application.onCreate()`. */
object Helpwing {
    @Volatile
    private var instance: HelpwingChat? = null
    private var started: Job? = null
    private var lifecycleObserver: DefaultLifecycleObserver? = null
    private var identified = false
    private var lastIdentity: Identity? = null

    @Volatile
    var settings: HelpwingSettings? = null
        private set

    val isInitialized: Boolean get() = instance != null

    val chat: HelpwingChat
        get() = instance ?: error("Call Helpwing.initialize(context, settings) first, usually in Application.onCreate().")

    /** Create the chat and start loading. Calling again for the same project only updates settings. */
    @JvmStatic
    fun initialize(context: Context, settings: HelpwingSettings): HelpwingChat {
        val existing = instance
        val previous = this.settings
        if (existing != null && previous != null &&
            previous.apiUrl == settings.apiUrl && previous.projectKey == settings.projectKey
        ) {
            this.settings = settings
            return existing
        }
        existing?.destroy()

        val chat = HelpwingChat(
            ChatOptions(
                apiUrl = settings.apiUrl,
                projectKey = settings.projectKey,
                storage = SharedPreferencesStorage(context),
                pollIntervalMs = settings.pollIntervalMs,
                backgroundPollIntervalMs = settings.backgroundPollIntervalMs,
                locale = settings.locale,
                timezone = settings.timezone,
            ),
        )
        instance = chat
        this.settings = settings
        val identity = lastIdentity
        val hadIdentity = identified
        started = chat.launch {
            chat.start()
            if (hadIdentity) chat.identify(identity)
        }
        observeLifecycle(chat)
        return chat
    }

    /**
     * Say who the visitor is; null on sign-out. Safe to call on every auth change, even before
     * [initialize]. `userHash` comes from your server: never ship the identity secret.
     */
    @JvmStatic
    fun identify(identity: Identity?) {
        if (identified && identity == lastIdentity) return
        identified = true
        lastIdentity = identity
        val chat = instance ?: return
        val waitFor = started
        chat.launch {
            waitFor?.join()
            chat.identify(identity)
        }
    }

    /** Forget the visitor and the conversation on this device. For sign-out on a shared device. */
    @JvmStatic
    fun reset() {
        identified = false
        lastIdentity = null
        val chat = instance ?: return
        chat.launch { chat.reset() }
    }

    /** Open the chat as a full-screen activity, for apps that do not use Compose. */
    @JvmStatic
    fun open(context: Context) {
        val intent = Intent(context, SupportActivity::class.java)
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** Polling stops while the app is in the background. */
    private fun observeLifecycle(chat: HelpwingChat) {
        val attach = Runnable {
            val lifecycle = ProcessLifecycleOwner.get().lifecycle
            lifecycleObserver?.let { lifecycle.removeObserver(it) }
            val observer = object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) = chat.setActive(true)
                override fun onStop(owner: LifecycleOwner) = chat.setActive(false)
            }
            lifecycleObserver = observer
            lifecycle.addObserver(observer)
        }
        if (Looper.myLooper() == Looper.getMainLooper()) attach.run() else Handler(Looper.getMainLooper()).post(attach)
    }
}
