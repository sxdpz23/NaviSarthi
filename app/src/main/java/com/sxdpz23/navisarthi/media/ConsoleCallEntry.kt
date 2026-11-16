package com.sxdpz23.navisarthi.media

import com.sxdpz23.navisarthi.dash.protocol.CallHistoryCategory

/** Phone numbers remain in memory and never appear in diagnostic messages. */
data class ConsoleCallEntry(val label: String, val number: String?)

internal fun consoleDialNumber(raw: String): String? {
    if (raw.any { it !in '0'..'9' && it !in "+ ()-." }) return null
    val number = raw.filter { it in '0'..'9' || it == '+' }
    return number.takeIf { it.matches(Regex("\\+?[0-9]{1,20}")) }
}

/** Normalize identity only; never change the number used to dial a displayed row. */
internal fun consoleNumberKey(number: String?, canonical: String? = null): String? {
    val key = canonical?.let(::consoleDialNumber) ?: number?.let(::consoleDialNumber) ?: return null
    return if (key.startsWith("00") && key.length > 2) "+${key.drop(2)}" else key
}

/** Input order is authoritative: history arrives newest-first; favorites use contact order. */
internal class UniqueConsoleCalls {
    private val rows = mutableListOf<ConsoleCallEntry>()
    private val numbers = mutableSetOf<String>()
    val full get() = rows.size == 5
    fun add(entry: ConsoleCallEntry, canonicalNumber: String? = null): Boolean {
        if (full) return false
        val key = entry.number?.let { consoleNumberKey(it, canonicalNumber) }
        if (key != null && !numbers.add(key)) return false
        rows += entry
        return true
    }
    fun entries(): List<ConsoleCallEntry> = rows.toList()
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
