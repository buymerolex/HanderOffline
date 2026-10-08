package org.hander.novelreader.source

import android.util.Log
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.hander.novelreader.network.SafeHttp
import kotlin.coroutines.cancellation.CancellationException

private sealed class Step<out T> {
    class Ok<T>(val value: T) : Step<T>()
    class Fail(val reason: String) : Step<Nothing>()
}

/**
 * Runs a real health test against a source: search -> details -> chapters -> content -> cover.
 * A failure in one step never crashes anything; it is recorded in the result.
 */
class SourceVerifier {

    suspend fun verify(source: NovelSource, http: SafeHttp): SourceHealth {
        val info = source.info
        val caps = info.capabilities
        val problems = ArrayList<String>()

        var search = CheckResult.SKIPPED
        var details = CheckResult.SKIPPED
        var chapters = CheckResult.SKIPPED
        var content = CheckResult.SKIPPED
        var cover = CheckResult.SKIPPED

        var target: NovelSearchResult? = null
        var chapter: Chapter? = null
        var coverUrl: String? = null

        if (SourceCapability.SEARCH in caps) {
            when (val r = step("Search") { source.search(info.testQuery) }) {
                is Step.Ok -> {
                    val usable = r.value.filter { it.novelId.isNotBlank() && it.title.isNotBlank() }
                    if (usable.isEmpty()) {
                        search = CheckResult.FAIL
                        problems.add("Search returned no usable results.")
                    } else {
                        search = CheckResult.PASS
                        target = usable.first()
                    }
                }
                is Step.Fail -> {
                    search = CheckResult.FAIL
                    problems.add(r.reason)
                }
            }
        } else {
            problems.add("This source cannot search, so it cannot be tested.")
        }

        val t = target
        if (t != null) coverUrl = t.coverUrl

        if (SourceCapability.DETAILS in caps && t != null) {
            when (val r = step("Novel details") { source.getNovel(t.novelId) }) {
                is Step.Ok -> if (r.value.title.isNotBlank()) {
                    details = CheckResult.PASS
                    if (r.value.coverUrl != null) coverUrl = r.value.coverUrl
                } else {
                    details = CheckResult.FAIL
                    problems.add("Novel details had no title.")
                }
                is Step.Fail -> {
                    details = CheckResult.FAIL
                    problems.add(r.reason)
                }
            }
        }

        if (SourceCapability.CHAPTERS in caps && t != null) {
            when (val r = step("Chapter list") { source.getChapters(t.novelId) }) {
                is Step.Ok -> if (r.value.isNotEmpty()) {
                    chapters = CheckResult.PASS
                    chapter = r.value.first()
                } else {
                    chapters = CheckResult.FAIL
                    problems.add("The chapter list was empty.")
                }
                is Step.Fail -> {
                    chapters = CheckResult.FAIL
                    problems.add(r.reason)
                }
            }
        }

        val c = chapter
        if (SourceCapability.CONTENT in caps && t != null && c != null) {
            when (val r = step("Chapter content") { source.getChapterContent(t.novelId, c.id) }) {
                is Step.Ok -> if (r.value.text.trim().length >= 50) {
                    content = CheckResult.PASS
                } else {
                    content = CheckResult.FAIL
                    problems.add("The chapter text was empty.")
                }
                is Step.Fail -> {
                    content = CheckResult.FAIL
                    problems.add(r.reason)
                }
            }
        }

        val cu = coverUrl
        if (SourceCapability.COVER in caps && cu != null) {
            when (val r = step("Cover") { http.exists(cu) }) {
                is Step.Ok -> if (r.value) {
                    cover = CheckResult.PASS
                } else {
                    cover = CheckResult.FAIL
                    problems.add("Covers could not be loaded.")
                }
                is Step.Fail -> {
                    cover = CheckResult.FAIL
                    problems.add("Covers could not be loaded.")
                }
            }
        }

        val required = ArrayList<CheckResult>()
        if (SourceCapability.SEARCH in caps) required.add(search)
        if (SourceCapability.DETAILS in caps) required.add(details)
        if (SourceCapability.CHAPTERS in caps) required.add(chapters)
        if (SourceCapability.CONTENT in caps) required.add(content)

        val status = when {
            required.isNotEmpty() && required.all { it == CheckResult.PASS } -> SourceStatus.WORKING
            search != CheckResult.PASS || required.none { it == CheckResult.PASS } -> SourceStatus.BROKEN
            else -> SourceStatus.PARTIAL
        }
        var reason = problems.joinToString(" ").ifBlank { null }
        if (status != SourceStatus.WORKING && reason == null) reason = "The source did not pass all checks."

        return SourceHealth(
            status = status,
            initialization = CheckResult.PASS,
            search = search,
            details = details,
            chapters = chapters,
            content = content,
            cover = cover,
            reason = reason,
            testedAt = System.currentTimeMillis()
        )
    }

    private suspend fun <T> step(label: String, block: suspend () -> T): Step<T> = try {
        Step.Ok(withTimeout(STEP_TIMEOUT_MS) { block() })
    } catch (e: TimeoutCancellationException) {
        Step.Fail("$label timed out.")
    } catch (e: CancellationException) {
        throw e
    } catch (e: SourceException) {
        Step.Fail("$label: ${e.userMessage}")
    } catch (e: Exception) {
        Log.w(TAG, "$label failed unexpectedly", e)
        Step.Fail("$label failed unexpectedly (${e.javaClass.simpleName}: ${e.message?.take(80)}).")
    }

    private companion object {
        const val STEP_TIMEOUT_MS = 25_000L
        const val TAG = "SourceVerifier"
    }
}
