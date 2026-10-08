package org.hander.novelreader.source

/**
 * All sources Hander currently knows how to talk to. Adding a new legal
 * source later means writing one more LibrarySource implementation and
 * listing it here - the rest of the app (search, novel page, downloads,
 * reader) doesn't change.
 */
object SourceRegistry {
    val installed: List<LibrarySource> = listOf(
        GutenbergSource()
    )

    fun byId(id: String): LibrarySource? = installed.firstOrNull { it.id == id }
}
