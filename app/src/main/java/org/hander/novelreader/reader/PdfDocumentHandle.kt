package org.hander.novelreader.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream

/**
 * An opened PDF. Text is extracted one page at a time so large books never sit in memory.
 */
class PdfDocumentHandle private constructor(
    private val file: File,
    private val document: PDDocument,
) : Closeable {

    val pageCount: Int = document.numberOfPages
    private val mutex = Mutex()
    private val cache = LruCache<Int, String>(12)

    /** Readable text of page [index] (0-based). Empty for image-only pages. */
    suspend fun pageText(index: Int): String {
        cache.get(index)?.let { return it }
        return mutex.withLock {
            cache.get(index) ?: withContext(Dispatchers.Default) {
                val stripper = PDFTextStripper()
                stripper.startPage = index + 1
                stripper.endPage = index + 1
                normalizePageText(stripper.getText(document))
            }.also { cache.put(index, it) }
        }
    }

    /** Renders page 1 as a small JPEG cover. Returns true on success. */
    fun renderCover(target: File): Boolean {
        return try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                val renderer = PdfRenderer(pfd)
                try {
                    if (renderer.pageCount == 0) return false
                    val page = renderer.openPage(0)
                    try {
                        val w = 400
                        val h = (w * page.height / page.width.toFloat()).toInt().coerceAtLeast(1)
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        target.parentFile?.mkdirs()
                        FileOutputStream(target).use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                        bmp.recycle()
                    } finally {
                        page.close()
                    }
                } finally {
                    renderer.close()
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun close() {
        try {
            document.close()
        } catch (_: Exception) {
        }
        file.delete()
    }

    companion object {
        /** Copies the PDF into app cache (so PDFBox can read it) and opens it. Call off the main thread. */
        fun open(context: Context, uri: Uri): PdfDocumentHandle {
            val cacheFile = File(context.cacheDir, "reader_${System.nanoTime()}.pdf")
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Cannot open $uri" }
                FileOutputStream(cacheFile).use { output -> input.copyTo(output) }
            }
            return try {
                PdfDocumentHandle(cacheFile, PDDocument.load(cacheFile))
            } catch (e: Exception) {
                cacheFile.delete()
                throw e
            }
        }
    }
}
