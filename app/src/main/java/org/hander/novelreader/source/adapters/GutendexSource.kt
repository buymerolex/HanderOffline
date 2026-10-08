package org.hander.novelreader.source.adapters

import org.hander.novelreader.network.SafeHttp
import org.hander.novelreader.source.Chapter
import org.hander.novelreader.source.ChapterContent
import org.hander.novelreader.source.NovelDetails
import org.hander.novelreader.source.NovelSearchResult
import org.hander.novelreader.source.NovelSource
import org.hander.novelreader.source.NovelSourceInfo
import org.hander.novelreader.source.SourceException
import org.hander.novelreader.source.str
import org.hander.novelreader.source.strings
import org.json.JSONException
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Project Gutenberg (public-domain books) through the open Gutendex API.
 * Phase 2: each book is one chapter ("Full text"). Chapter splitting comes in Phase 3.
 */
class GutendexSource(
    override val info: NovelSourceInfo,
    private val http: SafeHttp
) : NovelSource {

    private val baseUrl: String = readBaseUrl()

    private fun readBaseUrl(): String {
        val configured = try {
            JSONObject(info.configJson).optString("baseUrl", DEFAULT_BASE)
        } catch (e: JSONException) {
            DEFAULT_BASE
        }
        return (if (configured.isBlank()) DEFAULT_BASE else configured).trimEnd('/')
    }

    override suspend fun search(query: String): List<NovelSearchResult> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val encoded = URLEncoder.encode(q, "UTF-8")
        return parseBooks(fetchObject("$baseUrl/books/?search=$encoded"))
    }

    override suspend fun browse(page: Int): List<NovelSearchResult> =
        parseBooks(fetchObject("$baseUrl/books/?page=${page.coerceAtLeast(1)}"))

    override suspend fun getNovel(id: String): NovelDetails {
        val book = fetchBook(id)
        val title = book.str("title") ?: throw SourceException("The book has no title.")
        val summary = book.optJSONArray("summaries")?.strings()?.firstOrNull()
        return NovelDetails(
            sourceId = info.id,
            id = id,
            title = title,
            author = authorOf(book),
            description = summary,
            coverUrl = coverOf(book),
            genres = genresOf(book),
            status = "Complete",
            alternativeTitles = emptyList()
        )
    }

    override suspend fun getChapters(novelId: String): List<Chapter> {
        val book = fetchBook(novelId)
        if (textUrl(book) == null) throw SourceException("This book has no readable text edition.")
        return listOf(Chapter(novelId, FULL_TEXT_ID, "Full text", 0))
    }

    override suspend fun getChapterContent(novelId: String, chapterId: String): ChapterContent {
        if (chapterId != FULL_TEXT_ID) throw SourceException("That chapter does not exist.")
        val book = fetchBook(novelId)
        val url = textUrl(book) ?: throw SourceException("This book has no readable text edition.")
        val cleaned = GutenbergText.clean(http.getString(url))
        if (cleaned.isBlank()) throw SourceException("The book text was empty.")
        return ChapterContent(novelId, chapterId, book.str("title") ?: "Full text", cleaned)
    }

    // ---- helpers ----

    private suspend fun fetchBook(id: String): JSONObject {
        if (id.isEmpty() || id.length > 9 || !id.all { it.isDigit() }) {
            throw SourceException("That book identifier is not valid.")
        }
        return fetchObject("$baseUrl/books/$id")
    }

    private suspend fun fetchObject(url: String): JSONObject {
        val text = http.getString(url)
        return try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw SourceException("The website sent data Hander could not understand.", e)
        }
    }

    private fun parseBooks(root: JSONObject): List<NovelSearchResult> {
        val array = root.optJSONArray("results")
            ?: throw SourceException("The website sent data Hander could not understand.")
        val out = ArrayList<NovelSearchResult>()
        for (i in 0 until array.length()) {
            val book = array.optJSONObject(i) ?: continue
            val id = book.optInt("id", 0)
            val title = book.str("title")
            if (id <= 0 || title == null) continue
            if (textUrl(book) == null) continue
            out.add(
                NovelSearchResult(
                    sourceId = info.id,
                    novelId = id.toString(),
                    title = title,
                    author = authorOf(book),
                    coverUrl = coverOf(book),
                    description = book.optJSONArray("summaries")?.strings()?.firstOrNull()
                )
            )
        }
        return out
    }

    private fun authorOf(book: JSONObject): String? {
        val authors = book.optJSONArray("authors") ?: return null
        val names = ArrayList<String>()
        for (i in 0 until authors.length()) {
            val raw = authors.optJSONObject(i)?.str("name") ?: continue
            val parts = raw.split(", ", limit = 2)
            names.add(if (parts.size == 2) "${parts[1]} ${parts[0]}" else raw)
        }
        return if (names.isEmpty()) null else names.joinToString(", ")
    }

    private fun coverOf(book: JSONObject): String? =
        book.optJSONObject("formats")?.str("image/jpeg")?.replaceFirst("http://", "https://")

    private fun genresOf(book: JSONObject): List<String> {
        val shelves = book.optJSONArray("bookshelves")?.strings().orEmpty()
            .map { it.removePrefix("Category: ") }
        val list = if (shelves.isNotEmpty()) shelves else book.optJSONArray("subjects")?.strings().orEmpty()
        return list.distinct().take(8)
    }

    private fun textUrl(book: JSONObject): String? {
        val formats = book.optJSONObject("formats") ?: return null
        var best: String? = null
        val keys = formats.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (!key.startsWith("text/plain")) continue
            val value = formats.optString(key, "")
            if (value.isBlank() || value.endsWith(".zip")) continue
            if (best == null || key.contains("utf-8", ignoreCase = true)) best = value
        }
        return best?.replaceFirst("http://", "https://")
    }

    companion object {
        const val FULL_TEXT_ID = "full"
        private const val DEFAULT_BASE = "https://gutendex.com"
    }
}
