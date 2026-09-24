package ru.fanyagin.helpwing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.fanyagin.helpwing.core.Author
import ru.fanyagin.helpwing.core.ChatMessage
import ru.fanyagin.helpwing.core.Delivery

/** One message in the transcript. Retry is offered only on a message that failed. */
@Composable
fun MessageBubble(
    message: ChatMessage,
    theme: SupportTheme,
    labels: SupportLabels,
    onRetry: (clientMessageId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mine = message.author == Author.CUSTOMER
    val images = remember(message.attachments) {
        message.attachments
            .filter { it.contentId.isNotEmpty() && it.url.isNotEmpty() }
            .associate { it.contentId to it.url }
    }

    if (message.author == Author.SYSTEM) {
        BasicText(
            text = message.text,
            modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            style = TextStyle(
                color = theme.mutedText,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                fontFamily = theme.fontFamily,
            ),
        )
        return
    }

    val ink = if (mine) theme.onAccent else theme.text
    val quiet = if (mine) theme.onAccent else theme.mutedText
    val shape = if (mine) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp)
    }

    BoxWithConstraints(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        val limit = maxWidth * 0.85f
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
        ) {
            Column(
                Modifier
                    .widthIn(max = limit)
                    .alpha(if (message.delivery == Delivery.PENDING) 0.6f else 1f)
                    .clip(shape)
                    .background(if (mine) theme.accent else theme.surface)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                if (!mine && message.authorName.isNotEmpty()) {
                    BasicText(
                        text = message.authorName,
                        modifier = Modifier.padding(bottom = 2.dp),
                        style = TextStyle(
                            color = theme.mutedText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = theme.fontFamily,
                        ),
                    )
                }
                if (message.markdown) {
                    // On the accent there is no second colour to be quiet in.
                    MarkdownText(message.text, theme, color = ink, muted = quiet, images = images)
                } else {
                    BasicText(
                        text = message.text,
                        style = TextStyle(color = ink, fontSize = 15.sp, lineHeight = 21.sp, fontFamily = theme.fontFamily),
                    )
                }
                // An inline picture is already drawn in the body; listing it again reads as two files.
                message.attachments.filter { !it.isInline }.forEach { attachment ->
                    BasicText(
                        text = attachment.filename,
                        modifier = Modifier.padding(top = 6.dp),
                        style = TextStyle(
                            color = quiet,
                            fontSize = 13.sp,
                            textDecoration = TextDecoration.Underline,
                            fontFamily = theme.fontFamily,
                        ),
                    )
                }
            }

            val status = TextStyle(fontSize = 11.sp, fontFamily = theme.fontFamily)
            when (message.delivery) {
                Delivery.PENDING -> BasicText(
                    text = labels.sending,
                    modifier = Modifier.padding(top = 3.dp, start = 4.dp, end = 4.dp),
                    style = status.copy(color = theme.mutedText),
                )
                Delivery.FAILED -> BasicText(
                    text = "${labels.failed} ${labels.retry}",
                    modifier = Modifier
                        .padding(top = 3.dp, start = 4.dp, end = 4.dp)
                        .clickable(role = Role.Button) { message.clientMessageId?.let(onRetry) },
                    style = status.copy(color = theme.danger),
                )
                Delivery.SENT -> Unit
            }
        }
    }
}
