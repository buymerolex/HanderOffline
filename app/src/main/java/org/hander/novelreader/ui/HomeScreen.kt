package org.hander.novelreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import org.hander.novelreader.data.LibraryState
import org.hander.novelreader.data.LocalBook
import org.hander.novelreader.theme.HanderColors

@Composable
fun HomeScreen(
    library: LibraryState,
    onOpen: (LocalBook) -> Unit,
    onBrowseLibraries: () -> Unit,
    onOpenOffline: () -> Unit
) {
    val recents = library.recents
    val favorites = library.favorites
    val continueReading = library.continueReading

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text(
            "HANDER",
            color = HanderColors.Gold,
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp
        )
        Spacer(Modifier.height(24.dp))

        if (continueReading != null) {
            SectionTitle("Continue reading")
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(HanderColors.Panel)
                    .clickable { onOpen(continueReading) }
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        continueReading.title,
                        color = HanderColors.Text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Page ${continueReading.lastPage + 1} of ${continueReading.pageCount}",
                        color = HanderColors.Accent2,
                        fontSize = 12.sp
                    )
                }
                Button(
                    onClick = { onOpen(continueReading) },
                    colors = ButtonDefaults.buttonColors(containerColor = HanderColors.Accent)
                ) {
                    Text("RESUME", color = HanderColors.Background, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(28.dp))
        }

        SectionTitle("Recent books")
        if (recents.isEmpty()) {
            EmptyHint("Novels and PDFs you open will show up here.")
        } else {
            BookRow(recents, onOpen)
        }

        Spacer(Modifier.height(28.dp))

        SectionTitle("Favorites")
        if (favorites.isEmpty()) {
            EmptyHint("Tap the favorite icon on a book to keep it here.")
        } else {
            BookRow(favorites, onOpen)
        }

        Spacer(Modifier.height(28.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onBrowseLibraries,
                colors = ButtonDefaults.buttonColors(containerColor = HanderColors.Panel),
                modifier = Modifier.weight(1f)
            ) {
                Text("Browse Libraries", color = HanderColors.Gold)
            }
            Button(
                onClick = onOpenOffline,
                colors = ButtonDefaults.buttonColors(containerColor = HanderColors.Panel),
                modifier = Modifier.weight(1f)
            ) {
                Text("Offline Folders", color = HanderColors.Gold)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        color = HanderColors.Accent2,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp
    )
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun EmptyHint(text: String) {
    Text(text, color = HanderColors.Border, fontSize = 13.sp)
}

@Composable
private fun BookRow(books: List<LocalBook>, onOpen: (LocalBook) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(books, key = { it.uri }) { book ->
            Column(
                Modifier
                    .width(110.dp)
                    .clickable { onOpen(book) }
            ) {
                Box(
                    Modifier
                        .width(110.dp)
                        .height(150.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(HanderColors.Background2)
                ) {
                    if (book.coverPath != null) {
                        AsyncImage(
                            model = book.coverPath,
                            contentDescription = book.title,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    book.title,
                    color = HanderColors.Text,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
