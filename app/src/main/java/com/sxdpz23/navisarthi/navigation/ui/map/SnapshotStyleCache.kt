package com.sxdpz23.navisarthi.navigation.ui.map

import java.io.File

/** Keep the selected Nav style available when the console Wi-Fi has no internet. */
internal class SnapshotStyleCache(private val directory: File) {
    fun load(key: String, effective: String, uri: String, fetch: (String) -> String?): String? {
        val stored = File(directory, "$key.json")
        fun read(file: File): String? = runCatching { file.readText().takeIf { it.isNotBlank() } }.getOrNull()
        val patched = effective.takeIf { it.startsWith("file://") }?.let { read(File(it.removePrefix("file://"))) }
        val cached = read(stored)
        // Live tile snapshot paths rotate. Refresh aged styles when online; keep the last
        // usable specification when console Wi-Fi is the only network available.
        val fresh = cached?.takeIf { System.currentTimeMillis() - stored.lastModified() < 7L * 24 * 60 * 60 * 1000 }
        val json = patched ?: fresh ?: fetch(uri)?.takeIf { it.isNotBlank() } ?: cached ?: return null
        if (json != cached) runCatching {
            directory.mkdirs()
            val temporary = File(directory, "$key.json.tmp")
            temporary.writeText(json)
            check(temporary.renameTo(stored))
        }
        return json
    }
}
