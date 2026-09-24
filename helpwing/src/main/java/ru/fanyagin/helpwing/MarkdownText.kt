package ru.fanyagin.helpwing

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.fanyagin.helpwing.core.Align
import ru.fanyagin.helpwing.core.Block
import ru.fanyagin.helpwing.core.Inline
import ru.fanyagin.helpwing.core.Markdown
import java.net.URL

private class Shared(
    val theme: SupportTheme,
    val color: Color,
    val muted: Color,
    val images: Map<String, String>,
    val onLink: (String) -> Unit,
)

/**
 * A message body rendered from markdown with Compose primitives; no HTML anywhere.
 * `images` maps `content_id` to URL, so a body only shows pictures that came with it.
 */
@Composable
fun MarkdownText(
    source: String,
    theme: SupportTheme,
    color: Color,
    modifier: Modifier = Modifier,
    muted: Color = color,
    images: Map<String, String> = emptyMap(),
) {
    val context = LocalContext.current
    val blocks = remember(source) { Markdown.parse(source) }
    val shared = Shared(theme, color, muted, images) { href -> Links.open(context, href) }
    Blocks(blocks, shared, modifier)
}

@Composable
private fun Blocks(blocks: List<Block>, shared: Shared, modifier: Modifier = Modifier, color: Color = shared.color) {
    val scoped = if (color == shared.color) shared else Shared(shared.theme, color, shared.muted, shared.images, shared.onLink)
    Column(modifier) {
        blocks.forEachIndexed { index, block ->
            Box(if (index > 0) Modifier.padding(top = 8.dp) else Modifier) {
                BlockView(block, scoped)
            }
        }
    }
}

private fun bodyStyle(shared: Shared) = TextStyle(
    color = shared.color,
    fontSize = 15.sp,
    lineHeight = 21.sp,
    fontFamily = shared.theme.fontFamily,
)

@Composable
private fun BlockView(block: Block, shared: Shared) {
    when (block) {
        is Block.Paragraph -> Paragraph(block.spans, shared)

        is Block.Heading -> BasicText(
            text = annotate(block.spans, shared),
            modifier = Modifier.semantics { heading() },
            style = bodyStyle(shared).copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = when (block.level) {
                    1 -> 19.sp
                    2 -> 17.sp
                    3 -> 16.sp
                    else -> 15.sp
                },
            ),
        )

        is Block.ListBlock -> Column {
            block.items.forEachIndexed { index, item ->
                Row(if (index > 0) Modifier.padding(top = 4.dp) else Modifier) {
                    // A fixed marker column keeps wrapped lines under the item's text.
                    BasicText(
                        text = if (block.ordered) "${block.start + index}." else "•",
                        modifier = Modifier.width(20.dp),
                        style = bodyStyle(shared).copy(color = shared.muted),
                    )
                    Blocks(item, shared, Modifier.weight(1f))
                }
            }
        }

        is Block.Quote -> Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(2.dp).fillMaxHeight().background(shared.muted))
            Blocks(block.blocks, shared, Modifier.padding(start = 10.dp), color = shared.muted)
        }

        is Block.Code -> Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(shared.theme.border)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            BasicText(
                text = block.text,
                style = TextStyle(
                    color = shared.theme.text,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                ),
            )
        }

        is Block.Table -> Column(
            Modifier
                .fillMaxWidth()
                .border(1.dp, shared.muted, RoundedCornerShape(6.dp))
                .clip(RoundedCornerShape(6.dp)),
        ) {
            TableRow(block.head, shared, header = true)
            block.rows.forEach { row ->
                Box(Modifier.fillMaxWidth().height(1.dp).background(shared.muted.copy(alpha = 0.5f)))
                TableRow(row, shared, header = false)
            }
        }

        Block.Rule -> Box(Modifier.fillMaxWidth().height(1.dp).background(shared.muted.copy(alpha = 0.5f)))
    }
}

@Composable
private fun TableRow(cells: List<ru.fanyagin.helpwing.core.Cell>, shared: Shared, header: Boolean) {
    Row(Modifier.fillMaxWidth()) {
        cells.forEach { cell ->
            BasicText(
                text = annotate(cell.spans, shared),
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 5.dp),
                style = bodyStyle(shared).copy(
                    fontWeight = if (header) FontWeight.SemiBold else null,
                    textAlign = when (cell.align) {
                        Align.LEFT -> TextAlign.Start
                        Align.CENTER -> TextAlign.Center
                        Align.RIGHT -> TextAlign.End
                    },
                ),
            )
        }
    }
}

/** A paragraph; a picture that can be drawn stands on its own line between runs of words. */
@Composable
private fun Paragraph(spans: List<Inline>, shared: Shared) {
    val runs = mutableListOf<Any>()
    for (span in spans) {
        if (span is Inline.Image && shared.images[span.cid] != null) {
            runs.add(span)
        } else {
            @Suppress("UNCHECKED_CAST")
            val last = runs.lastOrNull() as? MutableList<Inline>
            if (last != null) last.add(span) else runs.add(mutableListOf(span))
        }
    }

    if (runs.size == 1 && runs[0] is MutableList<*>) {
        BasicText(text = annotate(spans, shared), style = bodyStyle(shared))
        return
    }
    Column {
        for (run in runs) {
            if (run is Inline.Image) {
                InlineImage(shared.images.getValue(run.cid), run.alt, shared.theme)
            } else {
                @Suppress("UNCHECKED_CAST")
                val words = run as List<Inline>
                if (words.any { it !is Inline.Text || it.text.isNotBlank() }) {
                    BasicText(text = annotate(words, shared), style = bodyStyle(shared))
                }
            }
        }
    }
}

private fun annotate(spans: List<Inline>, shared: Shared): AnnotatedString = buildAnnotatedString {
    appendSpans(spans, shared)
}

private fun AnnotatedString.Builder.appendSpans(spans: List<Inline>, shared: Shared) {
    for (span in spans) {
        when (span) {
            is Inline.Text -> append(span.text)
            Inline.Break -> append("\n")
            is Inline.Code -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp)) {
                append(span.text)
            }
            is Inline.Strong -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                appendSpans(span.children, shared)
            }
            is Inline.Em -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                appendSpans(span.children, shared)
            }
            is Inline.Strike -> withStyle(
                SpanStyle(textDecoration = TextDecoration.LineThrough, color = shared.muted),
            ) {
                appendSpans(span.children, shared)
            }
            // Only a picture whose file is missing reaches here; mail clients show its alt text.
            is Inline.Image -> append(span.alt)
            is Inline.Link -> {
                // On the accent the accent is invisible, so a link there keeps the bubble's colour.
                val linkColor = if (shared.color == shared.theme.onAccent) shared.color else shared.theme.accent
                val link = LinkAnnotation.Clickable(
                    tag = span.href,
                    styles = TextLinkStyles(style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
                    linkInteractionListener = { shared.onLink(span.href) },
                )
                withLink(link) { appendSpans(span.children, shared) }
            }
        }
    }
}

/** One picture from a message body, loaded without an image library. */
@Composable
private fun InlineImage(url: String, alt: String, theme: SupportTheme) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, url) {
        value = withContext(Dispatchers.IO) {
            try {
                URL(url).openStream().use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
            } catch (ignored: Exception) {
                null
            }
        }
    }
    val image = bitmap
    val frame = Modifier.padding(vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp))
    if (image == null || image.height == 0) {
        Box(frame.height(120.dp).background(theme.border))
        return
    }
    Image(
        bitmap = image,
        contentDescription = alt.ifEmpty { null },
        contentScale = ContentScale.Fit,
        modifier = frame.heightIn(max = 260.dp).aspectRatio(image.width.toFloat() / image.height),
    )
}
