package org.hander.novelreader.reader

import android.content.Context
import android.net.Uri
import android.text.Html
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.StringReader
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class EpubDocumentHandle private constructor(
    private val file: File,
    private val zip: ZipFile,
    private val paths: List<String>,
    override val chapterTitles: List<String>,
    private val coverPath: String?,
) : BookHandle {

    override val pageCount: Int get() = paths.size

    override suspend fun pageText(index: Int): String = withContext(Dispatchers.IO) {
        val path = paths.getOrNull(index) ?: return@withContext ""
        htmlToText(readEntry(zip, path) ?: "")
    }

    override suspend fun renderCover(target: File) {
        withContext(Dispatchers.IO) {
            val path = coverPath
            val entry = if (path != null) findEntry(zip, path) else null
            if (entry != null) {
                target.parentFile?.mkdirs()
                zip.getInputStream(entry).use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            }
            Unit
        }
    }

    override fun close() {
        try { zip.close() } catch (_: Exception) {}
        file.delete()
    }

    companion object {

        suspend fun open(context: Context, uri: Uri): EpubDocumentHandle =
            withContext(Dispatchers.IO) {
                val tmp = File(
                    context.cacheDir,
                    "epub_${Integer.toHexString(uri.toString().hashCode())}.epub"
                )
                val input = context.contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("Cannot read this file")
                input.use { src -> tmp.outputStream().use { dst -> src.copyTo(dst) } }

                val zip = ZipFile(tmp)
                try {
                    build(tmp, zip)
                } catch (e: Throwable) {
                    try { zip.close() } catch (_: Exception) {}
                    tmp.delete()
                    throw e
                }
            }

        /** Lightweight: extracts just the cover image of an EPUB. Call off the main thread. */
        fun extractCover(context: Context, uri: Uri, target: File): Boolean {
            val tmp = File(context.cacheDir, "epubcover_${System.nanoTime()}.epub")
            return try {
                val input = context.contentResolver.openInputStream(uri) ?: return false
                input.use { src -> tmp.outputStream().use { dst -> src.copyTo(dst) } }

                ZipFile(tmp).use { zip ->
                    val container = readEntry(zip, "META-INF/container.xml") ?: return false
                    val opfPath = Regex("full-path=\"([^\"]+)\"").find(container)
                        ?.groupValues?.get(1) ?: return false
                    val opfDir = opfPath.substringBeforeLast('/', "")
                    val opf = readEntry(zip, opfPath) ?: return false

                    val attrRe = Regex("([\\w:.-]+)\\s*=\\s*\"([^\"]*)\"")
                    fun attrs(tag: String): Map<String, String> =
                        attrRe.findAll(tag).associate { it.groupValues[1] to it.groupValues[2] }

                    val items = Regex("(?is)<(?:\\w+:)?item\\s[^>]*>")
                        .findAll(opf).map { attrs(it.value) }.toList()
                    val coverMeta = Regex("(?is)<(?:\\w+:)?meta\\s[^>]*>")
                        .findAll(opf).map { attrs(it.value) }
                        .firstOrNull { it["name"] == "cover" }?.get("content")

                    val cover = items.firstOrNull { " cover-image " in " ${it["properties"] ?: ""} " }
                        ?: items.firstOrNull {
                            coverMeta != null && it["id"] == coverMeta &&
                                    (it["media-type"] ?: "").startsWith("image/")
                        }
                        ?: items.firstOrNull {
                            (it["media-type"] ?: "").startsWith("image/") &&
                                    (it["id"] ?: "").contains("cover", ignoreCase = true)
                        }
                        ?: return false

                    val href = cover["href"] ?: return false
                    val entry = findEntry(zip, resolvePath(opfDir, href)) ?: return false
                    target.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { i ->
                        target.outputStream().use { o -> i.copyTo(o) }
                    }
                    true
                }
            } catch (e: Exception) {
                target.delete()
                false
            } finally {
                tmp.delete()
            }
        }

        private class Item(val id: String, val href: String, val type: String, val props: String)

        private fun build(file: File, zip: ZipFile): EpubDocumentHandle {
            val container = readEntry(zip, "META-INF/container.xml")
                ?: error("Not a valid EPUB")
            val opfPath = Regex("full-path=\"([^\"]+)\"").find(container)?.groupValues?.get(1)
                ?: error("Not a valid EPUB")
            val opfDir = opfPath.substringBeforeLast('/', "")
            val opf = readEntry(zip, opfPath) ?: error("Not a valid EPUB")

            // ---- OPF: manifest, spine, cover ----
            val manifest = LinkedHashMap<String, Item>()
            val spine = ArrayList<Pair<String, Boolean>>()
            var coverId: String? = null
            var ncxId: String? = null

            val p = Xml.newPullParser()
            p.setInput(StringReader(opf))
            var ev = p.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG) {
                    when (p.name.substringAfter(':')) {
                        "item" -> {
                            val id = p.getAttributeValue(null, "id") ?: ""
                            manifest[id] = Item(
                                id,
                                p.getAttributeValue(null, "href") ?: "",
                                p.getAttributeValue(null, "media-type") ?: "",
                                p.getAttributeValue(null, "properties") ?: "",
                            )
                        }
                        "itemref" -> {
                            val idref = p.getAttributeValue(null, "idref") ?: ""
                            val linear = p.getAttributeValue(null, "linear") != "no"
                            spine.add(idref to linear)
                        }
                        "spine" -> ncxId = p.getAttributeValue(null, "toc")
                        "meta" -> if (p.getAttributeValue(null, "name") == "cover") {
                            coverId = p.getAttributeValue(null, "content")
                        }
                    }
                }
                ev = p.next()
            }

            // ---- Table of contents -> titles by file path ----
            val titles = HashMap<String, String>()

            // EPUB 3: nav document
            manifest.values.firstOrNull { " nav " in " ${it.props} " }?.let { navItem ->
                val navPath = resolvePath(opfDir, navItem.href)
                val navDir = navPath.substringBeforeLast('/', "")
                val html = readEntry(zip, navPath) ?: ""
                val section = Regex("(?is)<nav[^>]*epub:type=\"[^\"]*toc[^\"]*\"[^>]*>(.*?)</nav>")
                    .find(html)?.groupValues?.get(1) ?: html
                Regex("(?is)<a\\s[^>]*?href=\"([^\"]*)\"[^>]*>(.*?)</a>")
                    .findAll(section)
                    .forEach { m ->
                        val t = cleanTitle(m.groupValues[2])
                        if (t.isNotBlank()) {
                            titles.putIfAbsent(resolvePath(navDir, m.groupValues[1]), t)
                        }
                    }
            }

            // EPUB 2: NCX
            val ncxItem = ncxId?.let { manifest[it] }
                ?: manifest.values.firstOrNull { it.type == "application/x-dtbncx+xml" }
            if (ncxItem != null) {
                try {
                    val ncxPath = resolvePath(opfDir, ncxItem.href)
                    val ncxDir = ncxPath.substringBeforeLast('/', "")
                    val ncx = readEntry(zip, ncxPath) ?: ""
                    val np = Xml.newPullParser()
                    np.setInput(StringReader(ncx))
                    var label: String? = null
                    var inLabel = false
                    var e2 = np.eventType
                    while (e2 != XmlPullParser.END_DOCUMENT) {
                        if (e2 == XmlPullParser.START_TAG) {
                            when (np.name.substringAfter(':')) {
                                "navLabel" -> inLabel = true
                                "text" -> if (inLabel) label = cleanTitle(np.nextText())
                                "content" -> {
                                    val src = np.getAttributeValue(null, "src")
                                    val l = label
                                    if (src != null && !l.isNullOrBlank()) {
                                        titles.putIfAbsent(resolvePath(ncxDir, src), l)
                                    }
                                    label = null
                                }
                            }
                        } else if (e2 == XmlPullParser.END_TAG &&
                            np.name.substringAfter(':') == "navLabel"
                        ) {
                            inLabel = false
                        }
                        e2 = np.next()
                    }
                } catch (_: Exception) {
                    // A broken NCX must not stop the book from opening.
                }
            }

            // ---- Chapters in reading order ----
            val paths = ArrayList<String>()
            val names = ArrayList<String>()
            for ((idref, linear) in spine) {
                if (!linear) continue
                val item = manifest[idref] ?: continue
                if (!item.type.contains("html")) continue
                val path = resolvePath(opfDir, item.href)
                val html = readEntry(zip, path) ?: continue

                val body = Regex("(?is)<body[^>]*>(.*)</body>").find(html)?.groupValues?.get(1) ?: html
                val plain = body
                    .replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), " ")
                    .replace(Regex("<[^>]+>"), " ")
                    .replace("&nbsp;", " ")
                if (plain.isBlank()) continue   // cover pages, image-only pages

                val heading = Regex("(?is)<h[1-3][^>]*>(.*?)</h[1-3]>")
                    .find(html)?.groupValues?.get(1)?.let { cleanTitle(it) }
                val title = titles[path]
                    ?: heading?.takeIf { it.isNotBlank() }
                    ?: "Section ${paths.size + 1}"

                paths.add(path)
                names.add(title)
            }
            if (paths.isEmpty()) error("No readable text in this EPUB")

            // ---- Cover ----
            val coverItem = manifest.values.firstOrNull { " cover-image " in " ${it.props} " }
                ?: coverId?.let { manifest[it] }?.takeIf { it.type.startsWith("image/") }
                ?: manifest.values.firstOrNull {
                    it.type.startsWith("image/") && it.id.contains("cover", ignoreCase = true)
                }
            val coverPath = coverItem?.let { resolvePath(opfDir, it.href) }

            return EpubDocumentHandle(file, zip, paths, names, coverPath)
        }

        // ---------------------------------------------------------------- helpers

        private fun resolvePath(baseDir: String, href: String): String {
            val clean = Uri.decode(href.substringBefore('#').substringBefore('?'))
            val full = if (baseDir.isEmpty()) clean else "$baseDir/$clean"
            val parts = ArrayList<String>()
            for (s in full.split('/')) {
                when (s) {
                    "", "." -> {}
                    ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
                    else -> parts.add(s)
                }
            }
            return parts.joinToString("/")
        }

        private fun findEntry(zip: ZipFile, path: String): ZipEntry? {
            zip.getEntry(path)?.let { return it }
            val e = zip.entries()
            while (e.hasMoreElements()) {
                val entry = e.nextElement()
                if (entry.name.equals(path, ignoreCase = true)) return entry
            }
            return null
        }

        private fun readEntry(zip: ZipFile, path: String): String? {
            val entry = findEntry(zip, path) ?: return null
            return zip.getInputStream(entry).use { it.readBytes().toString(Charsets.UTF_8) }
                .removePrefix("\uFEFF")
        }

        private fun cleanTitle(raw: String): String =
            Html.fromHtml(raw.replace(Regex("<[^>]+>"), " "), Html.FROM_HTML_MODE_LEGACY)
                .toString()
                .replace('\u00A0', ' ')
                .replace(Regex("\\s+"), " ")
                .trim()

        private fun htmlToText(html: String): String {
            val prepared = html
                .replace(Regex("(?is)<\\?xml.*?\\?>"), "")
                .replace(Regex("(?is)<!DOCTYPE.*?>"), "")
                .replace(Regex("(?is)<(script|style|head)[^>]*>.*?</\\1>"), " ")
                .replace(Regex("(?i)<br\\s*/?>"), "\n")
                .replace(Regex("(?i)</(p|div|h[1-6]|li|tr|blockquote|section)>"), "\n\n")
            return Html.fromHtml(prepared, Html.FROM_HTML_MODE_LEGACY)
                .toString()
                .replace('\u00A0', ' ')
                .replace("\uFFFC", "")
                .replace(Regex("[ \\t]+"), " ")
                .replace(Regex(" ?\\n ?"), "\n")
                .replace(Regex("\\n{3,}"), "\n\n")
                .trim()
        }
    }
}