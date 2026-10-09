package org.hander.novelreader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.hander.novelreader.theme.HanderColors

@Composable
fun ChapterPickerDialog(
    titles: List<String>,
    current: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (current - 3).coerceAtLeast(0)
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HanderColors.Panel,
        title = { Text("Chapters", color = HanderColors.Text) },
        text = {
            LazyColumn(state = listState) {
                itemsIndexed(titles) { index, title ->
                    Text(
                        text = title.ifBlank { "Section ${index + 1}" },
                        color = if (index == current) HanderColors.Gold else HanderColors.Text,
                        fontWeight = if (index == current) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onPick(index)
                                onDismiss()
                            }
                            .padding(vertical = 12.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("CLOSE", color = HanderColors.Accent) }
        },
    )
}