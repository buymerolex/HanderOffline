package org.hander.novelreader.source

import android.content.Context
import android.util.Log
import org.json.JSONArray
import java.io.File

/**
 * Saves repositories, installed sources and the available-source list as small JSON files
 * in Hander's private storage. (Chapters will never be stored here; they get their own files in Phase 6.)
 */
class SourceStore(context: Context) {

    private val dir = File(context.filesDir, "hander/sources").apply { mkdirs() }

    fun loadRepositories(): List<RepositoryInfo> =
        read("repositories.json") { SourceJson.repositoryFromJson(it) }

    fun loadInstalled(): List<InstalledSource> =
        read("installed.json") { SourceJson.installedFromJson(it) }

    fun loadAvailable(): List<AvailableSource> =
        read("available.json") { SourceJson.availableFromJson(it) }

    fun saveRepositories(list: List<RepositoryInfo>) =
        write("repositories.json", list.map { SourceJson.repositoryToJson(it) })

    fun saveInstalled(list: List<InstalledSource>) =
        write("installed.json", list.map { SourceJson.installedToJson(it) })

    fun saveAvailable(list: List<AvailableSource>) =
        write("available.json", list.map { SourceJson.availableToJson(it) })

    private fun <T> read(name: String, parse: (org.json.JSONObject) -> T?): List<T> {
        val file = File(dir, name)
        if (!file.exists()) return emptyList()
        return try {
            val array = JSONArray(file.readText())
            val out = ArrayList<T>()
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                parse(item)?.let { out.add(it) }
            }
            out
        } catch (e: Exception) {
            Log.w(TAG, "Could not read $name, starting fresh", e)
            emptyList()
        }
    }

    private fun write(name: String, items: List<org.json.JSONObject>) {
        try {
            val array = JSONArray()
            items.forEach { array.put(it) }
            val target = File(dir, name)
            val temp = File(dir, "$name.tmp")
            temp.writeText(array.toString())
            if (!temp.renameTo(target)) {
                target.delete()
                temp.renameTo(target)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not save $name", e)
        }
    }

    private companion object {
        const val TAG = "SourceStore"
    }
}
