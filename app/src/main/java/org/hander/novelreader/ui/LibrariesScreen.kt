package org.hander.novelreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import org.hander.novelreader.model.Novel
import org.hander.novelreader.source.SourceRegistry
import org.hander.novelreader.theme.HanderColors

@Composable
fun LibrariesScreen(
    onSelectNovel: (Novel) -> Unit
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<Novel>>(emptyList()) }
    var searched by remember { mutableStateOf(false) }

    fun runSearch() {
        if (query.isBlank()) return
        searching = true
        searched = true
        scope.launch {
            val all = mutableListOf<Novel>()
            for (source in SourceRegistry.installed) {
                all.addAll(source.search(query))
            }
            results = all
            searching = false
        }
    }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text(
            "LIBRARIES",
            color = HanderColors.Gold,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.height(16.dp))

        // search bar
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(HanderColors.Panel)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(color = HanderColors.Text, fontSize = 14.sp),
                cursorBrush = SolidColor(HanderColors.Accent),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text(
                            "Search across your installed libraries…",
                            color = HanderColors.Accent2,
                            fontSize = 14.sp
                        )
                    }
                    inner()
                },
                keyboardActions = KeyboardActions(onDone = { runSearch() }),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Search
                )
            )
            Text(
                "SEARCH",
                color = HanderColors.Accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { runSearch() }
            )
        }

        Spacer(Modifier.height(18.dp))

        if (!searched) {
            Text(
                "INSTALLED",
                color = HanderColors.Accent2,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(10.dp))
            SourceRegistry.installed.forEach { source ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Text(source.name, color = HanderColors.Text, fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold)
                    Text(source.description, color = HanderColors.Accent2, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "More legal sources can be added here over time - Hander only ships " +
                    "connectors for sites that provide material legally, not scrapers for " +
                    "unauthorized copies.",
                color = HanderColors.Border,
                fontSize = 11.sp
            )
        } else if (searching) {
            Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = HanderColors.Accent)
            }
        } else if (results.isEmpty()) {
            Text("No results found.", color = HanderColors.Accent2, fontSize = 13.sp,
                modifier = Modifier.padding(top = 20.dp))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(results, key = { it.id }) { novel ->
                    SearchResultRow(novel) {
                        onSelectNovel(novel)
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(novel: Novel, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Box(
            Modifier.width(52.dp).height(72.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(HanderColors.Background2)
        ) {
            if (novel.coverUrl.isNotEmpty()) {
                AsyncImage(model = novel.coverUrl, contentDescription = novel.title,
                    modifier = Modifier.fillMaxSize())
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.align(Alignment.CenterVertically)) {
            Text(novel.title, color = HanderColors.Text, fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(novel.author, color = HanderColors.Accent2, fontSize = 12.sp)
        }
    }
}
