package org.hander.novelreader.audio

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.content.ContextCompat
import org.hander.novelreader.data.SettingsStore
import org.hander.novelreader.reader.splitIntoChunks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VoiceOption(val id: String, val label: String, val tag: String)

/**
 * Wraps Android TextToSpeech. Text is spoken sentence by sentence so we can highlight
 * the sentence/word being spoken and pause/resume from the right place.
 * When speech starts it also starts PlaybackService (notification, lock screen, headset buttons).
 */
class TtsManager(context: Context, private val settings: SettingsStore) {

    enum class Status { IDLE, PLAYING, PAUSED }

    data class State(
        val status: Status = Status.IDLE,
        val chunkStart: Int = -1,
        val chunkEnd: Int = -1,
        val wordStart: Int = -1,
        val wordEnd: Int = -1,
    )

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _voices = MutableStateFlow<List<VoiceOption>>(emptyList())
    val voices: StateFlow<List<VoiceOption>> = _voices.asStateFlow()

    /** Called on the main thread when the whole text has been spoken. */
    var onPageFinished: (() -> Unit)? = null

    private var tts: TextToSpeech? = null
    private var text: String = ""
    private var chunks: List<Pair<Int, Int>> = emptyList()

    @Volatile
    private var currentChunk = 0

    @Volatile
    private var session = 0

    init {
        tts = TextToSpeech(appContext) { status ->
            main.post { onEngineReady(status) }
        }
    }

    private fun onEngineReady(status: Int) {
        val engine = tts ?: return
        if (status != TextToSpeech.SUCCESS) return
        engine.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        engine.setOnUtteranceProgressListener(listener)
        loadVoices()
        applyVoiceParams()
        _ready.value = true
        scope.launch {
            settings.state
                .map { Triple(it.voiceName, it.speechRate, it.pitch) }
                .distinctUntilChanged()
                .collect {
                    applyVoiceParams()
                    if (_state.value.status == Status.PLAYING) speakFrom(currentChunk)
                }
        }
    }

    private fun loadVoices() {
        var raw: Set<android.speech.tts.Voice> = emptySet()
        try {
            raw = tts?.voices ?: emptySet()
        } catch (e: Exception) {
            // Some engines throw here; just show no voice list.
        }
        val usable = raw
            .filter { v -> !(v.features?.contains("notInstalled") ?: false) }
            .sortedWith(compareBy({ it.locale.displayName }, { it.name }))
        val totals = usable.groupingBy { it.locale.displayName }.eachCount()
        val seen = HashMap<String, Int>()
        _voices.value = usable.map { v ->
            val base = v.locale.displayName
            val n = (seen[base] ?: 0) + 1
            seen[base] = n
            var label = if ((totals[base] ?: 1) > 1) "$base \u00B7 Voice $n" else base
            if (v.isNetworkConnectionRequired) label += " \u00B7 online"
            VoiceOption(v.name, label, v.locale.toLanguageTag())
        }
    }

    private fun applyVoiceParams() {
        val engine = tts ?: return
        val s = settings.state.value
        try {
            engine.setSpeechRate(s.speechRate)
            engine.setPitch(s.pitch)
            val name = s.voiceName
            if (name != null) {
                engine.voices?.firstOrNull { it.name == name }?.let { engine.setVoice(it) }
            }
        } catch (e: Exception) {
            // A misbehaving engine must never crash the app.
        }
    }

    private fun parse(id: String?): Int? =
        id?.takeIf { it.startsWith("s${session}_c") }?.substringAfter("_c")?.toIntOrNull()

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            val k = parse(utteranceId) ?: return
            val c = chunks.getOrNull(k) ?: return
            currentChunk = k
            _state.value = State(Status.PLAYING, c.first, c.second)
        }

        override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
            val k = parse(utteranceId) ?: return
            val c = chunks.getOrNull(k) ?: return
            _state.update { it.copy(wordStart = c.first + start, wordEnd = c.first + end) }
        }

        override fun onDone(utteranceId: String?) {
            val k = parse(utteranceId) ?: return
            if (k >= chunks.size - 1) {
                currentChunk = 0
                _state.value = State()
                main.post { onPageFinished?.invoke() }
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            if (parse(utteranceId) != null) stop()
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            if (parse(utteranceId) != null) stop()
        }
    }

    /** Starts the notification / lock-screen / headset-button service if it isn't running. */
    private fun ensurePlaybackService() {
        if (PlaybackBridge.serviceRunning) return
        try {
            ContextCompat.startForegroundService(
                appContext,
                Intent(appContext, PlaybackService::class.java).setAction(PlaybackService.ACTION_START),
            )
        } catch (e: Exception) {
            Log.w("TtsManager", "Could not start the playback service", e)
        }
    }

    private fun speakFrom(index: Int) {
        val engine = tts ?: return
        if (chunks.isEmpty()) {
            _state.value = State()
            return
        }
        session++
        val sid = session
        val start = index.coerceIn(0, chunks.lastIndex)
        currentChunk = start
        applyVoiceParams()
        val first = chunks[start]
        _state.value = State(Status.PLAYING, first.first, first.second)
        ensurePlaybackService()
        for (k in start until chunks.size) {
            val (s, e) = chunks[k]
            engine.speak(
                text.substring(s, e),
                if (k == start) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
                null,
                "s${sid}_c$k",
            )
        }
    }

    /** Starts reading [newText], beginning at the sentence containing [fromOffset]. */
    fun play(newText: String, fromOffset: Int = 0) {
        text = newText
        chunks = splitIntoChunks(newText)
        val idx = chunks.indexOfFirst { fromOffset < it.second }
        speakFrom(if (idx < 0) 0 else idx)
    }

    fun pause() {
        if (_state.value.status != Status.PLAYING) return
        session++
        tts?.stop()
        _state.update { it.copy(status = Status.PAUSED, wordStart = -1, wordEnd = -1) }
    }

    fun resume() {
        if (_state.value.status == Status.PAUSED) speakFrom(currentChunk)
    }

    fun stop() {
        session++
        tts?.stop()
        currentChunk = 0
        _state.value = State()
    }

    /** Speaks a short sample so the user can hear a voice. */
    fun preview(sample: String) {
        val engine = tts ?: return
        stop()
        applyVoiceParams()
        engine.speak(sample, TextToSpeech.QUEUE_FLUSH, null, "preview")
    }
}