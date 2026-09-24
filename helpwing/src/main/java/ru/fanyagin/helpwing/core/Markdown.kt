package ru.fanyagin.helpwing.core

// Markdown parsed into plain data. A port of react-native-sdk/src/core/markdown.ts;
// the renderers of this subset change together. No HTML is produced anywhere.

sealed interface Inline {
    data class Text(val text: String) : Inline
    data object Break : Inline
    data class Code(val text: String) : Inline
    data class Strong(val children: List<Inline>) : Inline
    data class Em(val children: List<Inline>) : Inline
    data class Strike(val children: List<Inline>) : Inline
    data class Link(val href: String, val children: List<Inline>) : Inline

    /** A picture that came with the message, named by its attachment's `content_id`. Never a URL. */
    data class Image(val cid: String, val alt: String) : Inline
}

enum class Align { LEFT, CENTER, RIGHT }

data class Cell(val spans: List<Inline>, val align: Align)

sealed interface Block {
    data class Paragraph(val spans: List<Inline>) : Block
    data class Heading(val level: Int, val spans: List<Inline>) : Block
    data class Code(val language: String, val text: String) : Block
    data class Quote(val blocks: List<Block>) : Block
    data class ListBlock(val ordered: Boolean, val start: Int, val items: List<List<Block>>) : Block
    data class Table(val head: List<Cell>, val rows: List<List<Cell>>) : Block
    data object Rule : Block
}

object Markdown {
    private val FENCE = Regex("""^ {0,3}(```|~~~)[ \t]*([^\s`]*)""")
    private val HEADING = Regex("""^ {0,3}(#{1,6})[ \t]+(.*?)[ \t]*#*[ \t]*$""")
    private val RULE = Regex("""^ {0,3}([-*_])[ \t]*(?:\1[ \t]*){2,}$""")
    private val QUOTE = Regex("""^ {0,3}>[ \t]?""")
    private val ITEM = Regex("""^( *)([-*+]|\d{1,9}[.)])[ \t]+(.*)$""")
    private val DIVIDER = Regex("""^ {0,3}\|?[ \t]*:?-+:?[ \t]*(\|[ \t]*:?-+:?[ \t]*)*\|?[ \t]*$""")

    /** Schemes a link may carry. Anything else, `javascript:` above all, is not a link. */
    private val SAFE_HREF = Regex("""^(?:https?://|mailto:|tel:)\S+$""", RegexOption.IGNORE_CASE)
    private val BARE_URL = Regex("""^(?:https?://|www\.)[^\s<>\[\]()]*[^\s<>\[\]().,;:!?'"]""", RegexOption.IGNORE_CASE)
    private val EMAIL = Regex("""^[^\s@<>]+@[^\s@<>]+\.[a-z]{2,}$""", RegexOption.IGNORE_CASE)
    private val WWW = Regex("""^www\.\S+$""", RegexOption.IGNORE_CASE)
    private val ESCAPABLE = Regex("""[\\`*_{}\[\]()#+\-.!|~>]""")
    private val CODE_SPAN = Regex("""^(`+)([^`][\s\S]*?)\1(?!`)""")
    private val LINK = Regex("""^(!?)\[([^\]\[]*)\]\([ \t]*<?([^\s)]*)>?(?:[ \t]+"[^"]*")?[ \t]*\)""")
    private val AUTOLINK = Regex("""^<((?:https?://|mailto:)[^\s>]+)>""", RegexOption.IGNORE_CASE)
    private val CID = Regex("""^cid:(\S+)$""", RegexOption.IGNORE_CASE)
    private val WORD = Regex("""\w""")
    private val URL_NEIGHBOUR = Regex("""[\w@/.]""")
    private val DIGIT = Regex("""\d""")

    private class Run(val pattern: Regex, val kind: String, val wordish: Boolean)

    private val RUNS = listOf(
        Run(Regex("""^\*\*(\S|\S[\s\S]*?\S)\*\*"""), "strong", false),
        Run(Regex("""^__(\S|\S[\s\S]*?\S)__"""), "strong", true),
        Run(Regex("""^~~(\S|\S[\s\S]*?\S)~~"""), "strike", false),
        Run(Regex("""^\*(\S|\S[\s\S]*?\S)\*"""), "em", false),
        Run(Regex("""^_(\S|\S[\s\S]*?\S)_"""), "em", true),
    )

    /** Markdown source as blocks. Empty source is no blocks. */
    fun parse(source: String?): List<Block> {
        if (source.isNullOrBlank()) return emptyList()
        val lines = source.replace(Regex("\r\n?"), "\n").replace("\t", "    ").split("\n")
        return blocks(lines)
    }

    /** A destination worth linking, or null. Bare addresses and `www.` hosts are promoted. */
    fun safeHref(raw: String): String? {
        val url = raw.trim()
        if (url.isEmpty()) return null
        if (SAFE_HREF.matches(url)) return url
        if (EMAIL.matches(url)) return "mailto:$url"
        if (WWW.matches(url)) return "https://$url"
        return null
    }

    /** A bare URL keeps its trailing bracket only when the text opened one. */
    private fun trimUrl(url: String): String {
        var end = url.length
        while (end > 0 && url[end - 1] == ')') {
            val slice = url.substring(0, end)
            if (slice.count { it == '(' } >= slice.count { it == ')' }) break
            end--
        }
        return url.substring(0, end)
    }

    private fun spansOf(source: String, linkable: Boolean = true): List<Inline> {
        val spans = mutableListOf<Inline>()
        val plain = StringBuilder()
        var index = 0

        fun flush() {
            if (plain.isNotEmpty()) spans.add(Inline.Text(plain.toString()))
            plain.setLength(0)
        }

        while (index < source.length) {
            val char = source[index]
            val rest = source.substring(index)
            val before = if (index > 0) source[index - 1].toString() else ""

            if (char == '\\' && index + 1 < source.length && ESCAPABLE.matches(source[index + 1].toString())) {
                plain.append(source[index + 1])
                index += 2
                continue
            }

            if (char == '\n') {
                flush()
                spans.add(Inline.Break)
                index++
                continue
            }

            // Code first: everything inside it is literal.
            if (char == '`') {
                val code = CODE_SPAN.find(rest)
                if (code != null) {
                    flush()
                    spans.add(Inline.Code(code.groupValues[2].replace('\n', ' ').trim()))
                    index += code.value.length
                    continue
                }
            }

            if (char == '[' || char == '!') {
                val link = LINK.find(rest)
                if (link != null && (char == '[' || link.groupValues[1] == "!")) {
                    val image = link.groupValues[1] == "!"
                    val label = link.groupValues[2]
                    val target = link.groupValues[3]
                    val cid = if (image) CID.find(target) else null
                    val href = safeHref(target)
                    flush()
                    // A remote image becomes a link to it: in a support thread it is a tracking pixel.
                    when {
                        cid != null -> spans.add(Inline.Image(cid.groupValues[1], label))
                        href == null -> spans.addAll(spansOf(label, linkable))
                        image -> spans.add(Inline.Link(href, listOf(Inline.Text(label.ifEmpty { href }))))
                        else -> spans.add(Inline.Link(href, spansOf(label, false)))
                    }
                    index += link.value.length
                    continue
                }
            }

            if (char == '<') {
                val auto = AUTOLINK.find(rest)
                if (auto != null) {
                    val href = safeHref(auto.groupValues[1])
                    flush()
                    if (href != null) {
                        spans.add(Inline.Link(href, listOf(Inline.Text(auto.groupValues[1]))))
                    } else {
                        plain.append(auto.value)
                    }
                    index += auto.value.length
                    continue
                }
            }

            if (char == '*' || char == '_' || char == '~') {
                val run = RUNS.firstOrNull { candidate ->
                    val match = candidate.pattern.find(rest) ?: return@firstOrNull false
                    // `snake_case` is one word: underscore forms only stand at word boundaries.
                    if (!candidate.wordish) return@firstOrNull true
                    val after = rest.substring(match.value.length).take(1)
                    !WORD.containsMatchIn(before) && !WORD.containsMatchIn(after)
                }
                if (run != null) {
                    val match = run.pattern.find(rest)!!
                    flush()
                    val children = spansOf(match.groupValues[1], linkable)
                    spans.add(
                        when (run.kind) {
                            "strong" -> Inline.Strong(children)
                            "strike" -> Inline.Strike(children)
                            else -> Inline.Em(children)
                        },
                    )
                    index += match.value.length
                    continue
                }
            }

            if (linkable && (char == 'h' || char == 'w' || char == 'H' || char == 'W') &&
                !URL_NEIGHBOUR.containsMatchIn(before)
            ) {
                val bare = BARE_URL.find(rest)
                if (bare != null) {
                    val text = trimUrl(bare.value)
                    val href = safeHref(text)
                    if (href != null) {
                        flush()
                        spans.add(Inline.Link(href, listOf(Inline.Text(text))))
                        index += text.length
                        continue
                    }
                }
            }

            plain.append(char)
            index++
        }

        flush()
        return spans
    }

    private fun indentOf(line: String): Int = line.length - line.trimStart().length

    /** Whether a line starts something of its own, and so cannot continue a paragraph. */
    private fun opensBlock(line: String): Boolean =
        FENCE.containsMatchIn(line) || HEADING.containsMatchIn(line) || RULE.containsMatchIn(line) ||
            QUOTE.containsMatchIn(line) || ITEM.containsMatchIn(line)

    private fun cells(row: String): List<String> {
        val trimmed = row.trim().removePrefix("|").removeSuffix("|")
        val out = mutableListOf<String>()
        val current = StringBuilder()
        var index = 0
        while (index < trimmed.length) {
            val char = trimmed[index]
            if (char == '\\' && index + 1 < trimmed.length && trimmed[index + 1] == '|') {
                current.append('|')
                index += 2
                continue
            }
            if (char == '|') {
                out.add(current.toString().trim())
                current.setLength(0)
            } else {
                current.append(char)
            }
            index++
        }
        out.add(current.toString().trim())
        return out
    }

    private fun alignments(divider: String): List<Align> = cells(divider).map { cell ->
        val left = cell.startsWith(":")
        val right = cell.endsWith(":")
        when {
            left && right -> Align.CENTER
            right -> Align.RIGHT
            else -> Align.LEFT
        }
    }

    /** One list, from its first marker to the first line no longer part of it. */
    private fun list(lines: List<String>, from: Int): Pair<Block, Int> {
        val first = ITEM.find(lines[from])!!
        val ordered = DIGIT.containsMatchIn(first.groupValues[2])
        val indent = first.groupValues[1].length
        val items = mutableListOf<List<Block>>()
        var index = from

        while (index < lines.size) {
            val marker = ITEM.find(lines[index])
            // A deeper marker belongs to the item above; a shallower one or a switch of kind ends the list.
            if (marker == null || marker.groupValues[1].length != indent ||
                ordered != DIGIT.containsMatchIn(marker.groupValues[2])
            ) {
                break
            }

            val content = marker.groupValues[1].length + marker.groupValues[2].length + 1
            val item = mutableListOf(marker.groupValues[3])
            index++

            while (index < lines.size) {
                val line = lines[index]
                if (line.isBlank()) {
                    val after = lines.getOrNull(index + 1)
                    if (after == null || after.isBlank() || (indentOf(after) < content && !ITEM.containsMatchIn(after))) break
                    item.add("")
                    index++
                    continue
                }
                if (indentOf(line) >= content) {
                    item.add(line.substring(content))
                    index++
                    continue
                }
                if (opensBlock(line)) break
                item.add(line.trim()) // A wrapped line still belongs to the item's paragraph.
                index++
            }

            items.add(blocks(item))
        }

        val start = if (ordered) (first.groupValues[2].takeWhile { it.isDigit() }.toIntOrNull() ?: 1) else 1
        return Block.ListBlock(ordered, start, items) to index
    }

    private fun blocks(lines: List<String>): List<Block> {
        val out = mutableListOf<Block>()
        var index = 0

        while (index < lines.size) {
            val line = lines[index]

            if (line.isBlank()) {
                index++
                continue
            }

            val fence = FENCE.find(line)
            if (fence != null) {
                val closes = Regex("^ {0,3}" + Regex.escape(fence.groupValues[1]) + "[ \\t]*$")
                val body = mutableListOf<String>()
                index++
                while (index < lines.size && !closes.containsMatchIn(lines[index])) body.add(lines[index++])
                index++ // The closing fence, or the end of the source.
                out.add(Block.Code(fence.groupValues[2], body.joinToString("\n")))
                continue
            }

            val heading = HEADING.find(line)
            if (heading != null) {
                out.add(Block.Heading(heading.groupValues[1].length, spansOf(heading.groupValues[2])))
                index++
                continue
            }

            if (RULE.containsMatchIn(line)) {
                out.add(Block.Rule)
                index++
                continue
            }

            if (QUOTE.containsMatchIn(line)) {
                val quoted = mutableListOf<String>()
                while (index < lines.size) {
                    val next = lines[index]
                    if (QUOTE.containsMatchIn(next)) {
                        quoted.add(QUOTE.replaceFirst(next, ""))
                    } else if (next.isNotBlank() && !opensBlock(next)) {
                        quoted.add(next) // Wrapped, still quoted.
                    } else {
                        break
                    }
                    index++
                }
                out.add(Block.Quote(blocks(quoted)))
                continue
            }

            if (ITEM.containsMatchIn(line)) {
                val (block, next) = list(lines, index)
                out.add(block)
                index = next
                continue
            }

            val divider = lines.getOrNull(index + 1)
            if (line.contains('|') && divider != null && DIVIDER.containsMatchIn(divider) && divider.contains('-')) {
                val align = alignments(divider)
                val head = cells(line).mapIndexed { column, cell -> Cell(spansOf(cell), align.getOrElse(column) { Align.LEFT }) }
                val rows = mutableListOf<List<Cell>>()
                index += 2
                while (index < lines.size && lines[index].isNotBlank() && lines[index].contains('|')) {
                    rows.add(cells(lines[index]).mapIndexed { column, cell -> Cell(spansOf(cell), align.getOrElse(column) { Align.LEFT }) })
                    index++
                }
                out.add(Block.Table(head, rows))
                continue
            }

            val paragraph = mutableListOf(line)
            index++
            while (index < lines.size && lines[index].isNotBlank() && !opensBlock(lines[index])) paragraph.add(lines[index++])
            out.add(Block.Paragraph(spansOf(paragraph.joinToString("\n").trim())))
        }

        return out
    }
}
