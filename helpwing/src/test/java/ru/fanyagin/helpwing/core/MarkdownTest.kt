package ru.fanyagin.helpwing.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The same cases the dashboard and the React Native SDK run against their parsers. */
class MarkdownTest {
    private fun text(nodes: List<Any>): String = nodes.joinToString("") { node ->
        when (node) {
            is Block.Paragraph -> text(node.spans)
            is Block.Heading -> text(node.spans)
            is Block.Quote -> text(node.blocks)
            is Block.ListBlock -> node.items.joinToString(" ") { text(it) }
            is Block.Code -> node.text
            is Inline.Text -> node.text
            is Inline.Code -> node.text
            is Inline.Break -> "\n"
            is Inline.Strong -> text(node.children)
            is Inline.Em -> text(node.children)
            is Inline.Strike -> text(node.children)
            is Inline.Link -> text(node.children)
            else -> ""
        }
    }

    private fun spans(source: String): List<Inline> = (Markdown.parse(source).first() as Block.Paragraph).spans

    @Test
    fun emptyDraftRendersNothing() {
        assertTrue(Markdown.parse("").isEmpty())
        assertTrue(Markdown.parse("   \n\n ").isEmpty())
        assertTrue(Markdown.parse(null).isEmpty())
    }

    @Test
    fun singleNewlineIsABreak() {
        val kinds = spans("Hi Rajiv,\nThanks for writing in.").map { it::class.simpleName }
        assertEquals(listOf("Text", "Break", "Text"), kinds)
    }

    @Test
    fun emphasisKeepsTheWordsAroundIt() {
        val spans = spans("**no mail is being held.** What we *can* do is enable a domain.")
        assertTrue(spans[0] is Inline.Strong)
        assertEquals("no mail is being held. What we can do is enable a domain.", text(spans))
        assertEquals(1, spans.count { it is Inline.Em })
    }

    @Test
    fun underscoreInsideAWordIsLeftAlone() {
        assertTrue(spans("the field is body_text_html here").all { it is Inline.Text })
    }

    @Test
    fun listMarkersAndStartNumber() {
        val bullets = Markdown.parse("- the From name is wrong\n- the footer is wrong")
        assertFalse((bullets[0] as Block.ListBlock).ordered)
        assertEquals("the From name is wrong the footer is wrong", text(bullets))

        val numbered = Markdown.parse("2. The agreement\n3. Written confirmation")[0] as Block.ListBlock
        assertTrue(numbered.ordered)
        assertEquals(2, numbered.start)
    }

    @Test
    fun nestedListBelongsToItsItem() {
        val list = Markdown.parse("- outer\n  - inner\n- second")[0] as Block.ListBlock
        assertEquals(2, list.items.size)
        assertTrue(list.items[0][0] is Block.Paragraph)
        assertTrue(list.items[0][1] is Block.ListBlock)
    }

    @Test
    fun headingsQuotesRulesAndFences() {
        val blocks = Markdown.parse("## On the review\n\n> quoted line\n\n---\n\n```json\n{\"a\": 1}\n```")
        assertEquals(listOf("Heading", "Quote", "Rule", "Code"), blocks.map { it::class.simpleName })
        assertEquals(2, (blocks[0] as Block.Heading).level)
        assertEquals(Block.Code("json", "{\"a\": 1}"), blocks[3])
    }

    @Test
    fun markersInsideCodeAreLiteral() {
        val spans = spans("the domain is `quickbooks-enterprises.com` **and** it is blocked")
        assertEquals(Inline.Code("quickbooks-enterprises.com"), spans[1])
        assertTrue(spans.any { it is Inline.Strong })
    }

    @Test
    fun linksAndBareAddresses() {
        val spans = spans("I'd point you to the [brand use guide](https://quickbooks.intuit.com/help) instead.")
        assertEquals("https://quickbooks.intuit.com/help", (spans[1] as Inline.Link).href)
        assertEquals("I'd point you to the brand use guide instead.", text(spans))

        assertTrue(spans("One went to batkinson@occaps.com, a third party.").none { it is Inline.Link })
    }

    @Test
    fun autolinkStopsBeforeTheSentenceDoes() {
        val link = spans("See https://helpwing.app/docs (the setup page).").first { it is Inline.Link } as Inline.Link
        assertEquals("https://helpwing.app/docs", link.href)
    }

    @Test
    fun pipeTableWithAlignments() {
        val table = Markdown.parse("| Entity | Domain |\n| --- | ---: |\n| QB Enterprise | .com |")[0] as Block.Table
        assertEquals(listOf("Entity", "Domain"), table.head.map { text(it.spans) })
        assertEquals(Align.RIGHT, table.head[1].align)
        assertEquals(1, table.rows.size)
    }

    @Test
    fun cidImageIsTheAttachedFile() {
        assertEquals(Inline.Image("shot-1@northwind", "the zone file"), spans("![the zone file](cid:shot-1@northwind)")[0])
    }

    @Test
    fun remoteImageIsOnlyALink() {
        assertEquals("https://tracker.example/open.gif", (spans("![](https://tracker.example/open.gif)")[0] as Inline.Link).href)
    }

    @Test
    fun escapedMarkerStaysACharacter() {
        val spans = spans("a literal \\*asterisk\\* stays")
        assertEquals("a literal *asterisk* stays", text(spans))
        assertTrue(spans.none { it is Inline.Em })
    }

    @Test
    fun safeHrefTakesLegitimateSchemes() {
        assertEquals("https://helpwing.app", Markdown.safeHref("https://helpwing.app"))
        assertEquals("mailto:support@helpwing.app", Markdown.safeHref("mailto:support@helpwing.app"))
        assertEquals("mailto:support@helpwing.app", Markdown.safeHref("support@helpwing.app"))
        assertEquals("https://www.helpwing.app", Markdown.safeHref("www.helpwing.app"))
    }

    @Test
    fun safeHrefRefusesCode() {
        assertNull(Markdown.safeHref("javascript:alert(1)"))
        assertNull(Markdown.safeHref("JavaScript:alert(1)"))
        assertNull(Markdown.safeHref("data:text/html;base64,PHNjcmlwdD4="))
        assertNull(Markdown.safeHref(""))
    }

    @Test
    fun unsafeLinkIsPrintedAsItsWords() {
        val spans = spans("[click me](javascript:alert(1))")
        assertTrue(spans.none { it is Inline.Link })
        assertTrue(text(spans).contains("click me"))
    }

    @Test
    fun htmlFallbackReadsAsText() {
        assertEquals("Hello\nWorld & <you>", HtmlText.of("<p>Hello</p><div>World &amp; &lt;you&gt;</div>"))
    }
}
