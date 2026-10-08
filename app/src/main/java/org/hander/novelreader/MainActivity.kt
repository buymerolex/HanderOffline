package org.hander.novelreader

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import org.hander.novelreader.navigation.HanderRoot
import org.hander.novelreader.reader.ReaderEngine
import org.hander.novelreader.reader.ReaderViewModel
import org.hander.novelreader.theme.HanderTheme
import org.hander.novelreader.ui.MainViewModel

class MainActivity : ComponentActivity() {

    private val mainVm: MainViewModel by viewModels()
    private val readerVm: ReaderViewModel by viewModels()

    private val notifPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ReaderEngine.init(
            applicationContext,
            lifecycleScope
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notifPermissionLauncher.launch(
                    Manifest.permission.POST_NOTIFICATIONS
                )
            }
        }

        setContent {
            HanderTheme {
                HanderRoot(
                    mainVm = mainVm,
                    readerVm = readerVm
                )
            }
        }
    }

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            try {
                if (
                    ReaderEngine.storage.volumeKeyNavEnabled &&
                    ReaderEngine.bookId.isNotEmpty()
                ) {
                    when (event.keyCode) {
                        KeyEvent.KEYCODE_VOLUME_UP -> {
                            ReaderEngine.previous()
                            return true
                        }

                        KeyEvent.KEYCODE_VOLUME_DOWN -> {
                            ReaderEngine.next()
                            return true
                        }
                    }
                }
            } catch (_: UninitializedPropertyAccessException) {
                // ReaderEngine is not initialized yet.
            }
        }

        return super.dispatchKeyEvent(event)
    }
}
