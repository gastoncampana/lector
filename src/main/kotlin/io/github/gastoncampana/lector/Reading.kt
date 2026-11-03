package io.github.gastoncampana.lector

/** A piece of on-screen text and where it sits, in screen pixels. */
data class Block(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int) {
    fun contains(x: Int, y: Int) = x in left until right && y in top until bottom
    val area get() = (right - left).toLong() * (bottom - top)
}

/** The index of the block under a touch, or -1. The smallest wins: a block's bounds can hold others (web pages). */
fun blockAt(blocks: List<Block>, x: Int, y: Int): Int =
    blocks.indices.filter { blocks[it].contains(x, y) }.minByOrNull { blocks[it].area } ?: -1

/** [text] cut into pieces of at most [max] characters, each at the last space before the limit when there is one. */
fun pieces(text: String, max: Int): List<String> {
    val out = mutableListOf<String>()
    var rest = text
    while (rest.length > max) {
        val cut = rest.lastIndexOf(' ', max).takeIf { it > 0 } ?: max
        out += rest.substring(0, cut)
        rest = rest.substring(cut).trimStart()
    }
    if (rest.isNotEmpty()) out += rest
    return out
}
