package org.hander.novelreader.source

object HanderVersion {
    const val CURRENT = "3.0"
}

/** Compares dotted version strings: "1.10" is newer than "1.9". */
fun compareVersions(a: String, b: String): Int {
    val pa = a.trim().split('.').map { part -> part.filter { it.isDigit() }.toIntOrNull() ?: 0 }
    val pb = b.trim().split('.').map { part -> part.filter { it.isDigit() }.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(pa.size, pb.size)) {
        val x = pa.getOrElse(i) { 0 }
        val y = pb.getOrElse(i) { 0 }
        if (x != y) return x.compareTo(y)
    }
    return 0
}
