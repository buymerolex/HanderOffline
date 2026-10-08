package org.hander.novelreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import org.hander.novelreader.data.Storage
import org.hander.novelreader.model.Novel
import org.hander.novelreader.reader.ChapterSplitter
import org.hander.novelreader.reader.ReaderEngine
import org.hander.novelreader.source.SourceRegistry
import org.hander.novelreader.theme.HanderColors

@Composable
fun NovelScreen(nav: NavHostController, sourceId: String, novelId: String) {
    val context = LocalContext.current
    val storage = remember { Storage(context) }
    val scope = rememberCoroutineScope()

    // try to find the novel in favorites/recents/downloads first (instant),
    // otherwise re-fetch it via a quick search as a fallback.
    var novel by remember(novelId) {
        mutableStateOf(
            (storage.favorites() + storage.recents() + storage.downloadedNovels())
                .firstOrNull { it.id == novelId }
        )
    }
    var downloaded by remember(novelId) { mutableStateOf(storage.isDownloaded(novelId)) }
    var favorite by remember(novelId) { mutableStateOf(storage.isFavorite(novelId)) }
    var downloading by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf<String?>(null) }

    val source = SourceRegistry.byId(sourceId)

    fun fetchAndSplit(onReady: (List<Pair<String, String>>) -> Unit) {
        val n = novel ?: return
        val src = source ?: return
        scope.launch {
            statusMsg = "Fetching text…"
            val text = src.fetchFullText(n)
            if (text.isBlank()) {
                statusMsg = "Could not fetch this novel's text."
                return@launch
            }
            val chapters = ChapterSplitter.split(text)
            statusMsg = null
            onReady(chapters)
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("‹ Back", color = HanderColors.Accent, fontSize = 14.sp,
            modifier = Modifier.clickable { nav.popBackStack() })
        Spacer(Modifier.height(16.dp))

        if (novel == null) {
            Text("Loading…", color = HanderColors.Accent2)
            return@Column
        }
        val n = novel!!

        Row {
            Box(
                Modifier.width(120.dp).height(168.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(HanderColors.Background2)
            ) {
                if (n.coverUrl.isNotEmpty()) {
                    AsyncImage(model = n.coverUrl, contentDescription = n.title,
                        modifier = Modifier.fillMaxSize())
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(n.title, color = HanderColors.Text, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(n.author, color = HanderColors.Accent2, fontSize = 14.sp)
                if (n.status.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(n.status, color = HanderColors.Gold, fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = {
                    storage.addRecent(n)
                    if (downloaded) {
                        ReaderEngine.openDownloaded(n.id, n.title, n.coverUrl)
                        nav.navigate("reader")
                    } else {
                        fetchAndSplit { chapters ->
                            ReaderEngine.openChapters(n.id, n.title, n.coverUrl, chapters)
                            nav.navigate("reader")
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = HanderColors.Accent)
            ) { Text("READ", color = HanderColors.Background, fontWeight = FontWeight.SemiBold) }

            OutlinedButton(
                onClick = {
                    favorite = !favorite
                    storage.toggleFavorite(n)
                },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (favorite) HanderColors.Gold else HanderColors.Accent2
                )
            ) { Text(if (favorite) "♥ FAVORITED" else "♡ FAVORITE") }

            OutlinedButton(
                enabled = !downloading && !downloaded,
                onClick = {
                    downloading = true
                    fetchAndSplit { chapters ->
                        storage.saveChapters(n.id, chapters)
                        storage.markDownloaded(n)
                        downloaded = true
                        downloading = false
                    }
                },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = HanderColors.Accent2)
            ) {
                Text(
                    when {
                        downloaded -> "DOWNLOADED"
                        downloading -> "DOWNLOADING…"
                        else -> "DOWNLOAD"
                    }
                )
            }
        }

        statusMsg?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = HanderColors.Accent2, fontSize = 12.sp)
        }

        if (n.description.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            Text("DESCRIPTION", color = HanderColors.Accent2, fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
            Spacer(Modifier.height(6.dp))
            Text(n.description, color = HanderColors.Text, fontSize = 13.sp, lineHeight = 19.sp)
        }

        if (downloaded) {
            Spacer(Modifier.height(20.dp))
            Text("CHAPTERS", color = HanderColors.Accent2, fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
            Spacer(Modifier.height(8.dp))
            val chapters = remember(n.id) { storage.loadChapters(n.id) }
            val savedPos = storage.savedPosition(n.id)
            chapters.forEachIndexed { index, (label, _) ->
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            storage.addRecent(n)
                            ReaderEngine.openDownloaded(n.id, n.title, n.coverUrl)
                            ReaderEngine.goTo(index)
                            nav.navigate("reader")
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (index <= savedPos) "✓" else "",
                        color = HanderColors.Accent,
                        fontSize = 12.sp,
                        modifier = Modifier.width(20.dp)
                    )
                    Text(label, color = HanderColors.Text, fontSize = 13.sp)
                }
            }
        }

        Spacer(Modifier.height(40.dp))
    }
}
