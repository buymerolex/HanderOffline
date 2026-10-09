package org.hander.novelreader.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.hander.novelreader.source.SourceException
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.util.Locale
import javax.net.ssl.SSLException

class SafeHttp(allowedHosts: Set<String>? = null) {

    private val allowed: Set<String>? = allowedHosts?.map { it.lowercase(Locale.ROOT) }?.toSet()

    suspend fun getString(url: String, maxBytes: Int = MAX_BODY_BYTES): String =
        withContext(Dispatchers.IO) {
            guarded {
                val conn = open(url, "GET")
                try {
                    val code = conn.responseCode
                    if (code !in 200..299) throw httpError(code)
                    conn.inputStream.use { readCapped(it, maxBytes) }
                } finally {
                    conn.disconnect()
                }
            }
        }

    /** True if the address answers successfully. Never throws. */
    suspend fun exists(url: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val conn = open(url, "HEAD")
            try {
                conn.responseCode in 200..299
            } finally {
                conn.disconnect()
            }
        } catch (e: SourceException) {
            false
        } catch (e: IOException) {
            false
        }
    }

    private fun open(startUrl: String, method: String): HttpURLConnection {
        var current = startUrl
        for (hop in 0..MAX_REDIRECTS) {
            val url = checkUrl(current)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = method
            conn.connectTimeout = 10_000
            conn.readTimeout = 20_000
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("User-Agent", USER_AGENT)
            val code = conn.responseCode
            if (code in 300..399) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location.isNullOrBlank()) throw SourceException("The website sent an invalid redirect.")
                current = try {
                    URL(url, location).toString()
                } catch (e: MalformedURLException) {
                    throw SourceException("The website sent an invalid redirect.")
                }
                continue
            }
            return conn
        }
        throw SourceException("The website redirected too many times.")
    }

    private fun checkUrl(raw: String): URL {
        val url = try {
            URL(raw)
        } catch (e: MalformedURLException) {
            throw SourceException("The address is not valid.")
        }
        if (!url.protocol.equals("https", ignoreCase = true)) {
            throw SourceException("Only secure (https) connections are allowed.")
        }
        val host = url.host.lowercase(Locale.ROOT)
        if (host.isEmpty()) throw SourceException("The address is not valid.")
        val list = allowed
        if (list != null && host !in list) {
            throw SourceException("This source tried to contact a website it is not allowed to use.")
        }
        return url
    }

    private fun httpError(code: Int): SourceException = when {
        code == 429 -> SourceException("The website is limiting requests. Please try again later.")
        code == 404 -> SourceException("The page was not found on the website.")
        code in 500..599 -> SourceException("The website is unavailable right now.")
        else -> SourceException("The website returned an error ($code).")
    }

    private fun readCapped(input: InputStream, maxBytes: Int): String {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            total += n
            if (total > maxBytes) throw SourceException("The page is too large to load.")
            out.write(buffer, 0, n)
        }
        return out.toString("UTF-8")
    }

    private inline fun <T> guarded(block: () -> T): T = try {
        block()
    } catch (e: SourceException) {
        throw e
    } catch (e: SecurityException) {
        throw SourceException(
            "Hander is not allowed to use the internet. The INTERNET permission is missing from AndroidManifest.xml.",
            e
        )
    } catch (e: UnknownHostException) {
        throw SourceException("No internet connection, or the website could not be found.", e)
    } catch (e: SocketTimeoutException) {
        throw SourceException("The website took too long to respond.", e)
    } catch (e: ConnectException) {
        throw SourceException("Could not connect to the website.", e)
    } catch (e: SSLException) {
        throw SourceException("A secure connection to the website could not be made.", e)
    } catch (e: IOException) {
        throw SourceException("The connection to the website failed.", e)
    }

    companion object {
        private const val MAX_REDIRECTS = 5
        private const val MAX_BODY_BYTES = 12_000_000
        private const val USER_AGENT = "Hander/3.0 (Android novel reader)"
    }
}
