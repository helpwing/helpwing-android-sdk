# Helpwing for Android

The [Helpwing](https://helpwing.app) support chat for native Android apps. App chat, website
chat and email tickets land in the same inbox.

- Drop-in launcher, sheet and chat screen in Jetpack Compose, or `SupportActivity` for an app
  without Compose
- A pure-Kotlin `HelpwingChat` client with a `StateFlow`, if you would rather draw your own
- Themed from your project's accent colour, or overridden to match your design system
- Every string is a parameter, so it speaks whatever language your app does
- Survives tunnels and process death: messages queue, persist and retry

```kotlin
// settings.gradle.kts: the repository you publish to, then
dependencies {
    implementation("ru.fanyagin.helpwing:helpwing-android:0.1.0")
}
```

`minSdk` 24. Needs the `INTERNET` permission, which the library's manifest already declares.

## Setup

Initialise once, in your `Application`:

```kotlin
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Helpwing.initialize(this, HelpwingSettings(apiUrl = "https://api.helpwing.app", projectKey = "pk_live_..."))
    }
}
```

Then put the launcher and the sheet over your content:

```kotlin
setContent {
    HelpwingProvider {
        Box(Modifier.fillMaxSize()) {
            AppNavigation()
            SupportLauncher()
            SupportSheet()
        }
    }
}
```

No Compose? `Helpwing.open(context)` starts `SupportActivity`, a full-screen chat with a close
button.

The project key is public — it is the same one the website snippet carries in
`data-project`, and shipping it in an APK is expected.

`apiUrl` is whichever host serves `/widget.js`, because that is the host `/widget/` is
routed on.

## Your own screen instead

The launcher and the sheet are conveniences. An app with its own "Help" row in a settings
list wants neither:

```kotlin
@Composable
fun HelpRow(onClick: () -> Unit) {
    val support = rememberSupport()
    Row(onClick, badge = support.unreadCount)
}

@Composable
fun SupportDestination(onBack: () -> Unit) {
    SupportScreen(onClose = onBack)   // or SupportChat() with a header of your own
}
```

Being in composition is what marks the visitor as reading. Everything is on
`rememberSupport()`: `state`, `messages`, `unreadCount`, `typing`, `offline`, `config`, `copy`,
`theme`, `send`, `retry`, `refresh`, `open`, `close` and the `chat` client itself. Drawing the
transcript some other way? Call `PresenceEffect()` in it.

## Telling us who the visitor is

```kotlin
Helpwing.identify(
    user?.let { Identity(id = it.id, email = it.email, name = it.name, userHash = it.supportHash) },
)
```

Call it whenever your auth state changes — identical identities are ignored, and a call made
before `initialize` is applied once it runs. It reaches a conversation that started before
anyone signed in too: a visitor who asked something anonymously and then signed in is the
ordinary case, and the conversation moves to their customer record when they do.

**`userHash` is an HMAC-SHA256 of the user id, keyed with your project's identity secret,
and your server computes it.** An APK is a zip file with your code in it; a secret shipped
inside one is not a secret. Fetch the hash from your own API alongside the rest of the
signed-in user, the way you would a session token.

With identity verification turned on in the dashboard, an unproved claim is not refused —
it simply buys nothing. The visitor gets a customer record of their own and what they
claimed is kept where an agent can see it without the profile implying anybody vouched for
it.

On sign-out, `Helpwing.identify(null)` stops the *next* conversation being attributed to
whoever just left; `Helpwing.reset()` also forgets the conversation itself, which is what you
want on a shared device.

Signing straight in as somebody else needs neither. A conversation belongs to whoever opened
it: when this client sees A and then B, B does not inherit A's. It is dropped from the device
and B opens their own. The server draws the same line and refuses to move a conversation
between two identified customers.

## Text

**The chat's own chrome** — "Send", "Write a message…". No translations ship with this
library; every string is on `SupportLabels`:

```kotlin
HelpwingSettings(…, labels = SupportLabels(placeholder = "Écrivez un message…", send = "Envoyer"))
```

or per call site, `SupportChat(labels = …)`. Your app knows what language it is in and already
has string resources; use them.

**What the project wrote** — the header, the greeting and the offline message. Those are
translated in the dashboard under **Chat widget → Appearance**, and picked by
`HelpwingSettings.locale`, which defaults to the device's. `ru-RU` finds `ru`, and a language
the project has not translated falls back to what it wrote without one. Read them yourself
with `rememberSupport().copy` if you draw your own empty state.

## Colours

The project picks an accent in the dashboard and everything else is worked out from it,
including whether text on top of it should be white or ink.

Light or dark comes from **Chat widget → Appearance → Colour scheme**, and ships as `Auto`,
which follows the device. `dark` overrules both:

```kotlin
HelpwingSettings(…, dark = true)      // a screen that is dark whatever the phone says
```

An app with a design system of its own names only what differs:

```kotlin
HelpwingSettings(
    …,
    dark = true,
    theme = SupportThemeOverride(
        accent = Color(0xFFC6FF3A),
        background = Color(0xFF0A0A0B),
        surface = Color(0xFF141517),
        fontFamily = FontFamily(Font(R.font.fira_sans)),
    ),
)
```

`HelpwingProvider(dark = …, theme = …, labels = …)` overrides the same things for one subtree.

## Delivery, polling and offline

- New messages are polled every 5 seconds while the chat is on screen and every 30 while it
  is not, so the unread badge stays honest. Polling stops entirely while the app is in the
  background (`ProcessLifecycleOwner`). Both intervals are settings.
- Sends are idempotent and retried automatically, so a request lost mid-tunnel does not
  produce a duplicate. Starting a brand new conversation is the exception: if its answer is
  lost it is shown as a failed send, and tapping it is the visitor's decision.
- Unsent messages are written to SharedPreferences, so they go out on the next launch, in
  order, even if the process was killed.
- Sends started from the UI run on the client's own scope, so closing the screen never cancels
  one halfway.
- Unread counts are computed against the server's cursor, never the phone's clock.

## Operating hours

The project's **Settings → Operating hours** decides whether the chat greets a visitor or
offers its offline message: `state.config?.isOnline` is that answer, and `SupportChat` draws
both. A project may also choose to disappear outside its hours, and then `state.status` is
`UNCONFIGURED` while it is closed — the same state a widget that was turned off reports.

## Markdown

Agents answer from a markdown composer, so a reply arrives formatted: **bold**, _italic_,
`code`, links, lists, quotes, headings, fenced code, rules and tables. The parser is a port of
the one every other Helpwing surface uses and produces plain data (`Markdown.parse`), which
`MarkdownText` draws with `BasicText` and `AnnotatedString`. No HTML, no WebView.

`ChatMessage.markdown` says whether `text` is markdown; false only for a message stored
before inbound email was translated on the way in.

A picture pasted into an email comes back as an `isInline` attachment the body points at with
`![alt](cid:<contentId>)`. `MessageBubble` draws those where they were written, fetched with
`HttpURLConnection` and `BitmapFactory` — no image library is pulled in — and leaves them out
of the file list. A `cid` with no file behind it renders as its alt text.

Links open with `ACTION_VIEW`, and only ever `http`, `https`, `mailto` and `tel`.

## What it does not do

- **Push notifications.** Nothing arrives while the app is closed. Turn on `require_email`
  and a reply written while the visitor is away reaches them by email.
- **Attachments from the visitor.** The API has no endpoint for it yet.
- **One conversation on two devices.** A visitor token belongs to one installation.

## The chat without the components

`ru.fanyagin.helpwing.core.HelpwingChat` imports nothing from Android and unit-tests on the
JVM.

| | |
| --- | --- |
| `state: StateFlow<ChatState>` | Everything true about the conversation. `current` is its value now. |
| `start()` | Load the config and whatever conversation is stored. Never throws. |
| `send(text, email)` | Send, drawing the message before it has gone anywhere. |
| `retry(clientMessageId)` | Try a failed message again. |
| `identify(identity)` | Say who the visitor is, now or later. |
| `setPresent(Boolean)` | Whether the conversation is in front of them. |
| `setActive(Boolean)` | Whether the app is in the foreground. |
| `markRead()` | Clear the unread count. |
| `refresh()` | Ask now rather than at the next tick. |
| `reset()` | Forget the visitor and the conversation on this device. |
| `subscribe(listener)` | Called immediately, then on every change. Returns the unsubscribe. |
| `destroy()` | Stop everything. |

The suspend functions switch to the client's own single-lane dispatcher, so they are safe to
call from any thread. Errors land in `state.error` rather than being thrown.

Bring your own HTTP stack by implementing `HttpClient` (throw an `IOException` for "no
answer"), and your own storage by implementing `HelpwingStorage`.

## Reference

| `HelpwingSettings` | |
| --- | --- |
| `apiUrl` | Where the API lives. Required. |
| `projectKey` | `pk_…`. Required. |
| `locale` | Picks the project's translated copy and is sent when a conversation opens. Device default. |
| `timezone` | Sent when a conversation opens. Device default. |
| `pollIntervalMs` | While the chat is on screen. Default 5000. |
| `backgroundPollIntervalMs` | While it is not. Default 30000; `0` to stop. |
| `dark` | Force the dark palette on or off. Null lets the project's Colour scheme decide. |
| `theme` | `SupportThemeOverride`: any colour, and the font family. |
| `labels` | `SupportLabels`: every string the chat draws. |

## Building

```bash
gradle wrapper --gradle-version 8.11.1   # once: the wrapper jar is not checked in yet
./gradlew test                            # core unit tests, JVM only
./gradlew :sample:installDebug
```

## Links

- [Documentation](https://helpwing.app/docs/android)
- [Issues](https://github.com/helpwing/helpwing-android-sdk/issues)
- [Changelog](CHANGELOG.md)

MIT © Helpwing
