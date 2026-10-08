package org.hander.novelreader.reader

private const val TERMINATORS = ".!?:\u201D\"\u2019\u2026)"

/**
 * PDF text comes out one visual line at a time. This joins wrapped lines back into
 * paragraphs and repairs words hyphenated across lines, so reading and TTS sound natural.
 */
fun normalizePageText(raw: String): String {
    val lines = raw.replace("\r", "").split("\n").map { it.trim() }
    val maxLen = lines.maxOfOrNull { it.length } ?: 0
    val sb = StringBuilder()
    var prevLen = 0
    for (line in lines) {
        if (line.isEmpty()) {
            if (sb.isNotEmpty() && !sb.endsWith("\n\n")) sb.append("\n\n")
            prevLen = 0
            continue
        }
        if (sb.isEmpty() || sb.endsWith("\n\n")) {
            sb.append(line)
            prevLen = line.length
            continue
        }
        val prev = sb[sb.length - 1]
        if (prev == '-' && line[0].isLowerCase()) {
            sb.setLength(sb.length - 1)
            sb.append(line)
        } else if (prev in TERMINATORS && prevLen < maxLen * 0.8) {
            sb.append("\n\n").append(line)
        } else {
            sb.append(' ').append(line)
        }
        prevLen = line.length
    }
    return sb.toString().trim()
}

/**
 * Splits text into speakable chunks (roughly one sentence each).
 * Returns (start, end) character offsets into [text]; end is exclusive.
 */
fun splitIntoChunks(text: String, maxLen: Int = 380): List<Pair<Int, Int>> {
    val out = ArrayList<Pair<Int, Int>>()
    val n = text.length
    var start = 0
    var i = 0

    fun emit(end: Int) {
        var s = start
        var e = end
        while (s < e && text[s].isWhitespace()) s++
        while (e > s && text[e - 1].isWhitespace()) e--
        if (e > s) out.add(s to e)
        start = end
    }

    while (i < n) {
        val c = text[i]
        if (c == '\n') {
            emit(i)
            start = i + 1
            i++
            continue
        }
        if (c == '.' || c == '!' || c == '?' || c == '\u2026') {
            var j = i + 1
            while (j < n && text[j] in "\"'\u201D\u2019)]") j++
            if (j >= n || text[j].isWhitespace()) emit(j)
            i = j
            continue
        }
        if (i - start >= maxLen) {
            val sp = text.lastIndexOf(' ', i)
            val cut = if (sp > start) sp else i + 1
            emit(cut)
            i = cut
            continue
        }
        i++
    }
    emit(n)
    return out
}
