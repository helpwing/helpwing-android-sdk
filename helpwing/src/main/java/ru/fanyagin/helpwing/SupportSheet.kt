package ru.fanyagin.helpwing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** [SupportChat] under a title and a close button. */
@Composable
fun SupportScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    /** Defaults to the project's own heading, then its name. */
    title: String? = null,
    labels: SupportLabels = rememberSupport().labels,
) {
    val support = rememberSupport()
    val theme = support.theme
    val titleText = title ?: support.copy.title.ifEmpty { support.config?.projectName.orEmpty() }

    SupportChat(
        modifier = modifier,
        labels = labels,
        header = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicText(
                        text = titleText,
                        modifier = Modifier.weight(1f).padding(end = 12.dp).semantics { heading() },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(
                            color = theme.text,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = theme.fontFamily,
                        ),
                    )
                    BasicText(
                        text = labels.close,
                        modifier = Modifier.clickable(role = Role.Button, onClick = onClose).padding(4.dp),
                        style = TextStyle(
                            color = theme.accent,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = theme.fontFamily,
                        ),
                    )
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(theme.border))
            }
        },
    )
}

/** The chat over your content while [Support.isOpen]; [SupportLauncher] opens it. */
@Composable
fun SupportSheet(
    title: String? = null,
    labels: SupportLabels = rememberSupport().labels,
) {
    val support = rememberSupport()
    if (!support.isOpen) return
    Dialog(
        onDismissRequest = { support.close() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        SupportScreen(
            onClose = { support.close() },
            modifier = Modifier.fillMaxSize().background(support.theme.background),
            title = title,
            labels = labels,
        )
    }
}
