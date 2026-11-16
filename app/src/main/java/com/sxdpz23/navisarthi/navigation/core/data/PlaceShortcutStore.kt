package com.sxdpz23.navisarthi.navigation.core.data

import android.content.Context
import com.sxdpz23.navisarthi.navigation.core.model.SavedPlace
import com.sxdpz23.navisarthi.navigation.core.model.ShortcutKind
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** Persisted Home / Work shortcuts (each an optional [SavedPlace]). */
@Singleton
class PlaceShortcutStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("navigation_shortcuts", Context.MODE_PRIVATE)

    // ignoreUnknownKeys: a newer build's extra field must not fail the decode here,
    // or the shortcut silently reads as unset (see PlaceListStore for the store rule).
    private val json = Json { ignoreUnknownKeys = true }

    fun get(kind: ShortcutKind): SavedPlace? =
        prefs.getString(kind.name, null)?.let {
            runCatching { json.decodeFromString<SavedPlace>(it) }.getOrNull()
        }

    /** Set (or clear, with null) the place for [kind]. */
    fun set(kind: ShortcutKind, place: SavedPlace?) {
        prefs.edit().apply {
            if (place == null) remove(kind.name) else putString(kind.name, json.encodeToString(place))
        }.apply()
    }
}
