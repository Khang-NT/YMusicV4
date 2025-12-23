package kmpcommon

fun Char.parseHexDigit(): Int =
    when (this) {
        in '0'..'9' -> this - '0'
        in 'a'..'f' -> this - 'a' + 10
        in 'A'..'F' -> this - 'A' + 10
        else -> -1
    }

fun Char.isHexDigit() = parseHexDigit() != -1

fun Char.isAlphabetLetter(): Boolean {
    return this in 'a'..'z' || this in 'A'..'Z'
}

fun Char.isAlphabetLetterOrDigit(): Boolean {
    return isAlphabetLetter() || isDigit()
}

fun CharSequence.getCodePointAt(index: Int): Int {
    val char = this[index]
    if (char.isHighSurrogate() && index + 1 < length) {
        val nextChar = this[index + 1]
        if (nextChar.isLowSurrogate()) {
            return ((char.code - 0xD800) * 0x400) + (nextChar.code - 0xDC00) + 0x10000
        }
    }
    return char.code
}

fun CharSequence.subSequence(startIndex: Int) = subSequence(startIndex, length)

fun CharSequence.splitSequence(char: Char, offset: Int = 0): Sequence<CharSequence> {
    return splitRange(char, offset).map { subSequence(it) }
}


fun CharSequence.splitRange(char: Char, offset: Int = 0): Sequence<IntRange> {
    return object : Sequence<IntRange> {
        override fun iterator() = object : Iterator<IntRange> {
            var previousOffset = offset
            var next: Int? = findNext(offset)
            var hasTrailing = true

            override fun next(): IntRange {
                val found = next
                return if (found != null) {
                    val result = previousOffset until found
                    previousOffset = found + 1
                    next = findNext(found + 1)
                    result
                } else {
                    // Return trailing segment after last delimiter
                    hasTrailing = false
                    previousOffset until length
                }
            }

            override fun hasNext(): Boolean {
                return next != null || (hasTrailing && previousOffset <= length)
            }

            private fun findNext(currentOffset: Int): Int? {
                var index = currentOffset
                while (index < length) {
                    if (this@splitRange[index] == char) {
                        return index
                    }
                    index++
                }
                return null
            }
        }
    }
}

/**
 * Append UTF code point.
 */
fun StringBuilder.appendCodePoint(codePoint: Int) {
    when {
        codePoint <= 0xFFFF -> {
            // BMP character (Basic Multilingual Plane)
            append(codePoint.toChar())
        }
        codePoint <= 0x10FFFF -> {
            // Convert to surrogate pair
            val offset = codePoint - 0x10000
            val highSurrogate = (0xD800 + (offset shr 10)).toChar()
            val lowSurrogate = (0xDC00 + (offset and 0x3FF)).toChar()
            append(highSurrogate)
            append(lowSurrogate)
        }
        else -> {
            // Invalid code point, skip or use replacement character
            append('\uFFFD') // Unicode replacement character
        }
    }
}

/**
 * Calculates the number of UTF-16 code units required for a code point.
 * - BMP characters (U+0000 to U+FFFF) 1 code unit
 * - Supplementary characters (from U+10000): 2 code units
 *
 * @return Number of UTF-16 code units required
 */
fun Int.utfCodePointSize(): Int {
    if (this <= 0xFFFF) return 1
    return 2
}

fun String.indexOf(char: Char, within: IntRange): Int {
    for (i in within) {
        if (this[i] == char) return i
    }
    return -1
}