package org.hander.novelreader.source

/**
 * The standard door every source goes through. Hander never talks to a website directly;
 * it only talks to a NovelSource.
 */
interface NovelSource {
    val info: NovelSourceInfo
    val id: String get() = info.id
    val name: String get() = info.name
    val version: String get() = info.version

    suspend fun search(query: String): List<NovelSearchResult>

    suspend fun browse(page: Int): List<NovelSearchResult> = emptyList()

    suspend fun getNovel(id: String): NovelDetails

    suspend fun getChapters(novelId: String): List<Chapter>

    suspend fun getChapterContent(novelId: String, chapterId: String): ChapterContent
}
