package org.hander.novelreader.reader

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.hander.novelreader.HanderApp
import org.hander.novelreader.audio.PlaybackBridge
import org.hander.novelreader.audio.TtsManager
import java.io.File

data class ReaderUiState(
    val bookUri: String = "",
    val title: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val pageIndex: Int = 0,
    val pageCount: Int = 0,
    val pageText: String = "",
    val pageLoading: Boolean = false,
)

/**
 * The single reader. Today it is fed by PDF pages; later phases feed it online and
 * downloaded chapters through the same screen and the same TTS.
 */
class ReaderViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as HanderApp).container
    private val library = container.library
    private val tts = container.tts

    private val _ui = MutableStateFlow(ReaderUiState())
    val ui: StateFlow<ReaderUiState> = _ui.asStateFlow()
    val speech: StateFlow<TtsManager.State> = tts.state

    private var handle: PdfDocumentHandle? = null
    private var loadJob: Job? = null
    private var pageJob: Job? = null
    private var coverPath: String? = null

    /** What the notification, lock screen and headset buttons use to control reading. */
    private val controls = object : PlaybackBridge.Controls {
        override fun play() {
            if (tts.state.value.status != TtsManager.Status.PLAYING) togglePlay()
        }

        override fun pause() {
            if (tts.state.value.status == TtsManager.Status.PLAYING) tts.pause()
        }

        override fun next() = nextPage()
        override fun previous() = previousPage()
        override fun stop() = stopSpeech()
    }

    init {
        tts.onPageFinished = { onSpeechFinished() }
        PlaybackBridge.controls = controls
    }

    private fun publishNowPlaying() {
        val u = _ui.value
        if (u.bookUri.isEmpty() || u.pageCount == 0) return
        PlaybackBridge.nowPlaying.value = PlaybackBridge.NowPlaying(
            title = u.title,
            subtitle = "Page ${u.pageIndex + 1} of ${u.pageCount}",
            coverPath = coverPath,
        )
    }

    fun open(uriString: String, title: String) {
        val current = _ui.value
        if (current.bookUri == uriString && handle != null && current.error == null) return

        tts.stop()
        loadJob?.cancel()
        pageJob?.cancel()
        coverPath = null
        _ui.value = ReaderUiState(bookUri = uriString, title = title, loading = true)

        loadJob = viewModelScope.launch {
            try {
                val opened = withContext(Dispatchers.IO) {
                    handle?.close()
                    handle = null
                    PdfDocumentHandle.open(getApplication(), Uri.parse(uriString))
                }
                handle = opened

                val cover = File(
                    getApplication<Application>().filesDir,
                    "covers/${Integer.toHexString(uriString.hashCode())}.jpg",
                )
                withContext(Dispatchers.IO) { if (!cover.exists()) opened.renderCover(cover) }
                coverPath = if (cover.exists()) cover.absolutePath else null
                library.markOpened(uriString, title, opened.pageCount, coverPath)

                val start = (library.get(uriString)?.lastPage ?: 0).coerceIn(0, maxOf(0, opened.pageCount - 1))
                _ui.update { it.copy(loading = false, pageCount = opened.pageCount) }
                showPage(start, speak = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.e("ReaderViewModel", "Could not open PDF", e)
                _ui.update { it.copy(loading = false, error = "This PDF could not be opened.") }
            }
        }
    }

    private fun showPage(index: Int, speak: Boolean) {
        val h = handle ?: return
        if (h.pageCount == 0) return
        val i = index.coerceIn(0, h.pageCount - 1)
        pageJob?.cancel()
        _ui.update { it.copy(pageIndex = i, pageLoading = true) }
        pageJob = viewModelScope.launch {
            val text = try {
                h.pageText(i)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.w("ReaderViewModel", "Could not read page $i", e)
                ""
            }
            _ui.update { it.copy(pageText = text, pageLoading = false) }
            library.saveProgress(_ui.value.bookUri, i, h.pageCount)
            publishNowPlaying()
            if (speak) {
                if (text.isNotBlank()) {
                    tts.play(text)
                    PlaybackBridge.advancing = false
                } else if (i < h.pageCount - 1) {
                    showPage(i + 1, speak = true)
                } else {
                    PlaybackBridge.advancing = false
                }
            } else {
                PlaybackBridge.advancing = false
            }
        }
    }

    private fun onSpeechFinished() {
        val s = container.settings.state.value
        val u = _ui.value
        if (s.autoNextPage && u.pageIndex < u.pageCount - 1) {
            PlaybackBridge.advancing = true
            showPage(u.pageIndex + 1, speak = true)
        }
    }

    fun goToPage(index: Int) {
        val wasPlaying = tts.state.value.status == TtsManager.Status.PLAYING
        tts.stop()
        showPage(index, speak = wasPlaying)
    }

    fun nextPage() = goToPage(_ui.value.pageIndex + 1)
    fun previousPage() = goToPage(_ui.value.pageIndex - 1)

    fun togglePlay() {
        when (tts.state.value.status) {
            TtsManager.Status.PLAYING -> tts.pause()
            TtsManager.Status.PAUSED -> tts.resume()
            TtsManager.Status.IDLE -> {
                val text = _ui.value.pageText
                if (text.isNotBlank()) tts.play(text)
            }
        }
    }

    fun playFrom(offset: Int) {
        val text = _ui.value.pageText
        if (text.isNotBlank()) tts.play(text, offset)
    }

    fun stopSpeech() {
        PlaybackBridge.advancing = false
        tts.stop()
    }

    override fun onCleared() {
        if (PlaybackBridge.controls === controls) PlaybackBridge.controls = null
        PlaybackBridge.nowPlaying.value = null
        PlaybackBridge.advancing = false
        tts.stop()
        tts.onPageFinished = null
        handle?.close()
        handle = null
    }
}