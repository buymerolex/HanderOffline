package org.hander.novelreader.source

import org.json.JSONArray
import org.json.JSONObject

/** Reads a string safely: missing, null or blank values become null. */
internal fun JSONObject.str(key: String): String? {
    if (!has(key) || isNull(key)) return null
    val v = optString(key, "").trim()
    return if (v.isEmpty()) null else v
}

internal fun JSONArray.strings(): List<String> {
    val out = ArrayList<String>()
    for (i in 0 until length()) {
        if (isNull(i)) continue
        val v = optString(i, "").trim()
        if (v.isNotEmpty()) out.add(v)
    }
    return out
}
