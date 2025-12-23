/**
 * Percent-Encoding as defined in RFC 3986 Section 2.1
 *
 * A percent-encoded octet is encoded as a character triplet, consisting of
 * the percent character "%" followed by the two hexadecimal digits representing
 * that octet's numeric value.
 */
package kmpcommon.uri

import kmpcommon.appendCodePoint
import kmpcommon.getCodePointAt
import kmpcommon.parseHexDigit
import kmpcommon.utfCodePointSize

/**
 * Utility object for percent-encoding and decoding URI components.
 *
 * Section 2.1: pct-encoded = "%" HEXDIG HEXDIG
 *
 * Characters are categorized as:
 * - Reserved (Section 2.2): gen-delims / sub-delims
 * - Unreserved (Section 2.3): ALPHA / DIGIT / "-" / "." / "_" / "~"
 */
object PercentEncoder {

    /**
     * Unreserved characters as defined in Section 2.3
     * unreserved = ALPHA / DIGIT / "-" / "." / "_" / "~"
     */
    fun isUnreserved(codePoint: Int): Boolean {
        return codePoint in '0'.code..'9'.code
                || codePoint in 'A'.code..'Z'.code
                || codePoint in 'a'.code..'z'.code
                || codePoint == '-'.code
                || codePoint == '.'.code
                || codePoint == '_'.code
                || codePoint == '~'.code
    }

    /**
     * General delimiters as defined in Section 2.2
     * gen-delims = ":" / "/" / "?" / "#" / "[" / "]" / "@"
     */
    private val GEN_DELIMS = setOf(':', '/', '?', '#', '[', ']', '@')

    /**
     * Sub-delimiters as defined in Section 2.2
     * sub-delims = "!" / "$" / "&" / "'" / "(" / ")" / "*" / "+" / "," / ";" / "="
     */
    private val SUB_DELIMS = setOf('!', '$', '&', '\'', '(', ')', '*', '+', ',', ';', '=')

    /**
     * Characters allowed in path component (beside unreserved chars). (Section 3.3)
     * pchar = unreserved / pct-encoded / sub-delims / ":" / "@"
     */
    private val PATH_ALLOWED = SUB_DELIMS + setOf(':', '@')

    /**
     * Characters allowed in path segment (without delimiter "/")
     */
    private val PATH_SEGMENT_ALLOWED = PATH_ALLOWED

    /**
     * Characters allowed in query component (beside unreserved chars) (Section 3.4)
     * query = *( pchar / "/" / "?" )
     */
    private val QUERY_ALLOWED = PATH_ALLOWED + setOf('/', '?')
    private val QUERY_NAME_ALLOWED = QUERY_ALLOWED - setOf('&', '=')
    private val QUERY_VALUE_ALLOWED = QUERY_ALLOWED - '&'

    /**
     * Characters allowed in fragment component (beside unreserved chars) (Section 3.5)
     * fragment = *( pchar / "/" / "?" )
     */
    private val FRAGMENT_ALLOWED = PATH_ALLOWED + setOf('/', '?')

    /**
     * Characters allowed in userinfo (name:password) component (beside unreserved chars) (Section 3.2.1)
     * userinfo = *( unreserved / pct-encoded / sub-delims / ":" )
     */
    private val USERINFO_ALLOWED = SUB_DELIMS + setOf(':')
    private val USERINFO_NAME_ALLOWED = SUB_DELIMS
    private val USERINFO_PASSWORD_ALLOWED = USERINFO_ALLOWED

    /**
     * Characters allowed in reg-name (beside unreserved chars) (Section 3.2.2)
     * reg-name = *( unreserved / pct-encoded / sub-delims )
     */
    private val REG_NAME_ALLOWED = SUB_DELIMS

    /**
     * IP-literal = "[" ( IPv6address / IPvFuture  ) "]"
     */
    private val IPV6_IP_FUTURE_ALLOWED = SUB_DELIMS + setOf('[', ']', ':')

    /**
     * Encodes a string for use in the path component.
     * Path segments are separated by "/", so "/" is preserved.
     */
    fun canonicalizePath(path: CharSequence, alreadyEncoded: Boolean = false): CharSequence {
        return canonicalize(path, PATH_ALLOWED + '/', alreadyEncoded)
    }

    /**
     * Encodes a single path segment.
     */
    fun canonicalizePathSegment(
        segment: CharSequence,
        alreadyEncoded: Boolean = false
    ): CharSequence {
        return canonicalize(segment, PATH_SEGMENT_ALLOWED, alreadyEncoded)
    }

    /**
     * Encodes a query component.
     */
    fun canonicalizeQuery(
        query: CharSequence,
        alreadyEncoded: Boolean = false
    ): CharSequence {
        return canonicalize(query, QUERY_ALLOWED, alreadyEncoded)
    }

    /**
     * Encodes a query parameter name.
     * This is more restrictive than encodeQuery as it excludes "&" and "=".
     */
    fun canonicalizeQueryParamName(
        param: CharSequence,
        alreadyEncoded: Boolean = false
    ): CharSequence {
        return canonicalize(param, QUERY_NAME_ALLOWED, alreadyEncoded)
    }

    /**
     * Encodes a query parameter value.
     * This is more restrictive than encodeQuery as it excludes "&".
     */
    fun canonicalizeQueryParamValue(
        value: CharSequence,
        alreadyEncoded: Boolean = false
    ): CharSequence {
        return canonicalize(value, QUERY_VALUE_ALLOWED, alreadyEncoded)
    }

    /**
     * Encodes a string for use in the fragment component.
     */
    fun canonicalizeFragment(
        fragment: CharSequence,
        alreadyEncoded: Boolean = false
    ): CharSequence {
        return canonicalize(fragment, FRAGMENT_ALLOWED, alreadyEncoded)
    }

    /**
     * Encodes a string for use in the userinfo name component.
     */
    fun canonicalizeUserInfoName(
        userInfo: CharSequence,
        alreadyEncoded: Boolean = false
    ): CharSequence {
        return canonicalize(userInfo, USERINFO_NAME_ALLOWED, alreadyEncoded)
    }

    /**
     * Encodes a string for use in the userinfo password component.
     */
    fun canonicalizeUserInfoPassword(
        userInfo: CharSequence,
        alreadyEncoded: Boolean = false
    ): CharSequence {
        return canonicalize(userInfo, USERINFO_PASSWORD_ALLOWED, alreadyEncoded)
    }

    fun canonicalizeHost(host: CharSequence, alreadyEncoded: Boolean = false): CharSequence {
        val allowed = if (host.startsWith('[') && host.endsWith(']')) {
            IPV6_IP_FUTURE_ALLOWED
        } else {
            REG_NAME_ALLOWED
        }
        return canonicalize(host, allowed, alreadyEncoded)
    }

    fun canonicalize(
        input: CharSequence,
        allowed: Set<Char>,
        alreadyEncoded: Boolean = false
    ): CharSequence {
        var i = 0
        var codePoint: Int
        while (i < input.length) {
            codePoint = input.getCodePointAt(i)
            if (isCodePointAllowed(input, i, codePoint, allowed, alreadyEncoded)) {
                i += codePoint.utfCodePointSize()
            } else {
                break
            }
        }
        if (i >= input.length) {
            return input
        }
        return StringBuilder().apply {
            if (i > 0) {
                append(input.subSequence(0, i))
            }
            while (i < input.length) {
                codePoint = input.getCodePointAt(i)
                if (alreadyEncoded &&
                    (
                            codePoint == '\t'.code ||
                                    codePoint == '\n'.code ||
                                    codePoint == '\u000c'.code ||
                                    codePoint == '\r'.code
                            )
                ) {
                    // Skip this character.
                    i++
                    continue
                }

                var j = i
                while (!isCodePointAllowed(input, i, codePoint, allowed, alreadyEncoded)) {
                    j += codePoint.utfCodePointSize()
                    if (j >= input.length) break
                    else codePoint = input.getCodePointAt(j)
                }
                if (j == i) {
                    // code point is allowed
                    appendCodePoint(codePoint)
                    i += codePoint.utfCodePointSize()
                } else {
                    val bytes = input.substring(i, j.coerceAtMost(input.length)).encodeToByteArray()
                    for (byte in bytes) {
                        append('%')
                        appendByteAsHex(byte)
                    }
                    i = j
                }

            }
        }
    }

    private fun isCodePointAllowed(
        input: CharSequence,
        index: Int,
        codePoint: Int,
        allowed: Set<Char>,
        alreadyEncoded: Boolean
    ): Boolean {
        if (isUnreserved(codePoint) || allowed.contains(codePoint.toChar())) {
            return true
        }
        if (alreadyEncoded && codePoint == '%'.code && isPercentEncoded(input, index)) {
            return true
        }
        return false
    }

    private fun isPercentEncoded(
        input: CharSequence,
        pos: Int
    ): Boolean =
        pos + 2 < input.length &&
                input[pos] == '%' &&
                input[pos + 1].parseHexDigit() != -1 &&
                input[pos + 2].parseHexDigit() != -1

    /**
     * Decodes percent-encoded characters in a string.
     * Invalid percent-encodings are left as-is.
     */
    fun decode(encoded: CharSequence, plusIsSpace: Boolean = false): CharSequence {
        if (!encoded.contains('%')) {
            return encoded
        }

        return buildString {
            var codePoint: Int
            var i = 0
            val bytes = mutableListOf<Byte>()
            while (i < encoded.length) {
                codePoint = encoded.getCodePointAt(i)
                if (codePoint == '%'.code && i + 2 < encoded.length) {
                    val d1 = encoded[i + 1].parseHexDigit()
                    val d2 = encoded[i + 2].parseHexDigit()
                    if (d1 != -1 && d2 != -1) {
                        bytes.add(((d1 shl 4) + d2).toByte())
                        i += 3
                        continue
                    }
                }

                if (bytes.isNotEmpty()) {
                    appendUtf8Buffer(bytes)
                    bytes.clear()
                }

                if (codePoint == '+'.code && plusIsSpace) {
                    i++
                    append(' ')
                } else {
                    i += codePoint.utfCodePointSize()
                    appendCodePoint(codePoint)
                }
            }
            if (bytes.isNotEmpty()) {
                appendUtf8Buffer(bytes)
            }
        }
    }

    internal fun StringBuilder.appendUtf8Buffer(buffer: List<Byte>): StringBuilder {
        var i = 0

        while (i < buffer.size) {
            val byte1 = buffer[i].toInt() and 0xFF

            when {
                // Single byte (0xxxxxxx)
                byte1 and 0x80 == 0 -> {
                    append(byte1.toChar())
                    i += 1
                }
                // Two bytes (110xxxxx 10xxxxxx)
                byte1 and 0xE0 == 0xC0 -> {
                    val byte2 = buffer[i + 1].toInt() and 0x3F
                    val codePoint = ((byte1 and 0x1F) shl 6) or byte2
                    append(codePoint.toChar())
                    i += 2
                }
                // Three bytes (1110xxxx 10xxxxxx 10xxxxxx)
                byte1 and 0xF0 == 0xE0 -> {
                    val byte2 = buffer[i + 1].toInt() and 0x3F
                    val byte3 = buffer[i + 2].toInt() and 0x3F
                    val codePoint = ((byte1 and 0x0F) shl 12) or (byte2 shl 6) or byte3
                    append(codePoint.toChar())
                    i += 3
                }
                // Four bytes (11110xxx 10xxxxxx 10xxxxxx 10xxxxxx)
                byte1 and 0xF8 == 0xF0 -> {
                    val byte2 = buffer[i + 1].toInt() and 0x3F
                    val byte3 = buffer[i + 2].toInt() and 0x3F
                    val byte4 = buffer[i + 3].toInt() and 0x3F
                    val codePoint = ((byte1 and 0x07) shl 18) or (byte2 shl 12) or (byte3 shl 6) or byte4
                    // Convert to surrogate pair for characters outside BMP
                    appendCodePoint(codePoint)
                    i += 4
                }
                else -> {
                    append('\uFFFD') // Replacement character for invalid bytes
                    i += 1
                }
            }
        }

        return this
    }

    /**
     * Percent-encodes a single byte.
     */
    private fun StringBuilder.appendByteAsHex(byte: Byte) {
        val value = byte.toInt() and 0xFF
        append(HEX_DIGITS[value shr 4])
        append(HEX_DIGITS[value and 0x0F])
    }

    private val HEX_DIGITS = "0123456789ABCDEF"

}
