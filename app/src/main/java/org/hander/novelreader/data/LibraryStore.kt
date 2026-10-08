package org.hander.novelreader.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** A book the user has touched (opened or favorited). Identified by its document URI. */
data class LocalBook(
    val uri: String,
    val title: String,
    val pageCount: Int = 0,
    val lastPage: Int = 0,
    val favorite: Boolean = false,
    val lastOpened: Long = 0L,
    val inRecents: Boolean = false,
    val coverPath: String? = null,
) {
    val hasProgress: Boolean get() = pageCount > 0
    val progress: Float
        get() = if (pageCount > 0) ((lastPage + 1f) / pageCount).coerceIn(0f, 1f) else 0f
}

data class LibraryState(
    val books: Map<String, LocalBook> = emptyMap(),
    val folderUri: String? = null,
    val lastOpenedUri: String? = null,
) {
    val recents: List<LocalBook>
        get() = books.values.filter { it.inRecents }.sortedByDescending { it.lastOpened }.take(20)
    val favorites: List<LocalBook>
        get() = books.values.filter { it.favorite }.sortedBy { it.title.lowercase() }
    val continueReading: LocalBook?
        get() = lastOpenedUri?.let { books[it] }
}

/**
 * Favorites, recents, reading progress and the chosen folder.
 * Stored as a small JSON document in SharedPreferences (Phase 2 moves this to Room).
 */
class LibraryStore(context: Context) {
    private val prefs = context.getSharedPreferences("hander_library", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    private fun load(): LibraryState {
        val map = LinkedHashMap<String, LocalBook>()
        try {
            val arr = JSONArray(prefs.getString("books", "[]") ?: "[]")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val book = LocalBook(
                    uri = o.getString("uri"),
                    title = o.optString("title", "Untitled"),
                    pageCount = o.optInt("pageCount", 0),
                    lastPage = o.optInt("lastPage", 0),
                    favorite = o.optBoolean("favorite", false),
                    lastOpened = o.optLong("lastOpened", 0L),
                    inRecents = o.optBoolean("inRecents", false),
                    coverPath = if (o.has("cover") && !o.isNull("cover")) o.getString("cover") else null,
                )
                map[book.uri] = book
            }
        } catch (e: Exception) {
            Log.w("LibraryStore", "Could not read saved library, starting fresh", e)
        }
        return LibraryState(map, prefs.getString("folder", null), prefs.getString("last", null))
    }

    private fun persist(s: LibraryState) {
        val arr = JSONArray()
        for (b in s.books.values) {
            val o = JSONObject()
                .put("uri", b.uri)
                .put("title", b.title)
                .put("pageCount", b.pageCount)
                .put("lastPage", b.lastPage)
                .put("favorite", b.favorite)
                .put("lastOpened", b.lastOpened)
                .put("inRecents", b.inRecents)
            b.coverPath?.let { o.put("cover", it) }
            arr.put(o)
        }
        prefs.edit()
            .putString("books", arr.toString())
            .putString("folder", s.folderUri)
            .putString("last", s.lastOpenedUri)
            .apply()
    }

    @Synchronized
    private fun mutate(change: (LibraryState) -> LibraryState) {
        val next = change(_state.value)
        _state.value = next
        persist(next)
    }

    fun get(uri: String): LocalBook? = _state.value.books[uri]

    fun setFolder(uri: String?) = mutate { it.copy(folderUri = uri) }

    fun markOpened(uri: String, title: String, pageCount: Int, coverPath: String?) = mutate { s ->
        val old = s.books[uri]
        val book = (old ?: LocalBook(uri = uri, title = title)).copy(
            title = title,
            pageCount = pageCount,
            lastOpened = System.currentTimeMillis(),
            inRecents = true,
            coverPath = coverPath ?: old?.coverPath,
        )
        s.copy(books = s.books + (uri to book), lastOpenedUri = uri)
    }

    fun saveProgress(uri: String, page: Int, pageCount: Int) = mutate { s ->
        val old = s.books[uri]
        if (old == null) {
            s
        } else {
            s.copy(books = s.books + (uri to old.copy(lastPage = page, pageCount = pageCount)))
        }
    }

    fun toggleFavorite(uri: String, title: String) = mutate { s ->
        val old = s.books[uri] ?: LocalBook(uri = uri, title = title)
        s.copy(books = s.books + (uri to old.copy(favorite = !old.favorite)))
    }

    /** Clears the Recents list. Favorites and reading progress are kept. */
    fun clearRecents() = mutate { s ->
        s.copy(books = s.books.mapValues { it.value.copy(inRecents = false) })
    }
}
