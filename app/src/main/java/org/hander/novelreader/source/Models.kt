package org.hander.novelreader.source

/** What a source says it can do. Sources never have to support everything. */
enum class SourceCapability { SEARCH, BROWSE, DETAILS, CHAPTERS, CONTENT, COVER, DOWNLOAD }

enum class SourceStatus { UNTESTED, WORKING, PARTIAL, BROKEN, UNSUPPORTED, DISABLED }

enum class CheckResult { PASS, FAIL, SKIPPED }

fun SourceStatus.label(): String = when (this) {
    SourceStatus.UNTESTED -> "Not tested"
    SourceStatus.WORKING -> "Working"
    SourceStatus.PARTIAL -> "Partially working"
    SourceStatus.BROKEN -> "Broken"
    SourceStatus.UNSUPPORTED -> "Unsupported"
    SourceStatus.DISABLED -> "Disabled"
}

/** A novel is always identified by sourceId + novelId, never by title. */
data class NovelSearchResult(
    val sourceId: String,
    val novelId: String,
    val title: String,
    val author: String? = null,
    val coverUrl: String? = null,
    val description: String? = null
)

data class NovelDetails(
    val sourceId: String,
    val id: String,
    val title: String,
    val author: String? = null,
    val description: String? = null,
    val coverUrl: String? = null,
    val genres: List<String> = emptyList(),
    val status: String? = null,
    val alternativeTitles: List<String> = emptyList()
)

data class Chapter(
    val novelId: String,
    val id: String,
    val title: String,
    val index: Int
)

data class ChapterContent(
    val novelId: String,
    val chapterId: String,
    val title: String,
    val text: String
)

data class NovelSourceInfo(
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val language: String,
    val minHanderVersion: String,
    val capabilities: Set<SourceCapability>,
    val adapter: String,
    val allowedHosts: Set<String>,
    val configJson: String = "{}",
    val iconUrl: String? = null,
    val testQuery: String = "a"
)

data class SourceHealth(
    val status: SourceStatus,
    val initialization: CheckResult,
    val search: CheckResult,
    val details: CheckResult,
    val chapters: CheckResult,
    val content: CheckResult,
    val cover: CheckResult,
    val reason: String?,
    val testedAt: Long
) {
    companion object {
        fun untested() = SourceHealth(
            SourceStatus.UNTESTED, CheckResult.SKIPPED, CheckResult.SKIPPED, CheckResult.SKIPPED,
            CheckResult.SKIPPED, CheckResult.SKIPPED, CheckResult.SKIPPED, null, 0L
        )

        fun unsupported(reason: String) = SourceHealth(
            SourceStatus.UNSUPPORTED, CheckResult.FAIL, CheckResult.SKIPPED, CheckResult.SKIPPED,
            CheckResult.SKIPPED, CheckResult.SKIPPED, CheckResult.SKIPPED, reason, System.currentTimeMillis()
        )
    }
}

data class InstalledSource(
    val info: NovelSourceInfo,
    val repositoryUrl: String,
    val enabled: Boolean,
    val health: SourceHealth,
    val installedAt: Long
) {
    val effectiveStatus: SourceStatus
        get() = if (!enabled) SourceStatus.DISABLED else health.status
}

data class RepositoryInfo(
    val url: String,
    val name: String,
    val enabled: Boolean,
    val builtIn: Boolean,
    val lastUpdated: Long?,
    val sourceCount: Int,
    val error: String?
)

data class AvailableSource(
    val info: NovelSourceInfo,
    val repositoryUrl: String,
    val supported: Boolean,
    val unsupportedReason: String?
)
