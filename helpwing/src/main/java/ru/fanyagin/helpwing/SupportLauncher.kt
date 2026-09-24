package ru.fanyagin.helpwing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class LauncherPosition { BOTTOM_RIGHT, BOTTOM_LEFT }

/**
 * The round button in the corner, with the unread badge. Put it last in a full-screen Box.
 * Hidden while the chat is open, unavailable, or turned off for phones in the dashboard.
 */
@Composable
fun SupportLauncher(
    modifier: Modifier = Modifier,
    /** Defaults to the corner the project chose. */
    position: LauncherPosition? = null,
    /** Distance from the screen edges, inside the safe area. */
    margin: Dp = 24.dp,
    label: String = rememberSupport().labels.launcher,
    /** Defaults to opening [SupportSheet]. */
    onClick: (() -> Unit)? = null,
) {
    val support = rememberSupport()
    val theme = support.theme
    val config = support.config
    if (!support.isReady || support.isOpen || config?.showOnMobile == false) return

    val corner = position
        ?: if (config?.launcherPosition == "bottom_left") LauncherPosition.BOTTOM_LEFT else LauncherPosition.BOTTOM_RIGHT
    val shape = RoundedCornerShape(28.dp)

    Box(
        modifier.fillMaxSize().safeDrawingPadding().padding(margin),
        contentAlignment = if (corner == LauncherPosition.BOTTOM_LEFT) Alignment.BottomStart else Alignment.BottomEnd,
    ) {
        Box {
            Box(
                modifier = Modifier
                    .shadow(4.dp, shape)
                    .clip(shape)
                    .background(theme.accent)
                    .clickable(role = Role.Button) { onClick?.invoke() ?: support.open() }
                    .semantics { contentDescription = label }
                    .height(56.dp)
                    .widthIn(min = 56.dp)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = label,
                    style = TextStyle(
                        color = theme.onAccent,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = theme.fontFamily,
                    ),
                )
            }
            val unread = support.unreadCount
            if (unread > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                        .height(22.dp)
                        .widthIn(min = 22.dp)
                        .clip(CircleShape)
                        .background(theme.danger)
                        .border(2.dp, theme.background, CircleShape)
                        .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        text = if (unread > 9) "9+" else unread.toString(),
                        style = TextStyle(
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = theme.fontFamily,
                        ),
                    )
                }
            }
        }
    }
}
