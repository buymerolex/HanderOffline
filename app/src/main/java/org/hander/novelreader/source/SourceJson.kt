package org.hander.novelreader.source

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

private val ID_REGEX = Regex("^[a-z0-9][a-z0-9._-]{1,63}$")
private val HOST_REGEX = Regex("^[a-z0-9]([a-z0-9.-]*[a-z0-9])?$")

private fun <T : Enum<T>> parseEnum(values: Array<T>, name: String?, default: T): T =
    values.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: default

/** Converts source data to and from JSON (used for repository files and for local storage). */
object SourceJson {

    /** Reads one source entry. Returns null when required fields are missing or invalid. */
    fun parseInfo(o: JSONObject): NovelSourceInfo? {
        val id = o.str("id") ?: return null
        if (!ID_REGEX.matches(id)) return null
        val name = o.str("name") ?: return null
        val version = o.str("version") ?: return null
        val adapter = o.str("adapter") ?: return null
        val caps = o.optJSONArray("capabilities")?.strings().orEmpty()
            .mapNotNull { raw -> SourceCapability.values().firstOrNull { it.name.equals(raw, ignoreCase = true) } }
            .toSet()
        val hosts = o.optJSONArray("allowedHosts")?.strings().orEmpty()
            .map { it.lowercase() }
            .filter { HOST_REGEX.matches(it) }
            .toSet()
        return NovelSourceInfo(
            id = id,
            name = name.take(80),
            version = version.take(32),
            author = o.str("author") ?: "Unknown",
            language = o.str("language") ?: "en",
            minHanderVersion = o.str("minHanderVersion") ?: "1.0",
            capabilities = caps,
            adapter = adapter,
            allowedHosts = hosts,
            configJson = o.optJSONObject("config")?.toString() ?: "{}",
            iconUrl = o.str("iconUrl"),
            testQuery = o.str("testQuery") ?: "a"
        )
    }

    fun infoToJson(i: NovelSourceInfo): JSONObject = JSONObject().apply {
        put("id", i.id)
        put("name", i.name)
        put("version", i.version)
        put("author", i.author)
        put("language", i.language)
        put("minHanderVersion", i.minHanderVersion)
        put("capabilities", JSONArray(i.capabilities.map { it.name }))
        put("adapter", i.adapter)
        put("allowedHosts", JSONArray(i.allowedHosts.toList()))
        put("config", try { JSONObject(i.configJson) } catch (e: JSONException) { JSONObject() })
        put("iconUrl", i.iconUrl)
        put("testQuery", i.testQuery)
    }

    fun healthToJson(h: SourceHealth): JSONObject = JSONObject().apply {
        put("status", h.status.name)
        put("initialization", h.initialization.name)
        put("search", h.search.name)
        put("details", h.details.name)
        put("chapters", h.chapters.name)
        put("content", h.content.name)
        put("cover", h.cover.name)
        put("reason", h.reason)
        put("testedAt", h.testedAt)
    }

    fun healthFromJson(o: JSONObject?): SourceHealth {
        if (o == null) return SourceHealth.untested()
        val skipped = CheckResult.SKIPPED
        return SourceHealth(
            status = parseEnum(SourceStatus.values(), o.str("status"), SourceStatus.UNTESTED),
            initialization = parseEnum(CheckResult.values(), o.str("initialization"), skipped),
            search = parseEnum(CheckResult.values(), o.str("search"), skipped),
            details = parseEnum(CheckResult.values(), o.str("details"), skipped),
            chapters = parseEnum(CheckResult.values(), o.str("chapters"), skipped),
            content = parseEnum(CheckResult.values(), o.str("content"), skipped),
            cover = parseEnum(CheckResult.values(), o.str("cover"), skipped),
            reason = o.str("reason"),
            testedAt = o.optLong("testedAt", 0L)
        )
    }

    fun installedToJson(s: InstalledSource): JSONObject = JSONObject().apply {
        put("info", infoToJson(s.info))
        put("repositoryUrl", s.repositoryUrl)
        put("enabled", s.enabled)
        put("health", healthToJson(s.health))
        put("installedAt", s.installedAt)
    }

    fun installedFromJson(o: JSONObject): InstalledSource? {
        val info = parseInfo(o.optJSONObject("info") ?: return null) ?: return null
        return InstalledSource(
            info = info,
            repositoryUrl = o.str("repositoryUrl") ?: return null,
            enabled = o.optBoolean("enabled", true),
            health = healthFromJson(o.optJSONObject("health")),
            installedAt = o.optLong("installedAt", 0L)
        )
    }

    fun repositoryToJson(r: RepositoryInfo): JSONObject = JSONObject().apply {
        put("url", r.url)
        put("name", r.name)
        put("enabled", r.enabled)
        put("builtIn", r.builtIn)
        put("lastUpdated", r.lastUpdated ?: -1L)
        put("sourceCount", r.sourceCount)
        put("error", r.error)
    }

    fun repositoryFromJson(o: JSONObject): RepositoryInfo? {
        val url = o.str("url") ?: return null
        val updated = o.optLong("lastUpdated", -1L)
        return RepositoryInfo(
            url = url,
            name = o.str("name") ?: url,
            enabled = o.optBoolean("enabled", true),
            builtIn = o.optBoolean("builtIn", false),
            lastUpdated = if (updated > 0) updated else null,
            sourceCount = o.optInt("sourceCount", 0),
            error = o.str("error")
        )
    }

    fun availableToJson(a: AvailableSource): JSONObject = JSONObject().apply {
        put("info", infoToJson(a.info))
        put("repositoryUrl", a.repositoryUrl)
        put("supported", a.supported)
        put("unsupportedReason", a.unsupportedReason)
    }

    fun availableFromJson(o: JSONObject): AvailableSource? {
        val info = parseInfo(o.optJSONObject("info") ?: return null) ?: return null
        return AvailableSource(
            info = info,
            repositoryUrl = o.str("repositoryUrl") ?: return null,
            supported = o.optBoolean("supported", false),
            unsupportedReason = o.str("unsupportedReason")
        )
    }
}
