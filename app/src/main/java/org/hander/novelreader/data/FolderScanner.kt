package org.hander.novelreader.data

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PdfFile(
    val uri: Uri,
    val title: String,
    val sizeBytes: Long,
    val isEpub: Boolean = false,
)

/** Finds PDFs and EPUBs inside the folder the user picked (searches up to 3 levels deep). */
object FolderScanner {
    private const val MAX_DEPTH = 3

    suspend fun scan(context: Context, treeUri: Uri): List<PdfFile> = withContext(Dispatchers.IO) {
        val out = ArrayList<PdfFile>()
        try {
            val root = DocumentFile.fromTreeUri(context, treeUri)
            if (root != null && root.canRead()) collect(root, 0, out)
        } catch (e: Exception) {
            Log.w("FolderScanner", "Could not scan folder", e)
        }
        out.sortedBy { it.title.lowercase() }
    }

    private fun collect(dir: DocumentFile, depth: Int, out: MutableList<PdfFile>) {
        for (child in dir.listFiles()) {
            val name = child.name ?: continue
            if (child.isDirectory) {
                if (depth < MAX_DEPTH) collect(child, depth + 1, out)
            } else if (child.isFile) {
                when {
                    name.endsWith(".pdf", ignoreCase = true) ->
                        out.add(PdfFile(child.uri, name.dropLast(4), child.length()))

                    name.endsWith(".epub", ignoreCase = true) ->
                        out.add(PdfFile(child.uri, name.dropLast(5), child.length(), isEpub = true))
                }
            }
        }
    }
}