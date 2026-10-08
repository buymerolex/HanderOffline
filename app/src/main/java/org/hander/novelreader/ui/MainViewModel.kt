package org.hander.novelreader.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.hander.novelreader.HanderApp
import org.hander.novelreader.audio.VoiceOption
import org.hander.novelreader.data.FolderScanner
import org.hander.novelreader.data.HanderSettings
import org.hander.novelreader.data.LibraryState
import org.hander.novelreader.data.PdfFile

/** State for Home, Offline and Settings. */
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as HanderApp).container

    val library: StateFlow<LibraryState> = container.library.state
    val settings: StateFlow<HanderSettings> = container.settings.state
    val online: StateFlow<Boolean> = container.network.online
    val voices: StateFlow<List<VoiceOption>> = container.tts.voices

    private val _files = MutableStateFlow<List<PdfFile>>(emptyList())
    val files: StateFlow<List<PdfFile>> = _files.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private var scanJob: Job? = null

    init {
        rescan()
    }

    fun rescan() {
        val folder = library.value.folderUri
        scanJob?.cancel()
        if (folder == null) {
            _files.value = emptyList()
            return
        }
        scanJob = viewModelScope.launch {
            _scanning.value = true
            _files.value = FolderScanner.scan(getApplication(), Uri.parse(folder))
            _scanning.value = false
        }
    }

    fun onFolderPicked(uri: Uri) {
        try {
            getApplication<Application>().contentResolver
                .takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: SecurityException) {
            Log.w("MainViewModel", "Could not keep access to the folder", e)
        }
        container.library.setFolder(uri.toString())
        rescan()
    }

    fun updateSettings(change: (HanderSettings) -> HanderSettings) = container.settings.update(change)

    fun toggleFavorite(uri: String, title: String) = container.library.toggleFavorite(uri, title)

    fun clearRecents() = container.library.clearRecents()

    fun previewVoice(sample: String) = container.tts.preview(sample)
}
