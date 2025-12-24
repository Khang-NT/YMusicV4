package kmphttp.internal

import okio.ArrayIndexOutOfBoundsException
import okio.IOException
import kotlin.experimental.ExperimentalNativeApi

/**
 * Returns the index of the first character in this string that contains a character in
 * [delimiters]. Returns endIndex if there is no such character.
 */
internal fun String.delimiterOffset(
    delimiters: String,
    startIndex: Int = 0,
    endIndex: Int = length,
): Int {
    for (i in startIndex until endIndex) {
        if (this[i] in delimiters) return i
    }
    return endIndex
}

/**
 * Returns the index of the first character in this string that contains a character in
 * [delimiter]. Returns endIndex if there is no such character.
 */
internal fun String.delimiterOffset(
    delimiter: Char,
    startIndex: Int = 0,
    endIndex: Int = length,
): Int {
    for (i in startIndex until endIndex) {
        if (this[i] == delimiter) return i
    }
    return endIndex
}

/** Increments [startIndex] until this string is not ASCII whitespace. Stops at [endIndex]. */
internal fun String.indexOfFirstNonAsciiWhitespace(
    startIndex: Int = 0,
    endIndex: Int = length,
): Int {
    for (i in startIndex until endIndex) {
        when (this[i]) {
            '\t', '\n', '\u000C', '\r', ' ' -> Unit
            else -> return i
        }
    }
    return endIndex
}

/**
 * Decrements [endIndex] until `input[endIndex - 1]` is not ASCII whitespace. Stops at [startIndex].
 */
internal fun String.indexOfLastNonAsciiWhitespace(
    startIndex: Int = 0,
    endIndex: Int = length,
): Int {
    for (i in endIndex - 1 downTo startIndex) {
        when (this[i]) {
            '\t', '\n', '\u000C', '\r', ' ' -> Unit
            else -> return i + 1
        }
    }
    return startIndex
}

/** Equivalent to `string.substring(startIndex, endIndex).trim()`. */
internal fun String.trimSubstring(
    startIndex: Int = 0,
    endIndex: Int = length,
): String {
    val start = indexOfFirstNonAsciiWhitespace(startIndex, endIndex)
    val end = indexOfLastNonAsciiWhitespace(start, endIndex)
    return substring(start, end)
}

/**
 * Returns the index of the first character in this string that is either a control character (like
 * `\u0000` or `\n`) or a non-ASCII character. Returns -1 if this string has no such characters.
 */
internal fun String.indexOfControlOrNonAscii(): Int {
    for (i in 0 until length) {
        val c = this[i]
        if (c <= '\u001f' || c >= '\u007f') {
            return i
        }
    }
    return -1
}

internal fun Char.parseHexDigit(): Int =
    when (this) {
        in '0'..'9' -> this - '0'
        in 'a'..'f' -> this - 'a' + 10
        in 'A'..'F' -> this - 'A' + 10
        else -> -1
    }

@OptIn(ExperimentalNativeApi::class)
internal fun String.codePointAt(index: Int): Int {
    val high = this[index]
    if (high.isHighSurrogate() && index + 1 < this.length) {
        val low = this[index + 1]
        if (low.isLowSurrogate()) {
            return Char.toCodePoint(high, low)
        }
    }
    return high.code
}

internal fun Int.charCount(): Int = if (this > Char.MAX_VALUE.code) 2 else 1

internal fun StringBuilder.appendCodePoint(codePoint: Int) {
    if (codePoint <= Char.MAX_VALUE.code) {
        append(codePoint.toChar())
    } else {
        append(Char.MIN_HIGH_SURROGATE + ((codePoint - 0x10000) shr 10))
        append(Char.MIN_LOW_SURROGATE + (codePoint and 0x3ff))
    }
}

/** Returns true if we should void putting this this header in an exception or toString(). */
internal fun isSensitiveHeader(name: String): Boolean =
    name.equals("Authorization", ignoreCase = true) ||
            name.equals("Cookie", ignoreCase = true) ||
            name.equals("Proxy-Authorization", ignoreCase = true) ||
            name.equals("Set-Cookie", ignoreCase = true)


internal fun checkOffsetAndCount(
    arrayLength: Long,
    offset: Long,
    count: Long,
) {
    if (offset or count < 0L || offset > arrayLength || arrayLength - offset < count) {
        throw ArrayIndexOutOfBoundsException("length=$arrayLength, offset=$offset, count=$offset")
    }
}

internal fun okio.Closeable.closeQuietly() {
    try {
        close()
    } catch (_: IOException) {
    }
}

/**
 * Returns the Host header value for this URL.
 * Includes port only if it differs from the default port for the scheme.
 */
internal fun kmphttp.HttpUrl.toHostHeader(includeDefaultPort: Boolean = false): String {
    val host = if (':' in host) "[$host]" else host
    return if (includeDefaultPort || port != kmphttp.HttpUrl.defaultPort(scheme)) {
        "$host:$port"
    } else {
        host
    }
}