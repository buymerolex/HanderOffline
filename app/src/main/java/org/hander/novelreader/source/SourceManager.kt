package org.hander.novelreader.source

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.hander.novelreader.network.SafeHttp
import org.hander.novelreader.source.adapters.AdapterRegistry
import java.net.URL
import kotlin.coroutines.cancellation.CancellationException

/**
 * Central place for everything about sources: repositories, installing, testing, enabling.
 * All work runs on its own background scope, so it keeps going even if the screen changes,
 * and one failing source or repository can never crash the app.
 */
class SourceManager private constructor(private val appContext: Context) {

    private val store = SourceStore(appContext)
    private val verifier = SourceVerifier()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Mutex()
    private val ready = CompletableDeferred<Unit>()
    private val adapterCache = HashMap<String, NovelSource>()

    private val _repositories = MutableStateFlow<List<RepositoryInfo>>(emptyList())
    val repositories: StateFlow<List<RepositoryInfo>> = _repositories.asStateFlow()

    private val _installed = MutableStateFlow<List<InstalledSource>>(emptyList())
    val installed: StateFlow<List<InstalledSource>> = _installed.asStateFlow()

    private val _available = MutableStateFlow<List<AvailableSource>>(emptyList())
    val available: StateFlow<List<AvailableSource>> = _available.asStateFlow()

    /** Keys of work in progress, e.g. "source:gutenberg", "repo:add", "repo:all". */
    private val _busy = MutableStateFlow<Set<String>>(emptySet())
    val busy: StateFlow<Set<String>> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        scope.launch { loadInitial() }
    }

    // ------------------------------------------------------------ public API

    fun clearMessage() {
        _message.value = null
    }

    /** Returns a ready-to-use adapter for an installed, enabled source (used from Phase 3). */
    fun getSource(id: String): NovelSource? {
        val inst = _installed.value.firstOrNull { it.info.id == id && it.enabled } ?: return null
        val key = id + "@" + inst.info.version
        synchronized(adapterCache) {
            val cached = adapterCache[key]
            if (cached != null) return cached
            val created = AdapterRegistry.create(inst.info, SafeHttp(inst.info.allowedHosts)) ?: return null
            adapterCache[key] = created
            return created
        }
    }

    fun addRepository(input: String) = launchTask("repo:add") {
        val url = normalizeRepoUrl(input)
        if (_repositories.value.any { sameRepo(it.url, url) }) {
            throw SourceException("This repository is already added.")
        }
        val parsed = fetchRepository(url)
        applyParsed(url, parsed, builtIn = false)
        val note = parsed.note
        post(
            if (note != null) "Repository added. Note: $note"
            else "Repository added with ${parsed.sources.size} sources."
        )
    }

    fun refreshRepository(url: String) = launchTask("repo:$url") {
        val error = refreshOne(url)
        post(error ?: "Repository updated.")
    }

    fun refreshAll() = launchTask("repo:all") {
        val repos = _repositories.value.filter { it.enabled }
        var failed = 0
        for (repo in repos) {
            if (refreshOne(repo.url) != null) failed++
        }
        post(
            if (failed == 0) "Refreshed ${repos.size} repositories."
            else "Refreshed ${repos.size - failed} of ${repos.size} repositories. $failed could not be reached."
        )
    }

    fun setRepositoryEnabled(url: String, enabled: Boolean) = launchTask(null) {
        lock.withLock {
            _repositories.update { list -> list.map { if (it.url == url) it.copy(enabled = enabled) else it } }
            persistRepositories()
        }
    }

    fun removeRepository(url: String) = launchTask(null) {
        val repo = _repositories.value.firstOrNull { it.url == url } ?: return@launchTask
        if (repo.builtIn) throw SourceException("The built-in repository cannot be removed.")
        lock.withLock {
            _repositories.update { list -> list.filter { it.url != url } }
            _available.update { list -> list.filter { it.repositoryUrl != url } }
            persistRepositories()
        }
        post("Repository removed. Sources you already installed stay installed.")
    }

    fun install(source: AvailableSource) = launchTask("source:${source.info.id}") {
        if (!source.supported) {
            throw SourceException(source.unsupportedReason ?: RepositoryParser.UNSUPPORTED_FORMAT)
        }
        val info = source.info
        val http = SafeHttp(info.allowedHosts)
        val adapter = AdapterRegistry.create(info, http)
            ?: throw SourceException(RepositoryParser.UNSUPPORTED_FORMAT)
        val health = verifier.verify(adapter, http)
        lock.withLock {
            val previous = _installed.value.firstOrNull { it.info.id == info.id }
            val entry = InstalledSource(
                info = info,
                repositoryUrl = source.repositoryUrl,
                enabled = previous?.enabled ?: true,
                health = health,
                installedAt = previous?.installedAt ?: System.currentTimeMillis()
            )
            _installed.update { list -> list.filter { it.info.id != info.id } + entry }
            synchronized(adapterCache) { adapterCache.clear() }
            persistInstalled()
        }
        post("${info.name} installed. Status: ${health.status.label()}.")
    }

    fun uninstall(id: String) = launchTask(null) {
        lock.withLock {
            _installed.update { list -> list.filter { it.info.id != id } }
            synchronized(adapterCache) { adapterCache.clear() }
            persistInstalled()
        }
    }

    fun setEnabled(id: String, enabled: Boolean) = launchTask(null) {
        lock.withLock {
            _installed.update { list -> list.map { if (it.info.id == id) it.copy(enabled = enabled) else it } }
            synchronized(adapterCache) { adapterCache.clear() }
            persistInstalled()
        }
    }

    fun runTest(id: String) = launchTask("source:$id") {
        val inst = _installed.value.firstOrNull { it.info.id == id } ?: return@launchTask
        val http = SafeHttp(inst.info.allowedHosts)
        val adapter = AdapterRegistry.create(inst.info, http)
        val health = if (adapter == null) {
            SourceHealth.unsupported(RepositoryParser.UNSUPPORTED_FORMAT)
        } else {
            verifier.verify(adapter, http)
        }
        lock.withLock {
            _installed.update { list -> list.map { if (it.info.id == id) it.copy(health = health) else it } }
            persistInstalled()
        }
        post("${inst.info.name}: ${health.status.label()}.")
    }

    // ------------------------------------------------------------ internals

    private suspend fun loadInitial() {
        try {
            withContext(Dispatchers.IO) {
                _repositories.value = store.loadRepositories()
                _installed.value = store.loadInstalled()
                _available.value = store.loadAvailable()
            }
            // The built-in list ships inside the app, so it can be refreshed without internet.
            val parsed = fetchRepository(BUILTIN_URL)
            applyParsed(BUILTIN_URL, parsed, builtIn = true)
        } catch (e: Exception) {
            Log.w(TAG, "Initial load problem", e)
        } finally {
            ready.complete(Unit)
        }
    }

    private fun launchTask(busyKey: String?, block: suspend () -> Unit) {
        scope.launch {
            ready.await()
            if (busyKey != null) {
                val before = _busy.getAndUpdate { it + busyKey }
                if (busyKey in before) return@launch
            }
            try {
                block()
            } catch (e: SourceException) {
                post(e.userMessage)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Task failed", e)
                post("Something went wrong. Please try again.")
            } finally {
                if (busyKey != null) _busy.update { it - busyKey }
            }
        }
    }

    private fun post(text: String) {
        _message.value = text
    }

    /** Re-downloads one repository. Returns an error message, or null on success. */
    private suspend fun refreshOne(url: String): String? {
        return try {
            val parsed = fetchRepository(url)
            applyParsed(url, parsed, _repositories.value.firstOrNull { it.url == url }?.builtIn ?: false)
            null
        } catch (e: SourceException) {
            lock.withLock {
                _repositories.update { list -> list.map { if (it.url == url) it.copy(error = e.userMessage) else it } }
                persistRepositories()
            }
            e.userMessage
        }
    }

    private suspend fun fetchRepository(url: String): ParsedRepository {
        val builtIn = url == BUILTIN_URL
        val text = if (builtIn) {
            withContext(Dispatchers.IO) {
                appContext.assets.open(BUILTIN_ASSET).bufferedReader().use { it.readText() }
            }
        } else {
            SafeHttp(null).getString(indexUrl(url), maxBytes = 5_000_000)
        }
        val fallback = if (builtIn) "Hander Official" else try { URL(url).host } catch (e: Exception) { url }
        return RepositoryParser.parse(url, text, fallback)
    }

    private suspend fun applyParsed(url: String, parsed: ParsedRepository, builtIn: Boolean) {
        lock.withLock {
            val existing = _repositories.value.firstOrNull { it.url == url }
            val info = RepositoryInfo(
                url = url,
                name = parsed.name,
                enabled = existing?.enabled ?: true,
                builtIn = builtIn,
                lastUpdated = System.currentTimeMillis(),
                sourceCount = parsed.sources.size,
                error = parsed.note
            )
            _repositories.update { list ->
                if (existing != null) list.map { if (it.url == url) info else it } else list + info
            }
            _available.update { list -> list.filter { it.repositoryUrl != url } + parsed.sources }
            persistRepositories()
        }
    }

    private suspend fun persistRepositories() = withContext(Dispatchers.IO) {
        store.saveRepositories(_repositories.value)
        store.saveAvailable(_available.value)
    }

    private suspend fun persistInstalled() = withContext(Dispatchers.IO) {
        store.saveInstalled(_installed.value)
    }

    private fun normalizeRepoUrl(input: String): String {
        var text = input.trim()
        if (text.isEmpty()) throw SourceException("Please enter a repository address.")
        if (!text.contains("://")) text = "https://$text"
        if (!text.startsWith("https://", ignoreCase = true)) {
            throw SourceException("Only secure addresses starting with https:// are allowed.")
        }
        val host = try { URL(text).host } catch (e: Exception) { "" }
        if (host.isNullOrEmpty()) throw SourceException("That address does not look valid.")
        return text
    }

    private fun indexUrl(repoUrl: String): String {
        val bare = repoUrl.substringBefore('?').substringBefore('#')
        return if (bare.endsWith(".json", ignoreCase = true)) repoUrl else repoUrl.trimEnd('/') + "/index.json"
    }

    private fun sameRepo(a: String, b: String): Boolean =
        a.trimEnd('/').equals(b.trimEnd('/'), ignoreCase = true)

    companion object {
        const val BUILTIN_URL = "builtin://hander"
        private const val BUILTIN_ASSET = "builtin_repository.json"
        private const val TAG = "SourceManager"

        @Volatile
        private var instance: SourceManager? = null

        fun get(context: Context): SourceManager =
            instance ?: synchronized(this) {
                instance ?: SourceManager(context.applicationContext).also { instance = it }
            }
    }
}
