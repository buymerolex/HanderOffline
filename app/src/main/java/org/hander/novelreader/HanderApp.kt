package org.hander.novelreader

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import org.hander.novelreader.audio.TtsManager
import org.hander.novelreader.data.LibraryStore
import org.hander.novelreader.data.SettingsStore
import org.hander.novelreader.network.NetworkMonitor

/** Long-lived objects shared by the whole app. */
class AppContainer(app: Application) {
    val settings = SettingsStore(app)
    val library = LibraryStore(app)
    val network = NetworkMonitor(app)
    val tts = TtsManager(app, settings)
}

class HanderApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
        container = AppContainer(this)
    }
}
