package org.hander.novelreader.source

import org.hander.novelreader.model.Novel

/**
 * The contract every library (source connector) implements. Hander's UI
 * never talks to a website directly - it only ever calls these methods,
 * so adding a new legal source later is just a new class in this shape.
 */
interface LibrarySource {
    val id: String
    val name: String
    val description: String

    /** Search this source for novels matching a free-text query. */
    suspend fun search(query: String): List<Novel>

    /** Fetch the full plain text of a novel, ready to be split into chapters. */
    suspend fun fetchFullText(novel: Novel): String
}
