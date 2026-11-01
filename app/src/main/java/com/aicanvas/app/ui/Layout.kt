package com.aicanvas.app.ui

import org.json.JSONObject
import com.aicanvas.app.data.Element

// How cards are sized and packed into rows. Pure functions, so they are unit-tested.
// ---------------------------------------------------------------- elements

/** Width units a card asks for (the screen is 6 units wide), never narrower than its content needs. */
internal fun spanOf(e: Element): Int {
    // A folded card is a portrait tile, two to a row.
    if (isFolded(e)) return 3
    // A big card the user opened takes the full width and grows as tall as its content needs.
    if (e.expanded && !com.aicanvas.app.data.CardSummary.isSmall(e.ui)) return 6
    val asked = when (e.size) {
        "third" -> 2
        "half" -> 3
        "twothirds" -> 4
        else -> 6
    }
    return maxOf(asked, minSpan(e.ui))
}

/** A big card the user opened: it spans the whole row in its own place. */
internal fun isOpenedFull(e: Element) = e.expanded && !com.aicanvas.app.data.CardSummary.isSmall(e.ui)

/** A big card the user hasn't opened: shown as a tile with its title and key lines. */
internal fun isFolded(e: Element) = !e.expanded && !com.aicanvas.app.data.CardSummary.isSmall(e.ui)

/**
 * The narrowest a card can be and still read well, whatever width the AI asked for: charts, inputs, timers and
 * rows of three things need room; counters, two-part rows, long lines and to-do lists need at least half.
 */
internal fun minSpan(ui: JSONObject): Int {
    var min = 2
    var checks = 0
    fun walk(n: JSONObject) {
        val children = n.optJSONArray("children")
        when (n.optString("type")) {
            "chart", "input", "timer" -> min = maxOf(min, 4)
            "counter" -> min = maxOf(min, 3)
            "check" -> checks++
            "row" -> min = maxOf(min, if ((children?.length() ?: 0) >= 3) 4 else if ((children?.length() ?: 0) == 2) 3 else 2)
            "text" -> if (n.optString("text").length > 28) min = maxOf(min, 3)
        }
        if (children != null) for (i in 0 until children.length()) children.optJSONObject(i)?.let(::walk)
    }
    walk(ui)
    if (checks > 0) min = maxOf(min, 3)
    if (checks >= 4) min = 6
    return min
}

/**
 * Lays cards out in rows, in importance order. A gap at the end of a row is filled with one of the next few
 * cards that fits; if none fits, the last card in the row stretches to fill it. So there are never holes.
 * Each card comes with the width units it takes.
 */
internal fun packRows(list: List<Element>): List<List<Pair<Element, Int>>> {
    val rest = list.toMutableList()
    val rows = mutableListOf<List<Pair<Element, Int>>>()
    var row = mutableListOf<Pair<Element, Int>>()
    var used = 0
    fun closeRow() {
        if (row.isEmpty()) return
        // The last card stretches to fill the row; a folded tile only when it's alone (no empty half beside it).
        if (used < 6 && (row.size == 1 || !isFolded(row.last().first))) row[row.lastIndex] = row.last().let { it.first to it.second + 6 - used }
        rows += row
        row = mutableListOf()
        used = 0
    }
    while (rest.isNotEmpty()) {
        // An opened card stays where it was: it takes the next full row, and later cards never jump ahead of it.
        val window = rest.take(4).let { w -> if (isOpenedFull(w.first())) w.take(1) else w.takeWhile { !isOpenedFull(it) } }
        val next = window.firstOrNull { used + spanOf(it) <= 6 }
        if (next == null) { closeRow(); continue }
        rest.remove(next)
        row += next to spanOf(next)
        used += spanOf(next)
        if (used >= 6) closeRow()
    }
    closeRow()
    return rows
}

/** Spans per row, for tests outside this package. */
fun packRowsForTest(list: List<Element>): List<List<Int>> = packRows(list).map { r -> r.map { it.second } }
