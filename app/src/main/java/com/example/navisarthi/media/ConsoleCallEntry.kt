package com.example.navisarthi.media

import com.example.navisarthi.dash.protocol.CallHistoryCategory

/** Phone numbers remain in memory and never appear in diagnostic messages. */
data class ConsoleCallEntry(val label: String, val number: String?)

internal fun consoleDialNumber(raw: String): String? {
    if (raw.any { it !in '0'..'9' && it !in "+ ()-." }) return null
    val number = raw.filter { it in '0'..'9' || it == '+' }
    return number.takeIf { it.matches(Regex("\\+?[0-9]{1,20}")) }
}

/** A row is callable only after that exact snapshot has finished transferring. */
internal class ConsoleDialSnapshot {
    private var category: CallHistoryCategory? = null
    private var rows = emptyList<ConsoleCallEntry>()
    private var lastDialAt: Long? = null
    fun clear() { category = null; rows = emptyList() }
    fun displayed(category: CallHistoryCategory, rows: List<ConsoleCallEntry>) {
        this.category = category
        this.rows = rows.take(5).toList()
    }
    fun number(category: CallHistoryCategory, index: Int, now: Long): String? {
        if (this.category != category || index !in 0..4 || lastDialAt?.let { now - it < 10_000 } == true) return null
        return rows.getOrNull(index)?.number?.let(::consoleDialNumber)
    }
    fun dialRequested(now: Long) { lastDialAt = now }
}
