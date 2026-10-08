package org.hander.novelreader.data

import android.content.Context
import org.hander.novelreader.model.Novel
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class Storage(private val ctx: Context) {

    private fun file(name: String) = File(ctx.filesDir, name)

    private fun readArray(name: String): JSONArray {
        val f = file(name)
        if (!f.exists()) return JSONArray()
        return try { JSONArray(f.readText()) } catch (_: Exception) { JSONArray() }
    }

    private fun writeArray(name: String, arr: JSONArray) {
        file(name).writeText(arr.toString())
    }

    private fun novelToJson(n: Novel): JSONObject = JSONObject().apply {
        put("id", n.id)
        put("sourceId", n.sourceId)
        put("title", n.title)
        put("author", n.author)
        put("description", n.description)
        put("coverUrl", n.coverUrl)
        put("status", n.status)
        put("textUrl", n.textUrl)
        put("genres", JSONArray(n.genres))
    }

    private fun jsonToNovel(o: JSONObject): Novel {
        val genresArr = o.optJSONArray("genres")
        val genres = mutableListOf<String>()
        if (genresArr != null) for (i in 0 until genresArr.length()) genres.add(genresArr.getString(i))
        return Novel(
            id = o.optString("id"),
            sourceId = o.optString("sourceId"),
            title = o.optString("title"),
            author = o.optString("author"),
            description = o.optString("description"),
            coverUrl = o.optString("coverUrl"),
            genres = genres,
            status = o.optString("status"),
            textUrl = o.optString("textUrl")
        )
    }

    // ---------------- favorites ----------------

    fun favorites(): List<Novel> {
        val arr = readArray("favorites.json")
        return (0 until arr.length()).map { jsonToNovel(arr.getJSONObject(it)) }
    }

    fun isFavorite(novelId: String): Boolean = favorites().any { it.id == novelId }

    fun toggleFavorite(novel: Novel) {
        val list = favorites().toMutableList()
        if (list.any { it.id == novel.id }) {
            list.removeAll { it.id == novel.id }
        } else {
            list.add(0, novel)
        }
        writeArray("favorites.json", JSONArray(list.map { novelToJson(it) }))
    }

    // ---------------- recents ----------------

    fun recents(): List<Novel> {
        val arr = readArray("recents.json")
        return (0 until arr.length()).map { jsonToNovel(arr.getJSONObject(it)) }
    }

    fun addRecent(novel: Novel) {
        val list = recents().toMutableList()
        list.removeAll { it.id == novel.id }
        list.add(0, novel)
        while (list.size > 30) list.removeAt(list.size - 1)
        writeArray("recents.json", JSONArray(list.map { novelToJson(it) }))
    }

    // ---------------- downloads ----------------

    fun downloadedNovels(): List<Novel> {
        val arr = readArray("downloads.json")
        return (0 until arr.length()).map { jsonToNovel(arr.getJSONObject(it)) }
    }

    fun isDownloaded(novelId: String): Boolean = downloadedNovels().any { it.id == novelId }

    fun markDownloaded(novel: Novel) {
        val list = downloadedNovels().toMutableList()
        if (list.none { it.id == novel.id }) {
            list.add(0, novel)
            writeArray("downloads.json", JSONArray(list.map { novelToJson(it) }))
        }
    }

    fun removeDownloaded(novelId: String) {
        val list = downloadedNovels().toMutableList()
        list.removeAll { it.id == novelId }
        writeArray("downloads.json", JSONArray(list.map { novelToJson(it) }))
        novelDir(novelId).deleteRecursively()
    }

    // ---------------- per-novel files on disk ----------------

    fun novelDir(novelId: String): File {
        val safe = novelId.replace(Regex("[^A-Za-z0-9_.-]"), "_")
        val dir = File(File(ctx.filesDir, "downloads"), safe)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun novelChaptersFile(novelId: String): File = File(novelDir(novelId), "chapters.json")

    fun saveChapters(novelId: String, chapters: List<Pair<String, String>>) {
        val arr = JSONArray()
        chapters.forEach { (label, text) ->
            arr.put(JSONObject().apply { put("label", label); put("text", text) })
        }
        novelChaptersFile(novelId).writeText(arr.toString())
    }

    fun loadChapters(novelId: String): List<Pair<String, String>> {
        val f = novelChaptersFile(novelId)
        if (!f.exists()) return emptyList()
        return try {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                o.optString("label") to o.optString("text")
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // ---------------- reading progress (shared by PDFs + downloads) ----------------

    private val progressPrefs = ctx.getSharedPreferences("hander_progress", Context.MODE_PRIVATE)
    fun savedPosition(bookId: String): Int = progressPrefs.getInt("pos_$bookId", 0)
    fun savePosition(bookId: String, index: Int) {
        progressPrefs.edit().putInt("pos_$bookId", index).apply()
    }

    // ---------------- settings ----------------

    private val settingsPrefs = ctx.getSharedPreferences("hander_settings", Context.MODE_PRIVATE)

    var voiceName: String?
        get() = settingsPrefs.getString("voice", null)
        set(v) { settingsPrefs.edit().putString("voice", v).apply() }

    var speed: Float
        get() = settingsPrefs.getFloat("speed", 1.0f)
        set(v) { settingsPrefs.edit().putFloat("speed", v).apply() }

    var pitch: Float
        get() = settingsPrefs.getFloat("pitch", 1.0f)
        set(v) { settingsPrefs.edit().putFloat("pitch", v).apply() }

    var fontSize: Int
        get() = settingsPrefs.getInt("font_size", 20)
        set(v) { settingsPrefs.edit().putInt("font_size", v).apply() }

    var offlineFolder: String?
        get() = settingsPrefs.getString("offline_folder", null)
        set(v) { settingsPrefs.edit().putString("offline_folder", v).apply() }

    /** Floating mini play/pause bubble, shown over other apps. */
    var floatingControlEnabled: Boolean
        get() = settingsPrefs.getBoolean("floating_control", false)
        set(v) { settingsPrefs.edit().putBoolean("floating_control", v).apply() }

    /** Volume buttons skip chapter/page while Hander is the foreground app. */
    var volumeKeyNavEnabled: Boolean
        get() = settingsPrefs.getBoolean("volume_key_nav", false)
        set(v) { settingsPrefs.edit().putBoolean("volume_key_nav", v).apply() }
}
