package org.hander.novelreader.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.hander.novelreader.data.LibraryState
import org.hander.novelreader.data.LocalBook
import org.hander.novelreader.data.PdfFile
import org.hander.novelreader.theme.HanderColors

@Composable
fun OfflineScreen(
    library: LibraryState,
    files: List<PdfFile>,
    scanning: Boolean,
    onFolderPicked: (Uri) -> Unit,
    onRescan: () -> Unit,
    onOpenPdf: (PdfFile) -> Unit,
    onToggleFavorite: (LocalBook) -> Unit
) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) onFolderPicked(uri)
    }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text(
            "OFFLINE",
            color = HanderColors.Gold,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "LOCAL BOOKS",
                        color = HanderColors.Accent2,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (library.folderUri != null) {
                            Text(
                                "Rescan",
                                color = HanderColors.Accent,
                                fontSize = 12.sp,
                                modifier = Modifier.clickable { onRescan() }
                            )
                        }
                        Text(
                            if (library.folderUri == null) "Choose Folder" else "Change Folder",
                            color = HanderColors.Accent,
                            fontSize = 12.sp,
                            modifier = Modifier.clickable { picker.launch(null) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            if (library.folderUri == null) {
                item {
                    Text(
                        "Choose a folder on your device containing PDF or EPUB novels.",
                        color = HanderColors.Border,
                        fontSize = 13.sp
                    )
                }
            } else if (scanning) {
                item {
                    Box(Modifier.fillMaxWidth().padding(top = 20.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = HanderColors.Accent)
                    }
                }
            } else if (files.isEmpty()) {
                item {
                    Text("No PDF or EPUB files found in this folder.", color = HanderColors.Accent2, fontSize = 13.sp)
                }
            } else {
                items(files, key = { it.uri.toString() }) { file ->
                    BookRow(file, onOpenPdf)
                }
            }

            item {
                Spacer(Modifier.height(24.dp))
                Text(
                    "DOWNLOADED NOVELS",
                    color = HanderColors.Accent2,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(8.dp))
            }

            val downloadedBooks = library.books.values.filter { it.pageCount > 0 }
            if (downloadedBooks.isEmpty()) {
                item {
                    Text(
                        "Novels downloaded from Libraries will show up here.",
                        color = HanderColors.Border,
                        fontSize = 12.sp
                    )
                }
            } else {
                items(downloadedBooks, key = { it.uri }) { book ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(book.title, color = HanderColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("Page ${book.lastPage + 1} of ${book.pageCount}", color = HanderColors.Accent2, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BookRow(file: PdfFile, onClick: (PdfFile) -> Unit) {
    val cover = rememberLocalCover(file)

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick(file) }
            .padding(8.dp)
    ) {
        Box(
            Modifier
                .width(56.dp)
                .height(78.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(HanderColors.Background2)
        ) {
            cover?.let {
                Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.align(Alignment.CenterVertically)) {
            Text(
                file.title,
                color = HanderColors.Text,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val size = "${maxOf(1L, file.sizeBytes / 1024 / 1024)} MB"
            Text(
                if (file.isEpub) "EPUB · $size" else "PDF · $size",
                color = HanderColors.Accent2,
                fontSize = 12.sp
            )
        }
    }
}