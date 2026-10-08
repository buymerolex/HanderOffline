package org.hander.novelreader.model

/** A novel as reported by a library source (online) or built from a local file. */
data class Novel(
    val id: String,              // e.g. "gutenberg:1661"
    val sourceId: String,        // e.g. "gutenberg", or "local" for offline PDFs
    val title: String,
    val author: String = "",
    val description: String = "",
    val coverUrl: String = "",
    val genres: List<String> = emptyList(),
    val status: String = "",
    val textUrl: String = ""     // where the full plain text can be fetched from
)

/** One chapter, once a novel's full text has been split up. */
data class Chapter(val index: Int, val label: String, val text: String)

enum class BookKind { PDF, DOWNLOADED }

/** A locally available book, whether it's a folder PDF or a downloaded novel. */
data class LocalBook(
    val id: String,
    val title: String,
    val kind: BookKind,
    val path: String,            // content:// uri string for PDFs, novel id for downloads
    val progress: Int = 0
)
