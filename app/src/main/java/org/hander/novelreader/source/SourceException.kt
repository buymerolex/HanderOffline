package org.hander.novelreader.source

/** An error whose message is safe and friendly to show to a normal user. */
class SourceException(val userMessage: String, cause: Throwable? = null) : Exception(userMessage, cause)
