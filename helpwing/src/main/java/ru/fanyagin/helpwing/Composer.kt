package ru.fanyagin.helpwing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The message box, and the address box above it while the project asks for one. */
@Composable
fun Composer(
    theme: SupportTheme,
    labels: SupportLabels,
    askForEmail: Boolean,
    onSend: (text: String, email: String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var text by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    val ready = enabled && text.isNotBlank() && (!askForEmail || email.isNotBlank())
    val style = TextStyle(color = theme.text, fontSize = 15.sp, fontFamily = theme.fontFamily)

    Column(
        modifier
            .fillMaxWidth()
            .background(theme.background)
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp),
    ) {
        if (askForEmail) {
            Field(
                value = email,
                onValueChange = { email = it },
                placeholder = labels.emailPlaceholder,
                theme = theme,
                style = style,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    keyboardType = KeyboardType.Email,
                ),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Field(
                value = text,
                onValueChange = { text = it },
                placeholder = labels.placeholder,
                theme = theme,
                style = style,
                singleLine = false,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .height(40.dp)
                    .alpha(if (ready) 1f else 0.4f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(theme.accent)
                    .clickable(enabled = ready, role = Role.Button) {
                        onSend(text, email.trim())
                        text = ""
                    }
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = labels.send,
                    style = TextStyle(
                        color = theme.onAccent,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = theme.fontFamily,
                    ),
                )
            }
        }
    }
}

@Composable
private fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    theme: SupportTheme,
    style: TextStyle,
    singleLine: Boolean,
    keyboardOptions: KeyboardOptions,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = style,
        singleLine = singleLine,
        maxLines = if (singleLine) 1 else 6,
        keyboardOptions = keyboardOptions,
        cursorBrush = SolidColor(theme.accent),
        modifier = modifier,
        decorationBox = { inner ->
            Box(
                Modifier
                    .heightIn(min = 40.dp, max = 120.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(theme.surface)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) BasicText(placeholder, style = style.copy(color = theme.mutedText))
                inner()
            }
        },
    )
}
