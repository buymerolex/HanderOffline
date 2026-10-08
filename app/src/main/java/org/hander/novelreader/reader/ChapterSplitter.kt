package org.hander.novelreader.reader

object ChapterSplitter {
    private val headingRegex =
        Regex("(?m)^[ \\t]*(chapter|book|part)\\s+([0-9]+|[ivxlcdm]+)\\b.{0,60}$", RegexOption.IGNORE_CASE)

    /**
     * Splits raw novel text into (label, text) chapters by looking for
     * heading lines like "Chapter 12" or "CHAPTER XII - The Return".
     * Falls back to one single chapter if fewer than two headings are found
     * (common for short stories, or texts that don't use "Chapter").
     */
    fun split(text: String): List<Pair<String, String>> {
        val cleaned = stripGutenbergBoilerplate(text)
        val matches = headingRegex.findAll(cleaned).toList()
        if (matches.size < 2) {
            return listOf("Full text" to cleaned.trim())
        }
        val chapters = mutableListOf<Pair<String, String>>()
        for (i in matches.indices) {
            val start = matches[i].range.first
            val end = if (i + 1 < matches.size) matches[i + 1].range.first else cleaned.length
            val label = matches[i].value.trim().take(60)
            val body = cleaned.substring(start, end).trim()
            if (body.isNotBlank()) chapters.add(label to body)
        }
        return if (chapters.isEmpty()) listOf("Full text" to cleaned.trim()) else chapters
    }

    /** Project Gutenberg texts wrap the real book in a license header/footer. */
    private fun stripGutenbergBoilerplate(text: String): String {
        val startMarker = Regex("\\*\\*\\*\\s*START OF (THE|THIS) PROJECT GUTENBERG EBOOK.*?\\*\\*\\*", RegexOption.IGNORE_CASE)
        val endMarker = Regex("\\*\\*\\*\\s*END OF (THE|THIS) PROJECT GUTENBERG EBOOK.*?\\*\\*\\*", RegexOption.IGNORE_CASE)
        var result = text
        startMarker.find(result)?.let { result = result.substring(it.range.last + 1) }
        endMarker.find(result)?.let { result = result.substring(0, it.range.first) }
        return result
    }
}
