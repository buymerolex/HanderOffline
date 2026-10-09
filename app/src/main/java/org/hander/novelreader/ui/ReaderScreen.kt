package org.hander.novelreader.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.hander.novelreader.audio.TtsManager
import org.hander.novelreader.data.HanderSettings
import org.hander.novelreader.reader.ReaderViewModel
import org.hander.novelreader.theme.HanderColors

@Composable
fun ReaderScreen(
    vm: ReaderViewModel,
    settings: HanderSettings,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onChangeSettings: ((HanderSettings) -> HanderSettings) -> Unit,
    onBack: () -> Unit
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val speech by vm.speech.collectAsStateWithLifecycle()

    var showSettings by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    var showChapters by remember { mutableStateOf(false) }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var viewportW by remember { mutableStateOf(0) }
    var selStart by remember { mutableStateOf(-1) }
    var selEnd by remember { mutableStateOf(-1) }

    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val context = LocalContext.current
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pendingAction?.invoke()
        pendingAction = null
    }
    fun withNotificationPermission(action: () -> Unit) {
        val needsAsk = Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
        if (needsAsk) {
            pendingAction = action
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            action()
        }
    }

    BackHandler {
        onBack()
    }

    LaunchedEffect(ui.pageIndex) {
        scroll.scrollTo(0)
        selStart = -1
        selEnd = -1
    }

    val styled = remember(ui.pageText, speech, selStart, selEnd) {
        val text = ui.pageText
        buildAnnotatedString {
            append(text)
            val len = text.length
            if (selStart in 0 until selEnd && selEnd <= len) {
                addStyle(SpanStyle(background = HanderColors.Accent.copy(alpha = 0.35f)), selStart, selEnd)
            }
            val cs = speech.chunkStart
            val ce = speech.chunkEnd
            if (cs >= 0 && ce <= len && cs < ce) {
                addStyle(SpanStyle(background = HanderColors.Gold, color = HanderColors.Background), cs, ce)
            }
        }
    }

    Column(Modifier.fillMaxSize().background(HanderColors.Background)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text("‹", fontSize = 28.sp, color = HanderColors.Accent)
            }
            Text(
                ui.title, Modifier.weight(1f), maxLines = 1,
                overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold,
                color = HanderColors.Text
            )
            IconButton(onClick = onToggleFavorite) {
                Text(if (isFavorite) "♥" else "♡", fontSize = 20.sp, color = if (isFavorite) HanderColors.Gold else HanderColors.Accent2)
            }
            TextButton(onClick = { showSettings = true }) {
                Text("⋮", fontSize = 22.sp, color = HanderColors.Accent2)
            }
        }

        Box(
            Modifier.weight(1f).fillMaxWidth().onSizeChanged { viewportW = it.width }
        ) {
            Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                Box(Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
                    if (ui.pageText.isBlank() && !ui.loading && !ui.pageLoading) {
                        Text(
                            ui.error ?: "No text on this page.",
                            color = HanderColors.Accent2.copy(alpha = 0.7f)
                        )
                    }
                    if (ui.loading || ui.pageLoading) {
                        Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = HanderColors.Accent)
                        }
                    } else {
                        Text(
                            text = styled,
                            fontSize = settings.textSize.sp,
                            lineHeight = (settings.textSize * 1.5f).sp,
                            color = HanderColors.Text,
                            onTextLayout = { layout = it },
                            modifier = Modifier.pointerInput(ui.pageText) {
                                detectTapGestures { pos ->
                                    val l = layout ?: return@detectTapGestures
                                    val len = ui.pageText.length
                                    if (len == 0) return@detectTapGestures
                                    val off = l.getOffsetForPosition(pos).coerceIn(0, len - 1)
                                    val r = l.getWordBoundary(off)
                                    if (r.end > r.start &&
                                        ui.pageText.substring(r.start, r.end.coerceAtMost(len)).isNotBlank()
                                    ) {
                                        selStart = r.start
                                        selEnd = r.end
                                    } else {
                                        selStart = -1
                                        selEnd = -1
                                    }
                                }
                            }
                        )

                        val l = layout
                        if (selStart >= 0 && l != null && selStart < ui.pageText.length) {
                            val box = l.getBoundingBox(selStart)
                            val popH = with(density) { 44.dp.roundToPx() }
                            val maxX = (viewportW - with(density) { (36 + 150).dp.roundToPx() }).coerceAtLeast(0)
                            val y = if (box.top - popH >= 0) box.top.toInt() - popH - 6 else box.bottom.toInt() + 6
                            Popup(
                                box.left.toInt().coerceIn(0, maxX), y
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = HanderColors.Panel,
                                    shadowElevation = 6.dp
                                ) {
                                    Text(
                                        "START READING",
                                        color = HanderColors.Gold,
                                        fontSize = 12.sp,
                                        letterSpacing = 0.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier
                                            .clickable {
                                                val start = selStart
                                                selStart = -1
                                                selEnd = -1
                                                withNotificationPermission { vm.playFrom(start) }
                                            }
                                            .padding(horizontal = 14.dp, vertical = 11.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(96.dp))
            }

            FloatingActionButton(
                onClick = {
                    if (speech.status == TtsManager.Status.PLAYING) vm.togglePlay()
                    else withNotificationPermission { vm.togglePlay() }
                },
                containerColor = HanderColors.Accent,
                contentColor = HanderColors.Background,
                shape = CircleShape,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
            ) {
                Text(if (speech.status == TtsManager.Status.PLAYING) "❚❚" else "▶", fontSize = 18.sp)
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { vm.previousPage() }) {
                Text("‹", fontSize = 28.sp, color = HanderColors.Accent2)
            }
            Box(
                Modifier
                    .widthIn(max = 220.dp)
                    .clip(RoundedCornerShape(50))
                    .background(HanderColors.Panel)
                    .clickable {
                        if (ui.chapterTitles.isNotEmpty()) showChapters = true else showPicker = true
                    }
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (ui.pageTitle.isNotBlank()) {
                        Text(
                            ui.pageTitle,
                            color = HanderColors.Text,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        "${ui.pageIndex + 1} / ${maxOf(1, ui.pageCount)}",
                        fontWeight = FontWeight.SemiBold, color = HanderColors.Gold, fontSize = 13.sp
                    )
                }
            }
            TextButton(onClick = { vm.nextPage() }) {
                Text("›", fontSize = 28.sp, color = HanderColors.Accent2)
            }
        }
    }

    if (showSettings) {
        ReaderSettingsDialog(settings, onChangeSettings) { showSettings = false }
    }
    if (showPicker) {
        UnitPickerDialog(ui.pageCount) { page ->
            vm.goToPage(page)
            showPicker = false
        }
    }
    if (showChapters) {
        ChapterPickerDialog(
            titles = ui.chapterTitles,
            current = ui.pageIndex,
            onPick = { vm.goToPage(it) },
            onDismiss = { showChapters = false }
        )
    }
}

@Composable
private fun Popup(x: Int, y: Int, content: @Composable () -> Unit) {
    Popup(
        offset = IntOffset(x, y),
        properties = PopupProperties(focusable = false)
    ) { content() }
}

@Composable
private fun UnitPickerDialog(pageCount: Int, onGo: (Int) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { onGo(0) },
        containerColor = HanderColors.Panel,
        title = { Text("Go to page", color = HanderColors.Text) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter(Char::isDigit).take(6) },
                label = { Text("Page number (1 - $pageCount)") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = {
                text.toIntOrNull()?.let { onGo((it - 1).coerceIn(0, pageCount - 1)) }
            }) { Text("GO", color = HanderColors.Accent) }
        }
    )
}

@Composable
private fun ReaderSettingsDialog(
    settings: HanderSettings,
    onChangeSettings: ((HanderSettings) -> HanderSettings) -> Unit,
    onClose: () -> Unit
) {
    var speed by remember { mutableStateOf(settings.speechRate) }
    var pitch by remember { mutableStateOf(settings.pitch) }
    var textSize by remember { mutableStateOf(settings.textSize.toFloat()) }

    AlertDialog(
        onDismissRequest = onClose,
        containerColor = HanderColors.Panel,
        title = { Text("Reader Settings", color = HanderColors.Text) },
        text = {
            Column {
                Text("Speed  ${"%.2f".format(speed)}x", color = HanderColors.Accent2, fontSize = 13.sp)
                Slider(
                    value = speed,
                    onValueChange = { speed = it },
                    onValueChangeFinished = { onChangeSettings { s -> s.copy(speechRate = speed) } },
                    valueRange = 0.5f..2f,
                    colors = SliderDefaults.colors(thumbColor = HanderColors.Gold, activeTrackColor = HanderColors.Accent)
                )
                Text("Pitch  ${"%.2f".format(pitch)}", color = HanderColors.Accent2, fontSize = 13.sp)
                Slider(
                    value = pitch,
                    onValueChange = { pitch = it },
                    onValueChangeFinished = { onChangeSettings { s -> s.copy(pitch = pitch) } },
                    valueRange = 0.5f..2f,
                    colors = SliderDefaults.colors(thumbColor = HanderColors.Gold, activeTrackColor = HanderColors.Accent)
                )
                Text("Font size  ${textSize.toInt()}", color = HanderColors.Accent2, fontSize = 13.sp)
                Slider(
                    value = textSize,
                    onValueChange = { textSize = it },
                    onValueChangeFinished = { onChangeSettings { s -> s.copy(textSize = textSize.toInt()) } },
                    valueRange = 14f..36f,
                    colors = SliderDefaults.colors(thumbColor = HanderColors.Gold, activeTrackColor = HanderColors.Accent)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) { Text("DONE", color = HanderColors.Accent) }
        }
    )
}