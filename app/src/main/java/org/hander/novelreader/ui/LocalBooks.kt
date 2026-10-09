package org.hander.novelreader.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AColor
import android.graphics.pdf.PdfRenderer
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.hander.novelreader.data.PdfFile
import org.hander.novelreader.reader.EpubDocumentHandle
import org.hander.novelreader.theme.HanderColors
import java.io.File

/** Loads the cover of a local PDF or EPUB (null while loading or if there is none). */
@Composable
fun rememberLocalCover(file: PdfFile): Bitmap? {
    val context = LocalContext.current
    var cover by remember(file.uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(file.uri) {
        cover = withContext(Dispatchers.IO) {
            runCatching {
                if (file.isEpub) {
                    val target = File(
                        context.filesDir,
                        "covers/${Integer.toHexString(file.uri.toString().hashCode())}.jpg",
                    )
                    if (!target.exists()) EpubDocumentHandle.extractCover(context, file.uri, target)
                    if (target.exists()) decodeSampled(target.absolutePath, 300) else null
                } else {
                    var bmp: Bitmap? = null
                    context.contentResolver.openFileDescriptor(file.uri, "r")?.use { pfd ->
                        PdfRenderer(pfd).use { r ->
                            if (r.pageCount > 0) {
                                r.openPage(0).use { page ->
                                    val w = 300
                                    val h = (w * page.height.toFloat() / page.width).toInt().coerceAtLeast(1)
                                    val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                    b.eraseColor(AColor.WHITE)
                                    page.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    bmp = b
                                }
                            }
                        }
                    }
                    bmp
                }
            }.getOrNull()
        }
    }
    return cover
}

private fun decodeSampled(path: String, targetWidth: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= targetWidth) sample *= 2
    return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
}

/** The "Local" row: cover + title cards for the books found in the chosen folder. */
@Composable
fun LocalBooksSection(
    files: List<PdfFile>,
    onOpen: (PdfFile) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (files.isEmpty()) return
    Column(modifier) {
        Text(
            "LOCAL",
            color = HanderColors.Accent2,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(files, key = { it.uri.toString() }) { file ->
                LocalBookCard(file, onOpen)
            }
        }
    }
}

@Composable
private fun LocalBookCard(file: PdfFile, onOpen: (PdfFile) -> Unit) {
    val cover = rememberLocalCover(file)
    Column(
        Modifier
            .width(104.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable { onOpen(file) }
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
                .clip(RoundedCornerShape(8.dp))
                .background(HanderColors.Background2)
        ) {
            cover?.let {
                Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            file.title,
            color = HanderColors.Text,
            fontSize = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}