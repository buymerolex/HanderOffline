package org.hander.novelreader.reader

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.hander.novelreader.data.Storage
import org.hander.novelreader.model.BookKind
import java.util.Locale

/**
 * One global reading session. The UI, the MediaSession notification and
 * the volume-key handler all read/control the exact same engine, so
 * play/pause state is always in sync no matter which of those triggered it.
 */
object ReaderEngine {
    private lateinit var appCtx: Context
    private lateinit var scope: CoroutineScope
    lateinit var storage: Storage
        private set

    private var initialized = false

    fun init(context: Context, externalScope: CoroutineScope) {
        if (initialized) return
        appCtx = context.applicationContext
        scope = externalScope
        storage = Storage(appCtx)
        PDFBoxResourceLoader.init(appCtx)
        initTts()
        initialized = true
    }

    // ---------------- current book ----------------
    var bookId by mutableStateOf("")
        private set
    var bookTitle by mutableStateOf("")
        private set
    var bookKind by mutableStateOf(BookKind.PDF)
        private set
    var coverUrl by mutableStateOf("")
        private set

    var unitIndex by mutableIntStateOf(0)   // page (PDF) or chapter (downloaded)
        private set
    var unitCount by mutableIntStateOf(0)
        private set
    var unitLabel by mutableStateOf("")     // "Chapter 12" if detected
        private set

    var currentText by mutableStateOf("")
        private set
    var hlStart by mutableIntStateOf(-1)
    var hlEnd by mutableIntStateOf(-1)
    var selStart by mutableIntStateOf(-1)
    var selEnd by mutableIntStateOf(-1)

    var loading by mutableStateOf(false)
        private set
    var errorMsg by mutableStateOf<String?>(null)
        private set

    var playing by mutableStateOf(false)
        private set

    /** For downloaded novels: all chapter labels, used by the chapter picker. */
    val chapterLabels: List<String>
        get() = downloadedChapters.map { it.first }

    // listeners notified on play/pause/track change (used by the notification service)
    private val listeners = mutableListOf<() -> Unit>()
    fun addListener(l: () -> Unit) { listeners.add(l) }
    fun removeListener(l: () -> Unit) { listeners.remove(l) }
    private fun notifyListeners() { listeners.forEach { it() } }

    private var pdfDoc: PDDocument? = null
    private val docLock = Mutex()
    private var downloadedChapters: List<Pair<String, String>> = emptyList()
    private var pageJob: Job? = null

    // ---------------- open a PDF (offline) ----------------

    fun openPdf(uri: Uri, title: String) {
        stop()
        bookId = uri.toString()
        bookTitle = title
        bookKind = BookKind.PDF
        coverUrl = ""
        currentText = ""; unitCount = 0; unitIndex = 0; unitLabel = ""
        errorMsg = null
        pageJob?.cancel()
        pageJob = scope.launch {
            loading = true
            try {
                docLock.withLock {
                    withContext(Dispatchers.IO) {
                        pdfDoc?.close(); pdfDoc = null
                        appCtx.contentResolver.openInputStream(uri)?.use { pdfDoc = PDDocument.load(it) }
                    }
                }
                val count = pdfDoc?.numberOfPages ?: 0
                if (count == 0) { errorMsg = "Could not open this PDF"; return@launch }
                unitCount = count
                showPdfPage(storage.savedPosition(bookId).coerceIn(0, count - 1))
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                errorMsg = "Could not open this PDF"
            } finally {
                loading = false
            }
        }
    }

    private suspend fun showPdfPage(index: Int) {
        val d = pdfDoc ?: return
        val i = index.coerceIn(0, (unitCount - 1).coerceAtLeast(0))
        val text = docLock.withLock {
            withContext(Dispatchers.IO) {
                PDFTextStripper().apply { startPage = i + 1; endPage = i + 1 }.getText(d)
            }
        }
        unitIndex = i
        currentText = text
        unitLabel = ""
        hlStart = -1; hlEnd = -1; selStart = -1; selEnd = -1
        storage.savePosition(bookId, i)
        notifyListeners()
    }

    // ---------------- open a downloaded novel (online source) ----------------

    fun openDownloaded(novelId: String, title: String, cover: String) {
        stop()
        pageJob?.cancel()
        pageJob = scope.launch {
            loading = true
            try {
                val chapters = withContext(Dispatchers.IO) { storage.loadChapters(novelId) }
                if (chapters.isEmpty()) {
                    bookId = novelId; bookTitle = title; bookKind = BookKind.DOWNLOADED
                    coverUrl = cover; errorMsg = "This novel hasn't been downloaded yet"
                    return@launch
                }
                openChapters(novelId, title, cover, chapters)
            } finally {
                loading = false
            }
        }
    }

    /** Opens a novel's chapters directly, without requiring them to already
     * be saved to disk - used for reading online before/without downloading. */
    fun openChapters(novelId: String, title: String, cover: String, chapters: List<Pair<String, String>>) {
        stop()
        bookId = novelId
        bookTitle = title
        bookKind = BookKind.DOWNLOADED
        coverUrl = cover
        errorMsg = null
        downloadedChapters = chapters
        unitCount = chapters.size
        if (chapters.isEmpty()) { errorMsg = "No readable text found"; return }
        showDownloadedChapter(storage.savedPosition(bookId).coerceIn(0, chapters.size - 1))
    }

    private fun showDownloadedChapter(index: Int) {
        if (downloadedChapters.isEmpty()) return
        val i = index.coerceIn(0, downloadedChapters.size - 1)
        unitIndex = i
        unitLabel = downloadedChapters[i].first
        currentText = downloadedChapters[i].second
        hlStart = -1; hlEnd = -1; selStart = -1; selEnd = -1
        storage.savePosition(bookId, i)
        notifyListeners()
    }

    // ---------------- shared navigation ----------------

    fun goTo(index: Int) {
        if (index < 0 || index >= unitCount) return
        stop()
        when (bookKind) {
            BookKind.PDF -> {
                pageJob?.cancel()
                pageJob = scope.launch { showPdfPage(index) }
            }
            BookKind.DOWNLOADED -> showDownloadedChapter(index)
        }
    }

    fun next() = goTo(unitIndex + 1)
    fun previous() = goTo(unitIndex - 1)

    fun close() {
        stop()
        bookId = ""; bookTitle = ""; currentText = ""; unitCount = 0; unitIndex = 0
        scope.launch {
            docLock.withLock { withContext(Dispatchers.IO) { pdfDoc?.close(); pdfDoc = null } }
        }
    }

    // ---------------- audio focus + notification service ----------------

    private val audioManager by lazy {
        appCtx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change == AudioManager.AUDIOFOCUS_LOSS ||
            change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
        ) stop()
    }

    private val focusRequest by lazy {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setOnAudioFocusChangeListener(focusListener)
            .build()
    }

    private fun startPlaybackService() {
        audioManager.requestAudioFocus(focusRequest)
        try {
            ContextCompat.startForegroundService(
                appCtx,
                Intent(appCtx, TtsMediaService::class.java)
            )
        } catch (_: Exception) {
        }
    }

    // ---------------- text-to-speech ----------------

    private var tts: TextToSpeech? = null
    val voices = mutableStateListOf<Voice>()

    private var chunkBase = 0
    private var chunkEnd = 0
    private val maxChunk = 3900

    private fun initTts() {
        tts = TextToSpeech(appCtx) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                tts?.language = Locale.US
                refreshVoices()
                voices.firstOrNull { it.name == storage.voiceName }?.let { tts?.voice = it }
            }
        }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onError(id: String?) {
                if (id == "hander") { playing = false; notifyListeners() }
            }
            override fun onRangeStart(id: String?, start: Int, end: Int, frame: Int) {
                if (id != "hander") return
                hlStart = chunkBase + start
                hlEnd = chunkBase + end
            }
            override fun onDone(id: String?) {
                if (id == "hander") onChunkDone()
            }
        })
    }

    private fun refreshVoices() {
        val list = try { tts?.voices?.toList() ?: emptyList() } catch (ex: Exception) { emptyList() }
        voices.clear()
        voices.addAll(
            list.filter {
                !it.isNetworkConnectionRequired &&
                        it.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) != true
            }.sortedWith(compareBy({ it.locale.displayName }, { it.name }))
        )
    }

    fun speakFrom(offset: Int) {
        if (!playing) startPlaybackService()

        val text = currentText
        val start = offset.coerceIn(0, text.length)
        selStart = -1; selEnd = -1
        if (text.substring(start).isBlank()) {
            playing = true
            onChunkDone(forceAdvance = true)
            return
        }

        var end = minOf(text.length, start + maxChunk)
        if (end < text.length) {
            val ws = maxOf(text.lastIndexOf(' ', end), text.lastIndexOf('\n', end))
            if (ws > start) end = ws
        }
        chunkBase = start
        chunkEnd = end
        tts?.setSpeechRate(storage.speed)
        tts?.setPitch(storage.pitch)
        tts?.speak(text.substring(start, end), TextToSpeech.QUEUE_FLUSH, null, "hander")
        playing = true
        notifyListeners()
    }

    private fun onChunkDone(forceAdvance: Boolean = false) {
        if (!playing && !forceAdvance) return
        if (!forceAdvance && chunkEnd < currentText.length) { speakFrom(chunkEnd); return }
        if (unitIndex < unitCount - 1) {
            pageJob?.cancel()
            pageJob = scope.launch {
                when (bookKind) {
                    BookKind.PDF -> showPdfPage(unitIndex + 1)
                    BookKind.DOWNLOADED -> showDownloadedChapter(unitIndex + 1)
                }
                if (playing) speakFrom(0)
            }
        } else {
            playing = false
            notifyListeners()
            audioManager.abandonAudioFocusRequest(focusRequest)
        }
    }

    fun toggle() { if (playing) stop() else speakFrom(if (hlStart >= 0) hlStart else 0) }

    fun stop() {
        tts?.stop()
        if (playing) { playing = false; notifyListeners() }
        if (::appCtx.isInitialized) audioManager.abandonAudioFocusRequest(focusRequest)
    }

    fun selectVoice(v: Voice) {
        stop()
        storage.voiceName = v.name
        tts?.voice = v
        tts?.setSpeechRate(storage.speed); tts?.setPitch(storage.pitch)
        tts?.speak("Hello, this is how I sound.", TextToSpeech.QUEUE_FLUSH, null, "preview")
    }

    fun applySpeechSettings() {
        if (playing) speakFrom(if (hlStart >= 0) hlStart else 0)
    }
}