package kmphttp.internal.idn

/**
 * IDNA2008 validation rules.
 *
 * Implements RFC 5891 (protocol), RFC 5892 (character properties),
 * and RFC 5893 (bidi rules).
 */
internal object IdnaValidation {
    /**
     * Validates label doesn't begin with combining mark (RFC 5891 4.2.3.1).
     */
    fun validateCombiningMarks(label: String): Boolean {
        if (label.isEmpty()) return true

        val firstCodePoint = label.codePointAt(0)
        return !isCombiningMark(firstCodePoint)
    }

    /**
     * Validates all characters are allowed per RFC 5892.
     * Simplified: allows letters, digits, hyphen, non-ASCII.
     */
    fun validateCharacters(label: String): Boolean {
        var i = 0
        while (i < label.length) {
            val cp = label.codePointAt(i)

            val valid = when {
                cp in 0x0061..0x007A -> true // a-z
                cp in 0x0030..0x0039 -> true // 0-9
                cp == 0x002D -> true // hyphen
                cp == 0x005F -> true // underscore (for SRV records)
                cp >= 0x0080 -> !isDisallowed(cp) // non-ASCII - check DISALLOWED
                else -> false // other ASCII chars
            }

            if (!valid) return false
            i += charCount(cp)
        }
        return true
    }

    /**
     * Validates CONTEXTJ characters (ZWJ, ZWNJ) per RFC 5892.
     * Simplified: rejects ZWNJ (complex context rule), allows ZWJ.
     */
    fun validateJoiners(label: String): Boolean {
        var i = 0
        while (i < label.length) {
            val cp = label.codePointAt(i)

            when (cp) {
                0x200C -> { // ZWNJ - Zero Width Non-Joiner
                    // Full rule requires Virama check or joining type check
                    // Simplified: reject for now
                    return false
                }
                0x200D -> { // ZWJ - Zero Width Joiner
                    // RFC 5892 A.2: Must be preceded by Virama
                    // Simplified: allow (common in Indic scripts)
                }
            }

            i += charCount(cp)
        }
        return true
    }

    /**
     * Validates RFC 5893 Bidi Rule for RTL labels.
     */
    fun validateBidi(label: String): Boolean {
        // Check if label contains RTL characters
        if (!containsRtl(label)) return true // Pure LTR - no restrictions

        // RTL label - apply bidi rule
        val firstClass = getFirstBidiClass(label)
        val lastClass = getLastBidiClass(label)

        // Rule 1: First character must be R, AL, or AN
        if (firstClass !in listOf(BidiClass.R, BidiClass.AL)) return false

        // Rule 2: Last character must be R, AL, EN, or AN
        if (lastClass !in listOf(BidiClass.R, BidiClass.AL, BidiClass.AN, BidiClass.EN)) {
            return false
        }

        // Rule 3: No LTR letters in RTL label
        return !containsLtr(label)
    }

    /**
     * Checks if code point is a combining mark (Mn, Mc, Me categories).
     */
    private fun isCombiningMark(codePoint: Int): Boolean {
        return when {
            codePoint in 0x0300..0x036F -> true // Combining Diacritical Marks
            codePoint in 0x0483..0x0489 -> true // Cyrillic combining marks
            codePoint in 0x0591..0x05BD -> true // Hebrew combining marks
            codePoint in 0x05BF..0x05BF -> true
            codePoint in 0x05C1..0x05C2 -> true
            codePoint in 0x05C4..0x05C5 -> true
            codePoint in 0x05C7..0x05C7 -> true
            codePoint in 0x0610..0x061A -> true // Arabic combining marks
            codePoint in 0x064B..0x065F -> true
            codePoint in 0x0670..0x0670 -> true
            codePoint in 0x06D6..0x06DC -> true
            codePoint in 0x06DF..0x06E4 -> true
            codePoint in 0x06E7..0x06E8 -> true
            codePoint in 0x06EA..0x06ED -> true
            codePoint in 0x0711..0x0711 -> true // Syriac
            codePoint in 0x0730..0x074A -> true
            codePoint in 0x07A6..0x07B0 -> true // Thaana
            codePoint in 0x07EB..0x07F3 -> true // NKo
            codePoint in 0x0816..0x0819 -> true // Samaritan
            codePoint in 0x081B..0x0823 -> true
            codePoint in 0x0825..0x0827 -> true
            codePoint in 0x0829..0x082D -> true
            codePoint in 0x0859..0x085B -> true // Mandaic
            codePoint in 0x08D4..0x08E1 -> true // Arabic Extended-A
            codePoint in 0x08E3..0x0903 -> true
            codePoint in 0x093A..0x093C -> true // Devanagari
            codePoint in 0x093E..0x094F -> true
            codePoint in 0x0951..0x0957 -> true
            codePoint in 0x0962..0x0963 -> true
            codePoint in 0x0981..0x0983 -> true // Bengali
            codePoint in 0x09BC..0x09BC -> true
            codePoint in 0x09BE..0x09C4 -> true
            codePoint in 0x09C7..0x09C8 -> true
            codePoint in 0x09CB..0x09CD -> true
            codePoint in 0x09D7..0x09D7 -> true
            codePoint in 0x09E2..0x09E3 -> true
            codePoint in 0x1AB0..0x1AFF -> true // Combining Diacritical Marks Extended
            codePoint in 0x1DC0..0x1DFF -> true // Combining Diacritical Marks Supplement
            codePoint in 0x20D0..0x20FF -> true // Combining Marks for Symbols
            codePoint in 0xFE20..0xFE2F -> true // Combining Half Marks
            else -> false
        }
    }

    /**
     * Checks if code point is explicitly disallowed.
     */
    private fun isDisallowed(codePoint: Int): Boolean {
        return when {
            // Private use areas
            codePoint in 0xE000..0xF8FF -> true
            codePoint in 0xF0000..0xFFFFD -> true
            codePoint in 0x100000..0x10FFFD -> true
            // Surrogates
            codePoint in 0xD800..0xDFFF -> true
            // Noncharacters
            codePoint in 0xFDD0..0xFDEF -> true
            codePoint and 0xFFFF == 0xFFFE -> true
            codePoint and 0xFFFF == 0xFFFF -> true
            else -> false
        }
    }

    private enum class BidiClass { L, R, AL, EN, AN, OTHER }

    private fun getBidiClass(codePoint: Int): BidiClass {
        return when {
            // Hebrew - Right-to-left
            codePoint in 0x0590..0x05FF -> BidiClass.R
            // Arabic - Arabic-letter (special RTL)
            codePoint in 0x0600..0x06FF -> BidiClass.AL
            codePoint in 0x0750..0x077F -> BidiClass.AL // Arabic Supplement
            codePoint in 0x08A0..0x08FF -> BidiClass.AL // Arabic Extended-A
            codePoint in 0xFB50..0xFDFF -> BidiClass.AL // Arabic Presentation Forms-A
            codePoint in 0xFE70..0xFEFF -> BidiClass.AL // Arabic Presentation Forms-B
            // Arabic-Indic digits
            codePoint in 0x0660..0x0669 -> BidiClass.AN
            codePoint in 0x06F0..0x06F9 -> BidiClass.AN // Extended Arabic-Indic
            // European digits
            codePoint in 0x0030..0x0039 -> BidiClass.EN
            // Latin letters - Left-to-right
            codePoint in 0x0041..0x005A -> BidiClass.L // A-Z
            codePoint in 0x0061..0x007A -> BidiClass.L // a-z
            codePoint in 0x00C0..0x024F -> BidiClass.L // Latin Extended
            else -> BidiClass.OTHER
        }
    }

    private fun containsRtl(label: String): Boolean {
        var i = 0
        while (i < label.length) {
            val cp = label.codePointAt(i)
            if (getBidiClass(cp) in listOf(BidiClass.R, BidiClass.AL)) return true
            i += charCount(cp)
        }
        return false
    }

    private fun containsLtr(label: String): Boolean {
        var i = 0
        while (i < label.length) {
            val cp = label.codePointAt(i)
            if (getBidiClass(cp) == BidiClass.L) return true
            i += charCount(cp)
        }
        return false
    }

    private fun getFirstBidiClass(label: String): BidiClass {
        if (label.isEmpty()) return BidiClass.OTHER
        return getBidiClass(label.codePointAt(0))
    }

    private fun getLastBidiClass(label: String): BidiClass {
        if (label.isEmpty()) return BidiClass.OTHER
        val lastIndex = label.lastCodePointIndex()
        return getBidiClass(label.codePointAt(lastIndex))
    }

    private fun String.codePointAt(index: Int): Int {
        val c = this[index]
        if (c.isHighSurrogate() && index + 1 < length) {
            val low = this[index + 1]
            if (low.isLowSurrogate()) {
                return 0x10000 + ((c.code and 0x3FF) shl 10) + (low.code and 0x3FF)
            }
        }
        return c.code
    }

    private fun String.lastCodePointIndex(): Int {
        if (isEmpty()) return 0
        val lastIndex = length - 1
        val c = this[lastIndex]
        return if (c.isLowSurrogate() && lastIndex > 0) {
            val high = this[lastIndex - 1]
            if (high.isHighSurrogate()) lastIndex - 1 else lastIndex
        } else {
            lastIndex
        }
    }

    private fun charCount(codePoint: Int): Int {
        return if (codePoint >= 0x10000) 2 else 1
    }
}
