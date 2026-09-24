# Changelog

Versions are immutable once published, so every line here describes something already in the
Maven repository. The tag is the release: `v0.1.0` publishes 0.1.0.

## Unreleased

- **First release.** The Helpwing chat for native Android apps, at parity with
  `@helpwing/react-native`: a pure-Kotlin `HelpwingChat` client (polling, an offline queue that
  survives process death, idempotent sends, identity, unread counts against the server's cursor),
  and Compose components on top of it — `SupportChat`, `SupportLauncher`, `SupportSheet`,
  `MessageBubble`, `MarkdownText` — plus `SupportActivity` for apps without Compose.
