package io.github.gastoncampana.lector

/**
 * A piece of on-screen text and where it sits, in screen pixels. [parent] and [grandparent] identify the nodes that hold
 * it: a paragraph holds its plain text directly, and its links' text through the link. [link] is text inside a link.
 */
data class Block(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val parent: Int = 0,
    val grandparent: Int = 0,
    val link: Boolean = false,
) {
    fun contains(x: Int, y: Int) = x in left until right && y in top until bottom
    val area get() = (right - left).toLong() * (bottom - top)
}

/** The index of the block under a touch, or -1. The smallest wins: a block's bounds can hold others (web pages). */
fun blockAt(blocks: List<Block>, x: Int, y: Int): Int =
    blocks.indices.filter { blocks[it].contains(x, y) }.minByOrNull { blocks[it].area } ?: -1

/**
 * The blocks from [from] to the end, grouped into runs the voice reads in one go: the pieces of a paragraph split by its
 * links. Read one by one, they would make the voice pause at every link.
 */
fun runs(blocks: List<Block>, from: Int): List<IntRange> {
    if (from !in blocks.indices) return emptyList()
    val out = mutableListOf<IntRange>()
    var start = from
    for (i in from + 1 until blocks.size) {
        if (!together(blocks[i - 1], blocks[i])) {
            out += start until i
            start = i
        }
    }
    out += start until blocks.size
    return out
}

/**
 * Whether [b] goes on reading [a]: one holds the other or they share a holder (a paragraph and its links), or [b] sits on
 * [a]'s last line (a footnote mark). Overlapping lines, not just [b] starting higher: a column beside [a] starts higher.
 */
private fun together(a: Block, b: Block) =
    b.parent == a.parent || b.parent == a.grandparent || b.grandparent == a.parent ||
        (b.top < a.bottom && a.top < b.bottom)

/**
 * [text] in clauses, cut after every comma, semicolon, colon or sentence end; a piece under three words goes with the
 * next one, or the last. The voice makes a whole piece before any of it sounds: short pieces start sooner, and the next
 * one is made while this one plays.
 */
fun clauses(text: String): List<String> {
    val out = mutableListOf<String>()
    var start = 0
    for (cut in Regex("[,;:.!?]\\s").findAll(text).map { it.range.first + 1 }) {
        val piece = text.substring(start, cut).trim()
        if (words(piece) < 3) continue
        out += piece
        start = cut
    }
    val rest = text.substring(start).trim()
    if (out.isNotEmpty() && words(rest) < 3) out[out.lastIndex] += " $rest" else if (rest.isNotEmpty()) out += rest
    return out
}

private fun words(text: String) = text.split(Regex("\\s+")).count { it.isNotEmpty() }

enum class Language { SPANISH, ENGLISH }

// Frequent words of one language that the other doesn't use, so "a", "no" or "me" count for neither.
private val englishWords = setOf(
    "the", "and", "is", "are", "was", "were", "you", "your", "of", "to", "in", "it", "its", "that", "this", "for",
    "with", "on", "at", "from", "by", "be", "been", "have", "has", "had", "not", "but", "or", "if", "so", "what",
    "who", "how", "why", "when", "i", "i'm", "we", "they", "he", "she", "my", "our", "their", "will", "would", "can",
    "just", "about", "all", "more", "out", "up", "do", "does", "don't", "get", "like", "now", "there", "an", "which",
)
private val spanishWords = setOf(
    "el", "la", "los", "las", "de", "del", "al", "que", "y", "en", "un", "una", "unos", "es", "son", "por", "con",
    "para", "se", "lo", "le", "les", "como", "pero", "su", "sus", "mi", "tu", "ya", "muy", "hay", "esto", "esta",
    "este", "eso", "ese", "fue", "ser", "nos", "cuando", "porque", "todo", "sin", "sobre", "entre", "yo", "vos",
    "si", "dónde", "qué", "más", "está", "también", "así", "o",
)

/**
 * Whether [text] reads as English or Spanish, or null when nothing tells (a name, a number, a handle). Counts each
 * language's frequent words; a letter only Spanish uses (ñ, an accent, ¿ or ¡) counts for Spanish too.
 */
fun language(text: String): Language? {
    val words = text.lowercase().split(Regex("[^\\p{L}']+")).filter { it.isNotEmpty() }
    val english = words.count { it in englishWords }
    val spanish = words.count { it in spanishWords } + text.count { it in "ñÑáéíóúÁÉÍÓÚ¿¡" }
    return when {
        english > spanish -> Language.ENGLISH
        spanish > english -> Language.SPANISH
        else -> null
    }
}

/**
 * [blocks] without the runs made only of links, such as a page's tabs or an icon's hidden label: what is left is the
 * text people read, links within it included.
 */
fun readable(blocks: List<Block>): List<Block> =
    runs(blocks, 0).filter { run -> run.any { !blocks[it].link } }.flatMap { run -> run.map { blocks[it] } }
