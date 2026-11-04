package io.github.gastoncampana.lector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingTest {
    @Test
    fun blockAtPicksTheSmallestBlockUnderTheTouch() {
        val page = Block("página", 0, 0, 1000, 2000)
        val line = Block("línea", 100, 100, 900, 150)
        assertEquals(1, blockAt(listOf(page, line), 500, 120))
        assertEquals(0, blockAt(listOf(page, line), 500, 500))
        assertEquals(-1, blockAt(listOf(page, line), 1500, 500))
    }

    @Test
    fun blockBoundsExcludeTheirRightAndBottomEdges() {
        val block = Block("x", 10, 10, 20, 20)
        assertTrue(block.contains(10, 10))
        assertFalse(block.contains(20, 15))
        assertFalse(block.contains(15, 20))
    }

    @Test
    fun runsJoinThePiecesOfAParagraphButNotSeparateParagraphs() {
        val title = Block("Mate", 0, 0, 500, 80, parent = 1)
        val intro = Block("Una infusión", 0, 100, 900, 160, parent = 2)
        val start = Block("es una infusión a base de hojas de yerba mate (", 0, 200, 900, 300, parent = 3)
        // A footnote mark in its own node, on the line the first piece ends on.
        val mark = Block("[2]", 0, 250, 50, 290, parent = 4, grandparent = 9)
        val rest = Block(") planta originaria de los ríos", 50, 250, 900, 350, parent = 3)
        // A link: its text hangs from the link, the link from the paragraph. It starts the next line.
        val link = Block("Paraguay", 0, 350, 300, 400, parent = 5, grandparent = 3)
        val comma = Block(",", 300, 350, 320, 400, parent = 3)
        val next = Block("Otro párrafo.", 0, 450, 900, 500, parent = 6)
        val page = listOf(title, intro, start, mark, rest, link, comma, next)
        assertEquals(listOf(0..0, 1..1, 2..6, 7..7), runs(page, 0))
        assertEquals(listOf(5..6, 7..7), runs(page, 5))
        assertEquals(emptyList<IntRange>(), runs(page, 8))
    }

    @Test
    fun runsKeepSideBySideColumnsApart() {
        val left = Block("End of the left column.", 0, 800, 450, 900, parent = 1)
        // Starts above where the left column ends, but not on its line.
        val right = Block("Start of the right column.", 500, 100, 900, 200, parent = 2)
        assertEquals(listOf(0..0, 1..1), runs(listOf(left, right), 0))
    }

    @Test
    fun languageTellsEnglishFromSpanish() {
        assertEquals(Language.ENGLISH, language("Just shipped the new version, check it out"))
        assertEquals(Language.ENGLISH, language("I can't believe this is real"))
        assertEquals(Language.SPANISH, language("El mate es una infusión a base de hojas de yerba"))
        assertEquals(Language.SPANISH, language("Mañana llueve"))
        assertEquals(Language.SPANISH, language("¿Venís?"))
    }

    @Test
    fun languageIsUnknownWithoutClues() {
        assertEquals(null, language("@gaston · 2 h"))
        assertEquals(null, language("1.234"))
        assertEquals(null, language(""))
    }

    @Test
    fun readableDropsRunsOfOnlyLinksButKeepsLinksInText() {
        val tabs = listOf(
            Block("Article", 0, 0, 100, 40, parent = 1, link = true),
            Block("Talk", 120, 0, 200, 40, parent = 2, link = true),
        )
        val paragraph = listOf(
            Block("It is popular in", 0, 100, 400, 150, parent = 3),
            Block("South America", 400, 100, 700, 150, parent = 4, grandparent = 3, link = true),
        )
        val icon = Block("Download PDF", 800, 200, 868, 250, parent = 5, link = true)
        assertEquals(paragraph, readable(tabs + paragraph + icon))
    }
}
