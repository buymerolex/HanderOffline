package org.hander.novelreader.source

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.hander.novelreader.model.Novel
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Project Gutenberg, via the free public Gutendex JSON API
 * (https://gutendex.com). Everything here is public-domain text with no
 * authentication and no scraping - a straightforward, legal first source.
 */
class GutenbergSource : LibrarySource {
    override val id = "gutenberg"
    override val name = "Project Gutenberg"
    override val description = "70,000+ public-domain books, free to read and download."

    private val client = OkHttpClient()

    override suspend fun search(query: String): List<Novel> = withContext(Dispatchers.IO) {
        val results = mutableListOf<Novel>()
        try {
            val url = "https://gutendex.com/books?search=" + URLEncoder.encode(query, "UTF-8")
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext results
                val body = resp.body?.string() ?: return@withContext results
                val json = JSONObject(body)
                val arr = json.optJSONArray("results") ?: return@withContext results

                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    val gid = item.optInt("id")
                    val title = item.optString("title", "Untitled")

                    val authorsArr = item.optJSONArray("authors")
                    val author = if (authorsArr != null && authorsArr.length() > 0) {
                        authorsArr.getJSONObject(0).optString("name", "Unknown author")
                    } else "Unknown author"

                    var cover = ""
                    var textUrl = ""
                    val formats = item.optJSONObject("formats")
                    if (formats != null) {
                        val keys = formats.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            val v = formats.optString(k, "")
                            if (v.isEmpty()) continue
                            if (cover.isEmpty() && k.startsWith("image/")) cover = v
                            if (textUrl.isEmpty() && k.startsWith("text/plain")) textUrl = v
                        }
                    }
                    if (textUrl.isEmpty()) continue // skip entries with no readable text

                    val subjectsArr = item.optJSONArray("subjects")
                    val genres = mutableListOf<String>()
                    if (subjectsArr != null) {
                        for (j in 0 until minOf(subjectsArr.length(), 6)) {
                            genres.add(subjectsArr.optString(j))
                        }
                    }

                    results.add(
                        Novel(
                            id = "gutenberg:$gid",
                            sourceId = id,
                            title = title,
                            author = author,
                            description = if (genres.isNotEmpty())
                                "Subjects: " + genres.joinToString(", ") else "",
                            coverUrl = cover,
                            genres = genres,
                            status = "Public domain",
                            textUrl = textUrl
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // network error / parsing error -> just return what we have (possibly empty)
        }
        results
    }

    override suspend fun fetchFullText(novel: Novel): String = withContext(Dispatchers.IO) {
        if (novel.textUrl.isEmpty()) return@withContext ""
        try {
            val request = Request.Builder().url(novel.textUrl).build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext ""
                resp.body?.string() ?: ""
            }
        } catch (_: Exception) {
            ""
        }
    }
}
