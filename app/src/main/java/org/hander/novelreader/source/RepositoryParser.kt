package org.hander.novelreader.source

import org.hander.novelreader.source.adapters.AdapterRegistry
import org.json.JSONException
import org.json.JSONObject

data class ParsedRepository(
    val name: String,
    val sources: List<AvailableSource>,
    /** A short human-readable warning about the repository, or null. */
    val note: String?
)

/**
 * Reads a repository index. Supported: Hander's own format ("hander-repo").
 * Script-based repositories are recognised so they can be listed, but they are never executed.
 */
object RepositoryParser {

    private const val SUPPORTED_FORMAT_VERSION = 1
    private const val MAX_SOURCES = 1000
    const val UNSUPPORTED_FORMAT = "Unsupported extension format"

    fun parse(repoUrl: String, text: String, fallbackName: String): ParsedRepository {
        val root = try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw SourceException("This address does not contain a source list Hander can read.")
        }
        return when {
            root.str("format") == "hander-repo" -> parseNative(repoUrl, root, fallbackName)
            root.optJSONArray("scripts") != null -> parseScriptRepository(repoUrl, root, fallbackName)
            else -> throw SourceException("This repository uses a format Hander does not recognise.")
        }
    }

    /** Returns null if Hander can safely run this source, otherwise a human-readable reason. */
    fun unsupportedReason(info: NovelSourceInfo): String? = when {
        !AdapterRegistry.isSupported(info.adapter) -> UNSUPPORTED_FORMAT
        info.allowedHosts.isEmpty() -> "This source does not say which websites it uses."
        compareVersions(info.minHanderVersion, HanderVersion.CURRENT) > 0 -> "Needs a newer version of Hander."
        SourceCapability.SEARCH !in info.capabilities -> "This source cannot search."
        else -> null
    }

    private fun parseNative(repoUrl: String, root: JSONObject, fallbackName: String): ParsedRepository {
        if (root.optInt("formatVersion", 1) > SUPPORTED_FORMAT_VERSION) {
            throw SourceException("This repository needs a newer version of Hander.")
        }
        val array = root.optJSONArray("sources")
            ?: throw SourceException("This repository has no source list.")
        val seen = HashSet<String>()
        val sources = ArrayList<AvailableSource>()
        for (i in 0 until minOf(array.length(), MAX_SOURCES)) {
            val entry = array.optJSONObject(i) ?: continue
            val info = SourceJson.parseInfo(entry) ?: continue
            if (!seen.add(info.id)) continue
            val reason = unsupportedReason(info)
            sources.add(AvailableSource(info, repoUrl, reason == null, reason))
        }
        return ParsedRepository(root.str("name") ?: fallbackName, sources, null)
    }

    private fun parseScriptRepository(repoUrl: String, root: JSONObject, fallbackName: String): ParsedRepository {
        val array = root.optJSONArray("scripts")!!
        val sources = ArrayList<AvailableSource>()
        for (i in 0 until minOf(array.length(), MAX_SOURCES)) {
            val entry = array.optJSONObject(i) ?: continue
            val rawId = entry.opt("id")?.toString()?.trim().orEmpty()
            val name = entry.str("name") ?: continue
            if (rawId.isEmpty()) continue
            val info = NovelSourceInfo(
                id = "script-$rawId",
                name = name.take(80),
                version = entry.str("ver") ?: entry.str("version") ?: "?",
                author = "Unknown",
                language = entry.str("lang") ?: "?",
                minHanderVersion = "1.0",
                capabilities = emptySet(),
                adapter = "script",
                allowedHosts = emptySet()
            )
            sources.add(AvailableSource(info, repoUrl, false, UNSUPPORTED_FORMAT))
        }
        val note = "$UNSUPPORTED_FORMAT. This repository publishes scripts, and Hander does not run " +
            "downloaded scripts because that would not be safe."
        return ParsedRepository(root.str("name") ?: fallbackName, sources, note)
    }
}
