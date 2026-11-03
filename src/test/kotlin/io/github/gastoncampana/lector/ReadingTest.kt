package io.github.gastoncampana.lector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingTest {
    @Test
    fun piecesBreakAtTheLastSpaceBeforeTheLimit() {
        assertEquals(listOf("uno dos", "tres"), pieces("uno dos tres", 8))
        assertEquals(listOf("corto"), pieces("corto", 8))
        assertEquals(emptyList<String>(), pieces("", 8))
    }

    @Test
    fun piecesCutAWordLongerThanTheLimit() {
        assertEquals(listOf("abcd", "efgh", "ij"), pieces("abcdefghij", 4))
    }

    @Test
    fun piecesNeverPassTheLimitOrLoseText() {
        val text = (1..500).joinToString(" ") { "palabra$it" }
        val parts = pieces(text, 100)
        assertTrue(parts.all { it.length <= 100 })
        assertEquals(text, parts.joinToString(" "))
    }

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
}
