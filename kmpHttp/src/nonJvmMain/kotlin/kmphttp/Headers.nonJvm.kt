package kmphttp

import kmphttp.HttpStatus.HTTP_CONTINUE
import kmphttp.HttpStatus.HTTP_NOT_MODIFIED
import kmphttp.HttpStatus.HTTP_NO_CONTENT

actual class Headers internal constructor(
    internal val namesAndValues: List<Pair<String, String>>
) : Iterable<Pair<String, String>> {

    actual val size: Int
        get() = namesAndValues.size

    actual fun name(index: Int): String = namesAndValues[index].first

    actual fun value(index: Int): String = namesAndValues[index].second

    actual operator fun get(name: String): String? {
        return namesAndValues.lastOrNull { it.first.equals(name, ignoreCase = true) }?.second
    }

    actual fun values(name: String): List<String> {
        return namesAndValues.filter { it.first.equals(name, ignoreCase = true) }.map { it.second }
    }

    actual fun newBuilder() = HeadersBuilder().apply {
        addAll(this@Headers)
    }

    actual override fun iterator(): Iterator<Pair<String, String>> = namesAndValues.iterator()

    actual override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Headers) return false
        return namesAndValues == other.namesAndValues
    }

    actual override fun hashCode(): Int = namesAndValues.hashCode()

    actual override fun toString(): String = buildString {
        for ((name, value) in namesAndValues) {
            append(name)
            append(": ")
            append(value)
            append('\n')
        }
    }


    actual companion object {
        actual val EMPTY = Headers(emptyList())
    }
}

actual class HeadersBuilder {
    private val namesAndValues = mutableListOf<Pair<String, String>>()

    actual fun add(line: String) = apply {
        val index = line.indexOf(':')
        require(index != -1) { "Unexpected header: $line" }
        add(line.substring(0, index).trim(), line.substring(index + 1).trim())
    }

    internal fun addLenient(line: String) =
        apply {
            val index = line.indexOf(':', 1)
            when {
                index != -1 -> {
                    addLenient(line.substring(0, index), line.substring(index + 1))
                }

                line[0] == ':' -> {
                    // Work around empty header names and header names that start with a colon (created by old
                    // broken SPDY versions of the response cache).
                    addLenient("", line.substring(1)) // Empty header name.
                }

                else -> {
                    // No header name.
                    addLenient("", line)
                }
            }
        }

    internal fun addLenient(
        name: String,
        value: String,
    ) = apply {
        namesAndValues.add(name to value.trim())
    }

    actual fun add(name: String, value: String) = apply {
        checkName(name)
        checkValue(value, name)
        namesAndValues.add(name to value)
    }

    actual fun addUnsafeNonAscii(name: String, value: String) = apply {
        checkName(name)
        namesAndValues.add(name to value)
    }

    actual fun addAll(headers: Headers) = apply {
        this.namesAndValues.addAll(headers.namesAndValues)
    }

    actual operator fun set(name: String, value: String) = apply {
        checkName(name)
        checkValue(value, name)
        removeAll(name)
        namesAndValues.add(name to value)
    }

    actual operator fun get(name: String): String? {
        return namesAndValues.find { it.first == name }?.second
    }

    actual fun removeAll(name: String) = apply {
        namesAndValues.removeAll { it.first.equals(name, ignoreCase = true) }
    }

    actual fun build(): Headers = Headers(namesAndValues.toList())

    private fun checkName(name: String) {
        require(name.isNotEmpty()) { "name is empty" }
        for (char in name) {
            require(char in '\u0021'..'\u007e') {
                "Unexpected char 0x${char.code.toString(16)} at 0 in header name: $name"
            }
        }
    }

    private fun checkValue(value: String, name: String) {
        for (char in value) {
            require(char == '\t' || char in '\u0020'..'\u007e') {
                "Unexpected char 0x${char.code.toString(16)} in value for header $name"
            }
        }
    }
}

/**
 * Returns true if the response headers and status indicate that this response has a (possibly
 * 0-length) body. See RFC 7231.
 */
fun Response.promisesBody(): Boolean {
    // HEAD requests never yield a body regardless of the response headers.
    if (request.method == "HEAD") {
        return false
    }

    val responseCode = code
    if ((responseCode < HTTP_CONTINUE || responseCode >= 200) &&
        responseCode != HTTP_NO_CONTENT &&
        responseCode != HTTP_NOT_MODIFIED
    ) {
        return true
    }

    // If the Content-Length or Transfer-Encoding headers disagree with the response code, the
    // response is malformed. For best compatibility, we honor the headers.
    if (headersContentLength() != -1L ||
        "chunked".equals(header("Transfer-Encoding"), ignoreCase = true)
    ) {
        return true
    }

    return false
}

/** Returns the Content-Length as reported by the response headers. */
internal fun Response.headersContentLength(): Long =
    headers["Content-Length"]?.toLongOrNull() ?: -1L