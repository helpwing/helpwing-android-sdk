package ru.fanyagin.helpwing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.fanyagin.helpwing.core.ChatStatus

private const val BRAND_URL = "https://helpwing.app"

/**
 * The conversation as a screen. Being in composition is what marks the visitor as reading,
 * which stops an agent's reply also being emailed to them.
 */
@Composable
fun SupportChat(
    modifier: Modifier = Modifier,
    labels: SupportLabels = rememberSupport().labels,
    /** Drawn above the transcript; a header with a close button usually goes here. */
    header: (@Composable () -> Unit)? = null,
    /** Shown instead of an empty transcript. Defaults to the project's greeting. */
    empty: (@Composable () -> Unit)? = null,
) {
    val support = rememberSupport()
    val theme = support.theme
    val state = support.state
    val config = state.config
    val context = LocalContext.current
    val list = rememberLazyListState()
    val muted = TextStyle(color = theme.mutedText, fontSize = 14.sp, textAlign = TextAlign.Center, fontFamily = theme.fontFamily)

    PresenceEffect()

    // Only ever downwards, and only when something arrived.
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) list.animateScrollToItem(state.messages.size - 1)
    }

    if (state.status == ChatStatus.IDLE || state.status == ChatStatus.LOADING ||
        state.status == ChatStatus.UNCONFIGURED
    ) {
        Box(
            modifier.fillMaxSize().background(theme.background).padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(if (state.status == ChatStatus.UNCONFIGURED) labels.unavailable else labels.loading, style = muted)
        }
        return
    }

    // The address is asked for only until there is a conversation to reply to.
    val askForEmail = config?.requireEmail == true && state.conversation == null

    Column(modifier.fillMaxSize().background(theme.background).imePadding()) {
        header?.invoke()

        if (config?.showAgentAvailability == true) {
            BasicText(
                text = if (config?.isOnline == true) labels.online else labels.away,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                style = muted.copy(fontSize = 12.sp, textAlign = TextAlign.Start),
            )
            Box(Modifier.fillMaxWidth().height(1.dp).background(theme.border))
        }

        if (support.offline) {
            BasicText(
                text = labels.offline,
                modifier = Modifier.fillMaxWidth().background(theme.surface).padding(horizontal = 16.dp, vertical = 8.dp),
                style = muted.copy(fontSize = 12.sp, textAlign = TextAlign.Start),
            )
        }

        LazyColumn(
            state = list,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            if (state.messages.isEmpty()) {
                item {
                    Box(Modifier.fillParentMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        if (empty != null) {
                            empty()
                        } else {
                            val offlineMessage = support.copy.offlineMessage
                            BasicText(
                                text = if (config?.isOnline == false && offlineMessage.isNotEmpty()) {
                                    offlineMessage
                                } else {
                                    support.copy.greeting
                                },
                                style = muted.copy(fontSize = 15.sp, lineHeight = 22.sp),
                            )
                        }
                    }
                }
            } else {
                items(state.messages, key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        theme = theme,
                        labels = labels,
                        onRetry = { id -> support.retry(id) },
                    )
                }
            }
        }

        state.typing?.let { typing ->
            val typingText = support.copy.typingText
            BasicText(
                text = if (typingText.isNotBlank()) typingText.replace("{name}", typing.name) else labels.typing(typing.name),
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 6.dp),
                style = muted.copy(fontSize = 12.sp, textAlign = TextAlign.Start),
            )
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(theme.border))
        Composer(
            theme = theme,
            labels = labels,
            askForEmail = askForEmail,
            onSend = { text, email -> support.send(text, email) },
        )

        if (config?.showBranding == true) {
            BasicText(
                text = labels.branding,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { Links.open(context, BRAND_URL) }
                    .padding(bottom = 10.dp),
                style = muted.copy(fontSize = 11.sp),
            )
        }
    }
}
