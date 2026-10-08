package org.hander.novelreader.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.hander.novelreader.audio.VoiceOption
import org.hander.novelreader.data.HanderSettings
import org.hander.novelreader.data.LibraryState
import org.hander.novelreader.theme.HanderColors

@Composable
fun SettingsScreen(
    settings: HanderSettings,
    voices: List<VoiceOption>,
    library: LibraryState,
    onChange: ((HanderSettings) -> HanderSettings) -> Unit,
    onPreviewVoice: () -> Unit,
    onFolderPicked: (Uri) -> Unit,
    onClearRecents: () -> Unit
) {
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) onFolderPicked(uri)
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text(
            "SETTINGS",
            color = HanderColors.Gold,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.height(24.dp))

        // Speech rate
        Text("Speech Rate: ${"%.2f".format(settings.speechRate)}x", color = HanderColors.Text, fontSize = 14.sp)
        Slider(
            value = settings.speechRate,
            onValueChange = { rate -> onChange { it.copy(speechRate = rate) } },
            valueRange = 0.5f..2.0f,
            colors = SliderDefaults.colors(thumbColor = HanderColors.Gold, activeTrackColor = HanderColors.Accent)
        )

        Spacer(Modifier.height(16.dp))

        // Pitch
        Text("Pitch: ${"%.2f".format(settings.pitch)}", color = HanderColors.Text, fontSize = 14.sp)
        Slider(
            value = settings.pitch,
            onValueChange = { p -> onChange { it.copy(pitch = p) } },
            valueRange = 0.5f..1.5f,
            colors = SliderDefaults.colors(thumbColor = HanderColors.Gold, activeTrackColor = HanderColors.Accent)
        )

        Spacer(Modifier.height(16.dp))

        // Voice selection
        Text("Voice", color = HanderColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        if (voices.isEmpty()) {
            Text("No TTS voices available.", color = HanderColors.Accent2, fontSize = 12.sp)
        } else {
            Column(Modifier.heightIn(max = 185.dp).verticalScroll(rememberScrollState())) {
                voices.forEach { voice ->
                    val selected = voice.id == settings.voiceName || (settings.voiceName == null && voice == voices.firstOrNull())
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                onChange { it.copy(voiceName = voice.id) }
                                onPreviewVoice()
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (selected) "● " else "○ ",
                            color = if (selected) HanderColors.Gold else HanderColors.Accent2
                        )
                        Text(
                            voice.label,
                            color = if (selected) HanderColors.Gold else HanderColors.Text,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider(color = HanderColors.Border)
        Spacer(Modifier.height(24.dp))

        // Library folder
        Text("Offline Folder", color = HanderColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            library.folderUri ?: "No folder selected for local PDFs.",
            color = HanderColors.Accent2,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { folderPicker.launch(null) },
            colors = ButtonDefaults.buttonColors(containerColor = HanderColors.Accent)
        ) {
            Text("Pick Folder", color = HanderColors.Background)
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider(color = HanderColors.Border)
        Spacer(Modifier.height(24.dp))

        // Clear recents
        Button(
            onClick = onClearRecents,
            colors = ButtonDefaults.buttonColors(containerColor = HanderColors.Panel)
        ) {
            Text("Clear Recent History", color = HanderColors.Gold)
        }
    }
}
