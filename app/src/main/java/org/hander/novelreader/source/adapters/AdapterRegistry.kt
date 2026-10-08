package org.hander.novelreader.source.adapters

import org.hander.novelreader.network.SafeHttp
import org.hander.novelreader.source.NovelSource
import org.hander.novelreader.source.NovelSourceInfo

/**
 * The list of source types built into Hander. A repository can only choose from this list;
 * it can never bring its own code. That is what keeps third-party sources safe.
 */
object AdapterRegistry {
    private val supported = setOf("gutendex")

    fun isSupported(adapter: String): Boolean = adapter in supported

    fun create(info: NovelSourceInfo, http: SafeHttp): NovelSource? = when (info.adapter) {
        "gutendex" -> GutendexSource(info, http)
        else -> null
    }
}
