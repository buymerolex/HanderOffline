package org.hander.novelreader.reader

import java.io.File

interface BookHandle {
    /** PDF pages, or EPUB chapters. */
    val pageCount: Int

    /** Chapter titles for an EPUB. Empty for a PDF. */
    val chapterTitles: List<String>

    suspend fun pageText(index: Int): String
    suspend fun renderCover(target: File)
    fun close()
}