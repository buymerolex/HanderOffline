package org.hander.novelreader.data

import android.content.Context
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ReaderFont(val label: String, val family: FontFamily) {
    SERIF("Serif", FontFamily.Serif),
    CLEAN("Clean", FontFamily.SansSerif),
    TYPEWRITER("Typewriter", FontFamily.Monospace),
}

data class HanderSettings(
    val textSize: Int = 21,
    val font: ReaderFont = ReaderFont.SERIF,
    val voiceName: String? = null,
    val speechRate: Float = 1.0f,
    val pitch: Float = 1.0f,
    val autoNextPage: Boolean = true,
)

/** Small key/value settings, kept in SharedPreferences. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("hander_settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<HanderSettings> = _state.asStateFlow()

    private fun load(): HanderSettings {
        val font = try {
            ReaderFont.valueOf(prefs.getString("font", ReaderFont.SERIF.name) ?: ReaderFont.SERIF.name)
        } catch (e: IllegalArgumentException) {
            ReaderFont.SERIF
        }
        return HanderSettings(
            textSize = prefs.getInt("textSize", 21).coerceIn(14, 34),
            font = font,
            voiceName = prefs.getString("voiceName", null),
            speechRate = prefs.getFloat("speechRate", 1.0f).coerceIn(0.5f, 2.0f),
            pitch = prefs.getFloat("pitch", 1.0f).coerceIn(0.5f, 1.5f),
            autoNextPage = prefs.getBoolean("autoNextPage", true),
        )
    }

    @Synchronized
    fun update(change: (HanderSettings) -> HanderSettings) {
        val next = change(_state.value)
        _state.value = next
        prefs.edit()
            .putInt("textSize", next.textSize)
            .putString("font", next.font.name)
            .putString("voiceName", next.voiceName)
            .putFloat("speechRate", next.speechRate)
            .putFloat("pitch", next.pitch)
            .putBoolean("autoNextPage", next.autoNextPage)
            .apply()
    }
}
