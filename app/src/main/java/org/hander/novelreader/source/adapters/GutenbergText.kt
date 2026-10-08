package org.hander.novelreader.source.adapters

/** Cleans a raw Project Gutenberg text file into plain paragraphs for the reader. */
object GutenbergText {
    private val startMarker = Regex("(?m)^\\*\\*\\* ?START OF[^\\n]*\\n")
    private val endMarker = Regex("(?m)^\\*\\*\\* ?END OF")
    private val paragraphSplit = Regex("\\n\\s*\\n")
    private val lineBreaks = Regex("\\s*\\n\\s*")
    private val spaces = Regex("[ \\t]{2,}")

    fun clean(raw: String): String {
        var text = raw.replace("\uFEFF", "").replace("\r\n", "\n").replace('\r', '\n')
        val start = startMarker.find(text)
        if (start != null) text = text.substring(start.range.last + 1)
        val end = endMarker.find(text)
        if (end != null) text = text.substring(0, end.range.first)
        return text.split(paragraphSplit)
            .map { it.replace(lineBreaks, " ").replace(spaces, " ").trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n\n")
    }
}
