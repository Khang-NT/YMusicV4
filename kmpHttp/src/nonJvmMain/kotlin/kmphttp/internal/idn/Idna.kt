package kmphttp.internal.idn



/**
 * IDNA2008 implementation for Kotlin Multiplatform.
 *
 * Converts domain names between Unicode (U-labels) and ASCII (A-labels)
 * per RFC 5890-5895 and UTS#46.
 *
 * Example:
 * ```
 * Idna.toAscii("münchen.de") // "xn--mnchen-3ya.de"
 * Idna.toUnicode("xn--n3h.net") // "☃.net"
 * ```
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc5890">RFC 5890</a>
 * @see <a href="https://www.unicode.org/reports/tr46/">UTS #46</a>
 */
object Idna {
    /**
     * Converts Unicode domain name to ASCII (ToASCII operation).
     *
     * Process:
     * 1. Split domain by '.'
     * 2. For each label:
     *    a. Apply UTS#46 mapping (case-folding, normalization)
     *    b. Normalize to NFC
     *    c. Validate (combining marks, characters, joiners, bidi)
     *    d. Punycode encode if non-ASCII
     *    e. Prepend "xn--" if encoded
     * 3. Join labels with '.'
     *
     * @param domain Unicode domain name (e.g., "münchen.de")
     * @return ASCII domain name (e.g., "xn--mnchen-3ya.de"), or null if invalid
     */
    fun toAscii(domain: String): String? {
        if (domain.isEmpty()) return null

        // Handle trailing dot (allowed in DNS)
        val hasTrailingDot = domain.endsWith('.')
        val domainToProcess = if (hasTrailingDot) domain.dropLast(1) else domain

        // Split by dots
        val labels = domainToProcess.split('.')

        // Process each label
        val asciiLabels = labels.map { label ->
            if (label.isEmpty()) return null // Empty label invalid

            // Already a Punycode label? Validate and return
            if (label.startsWith(Punycode.PREFIX_STRING, ignoreCase = true)) {
                if (!validatePunycodeLabel(label)) return null
                return@map label.lowercase()
            }

            // Process label (map + normalize + validate)
            val processed = IdnaProcessor.processLabel(label) ?: return null

            // Check if encoding needed
            if (requiresEncoding(processed)) {
                // OkHttp's Punycode.encode() automatically adds xn-- prefix for non-ASCII
                val asciiLabel = Punycode.encode(processed) ?: return null
                if (!validateAsciiLabel(asciiLabel)) return null
                asciiLabel
            } else {
                // Already ASCII - validate and return
                if (!validateAsciiLabel(processed)) return null
                processed
            }
        }

        // Validate total domain length (253 octets max)
        val result = asciiLabels.joinToString(".")
        if (result.length > 253) return null

        return if (hasTrailingDot) "$result." else result
    }

    /**
     * Converts ASCII domain name to Unicode (ToUnicode operation).
     *
     * Process:
     * 1. Split domain by '.'
     * 2. For each label:
     *    a. If starts with "xn--", Punycode decode
     *    b. Otherwise, return as-is
     * 3. Join labels with '.'
     *
     * This operation never fails - returns original if decode fails.
     *
     * @param domain ASCII domain name (e.g., "xn--mnchen-3ya.de")
     * @return Unicode domain name (e.g., "münchen.de")
     */
    fun toUnicode(domain: String): String {
        if (domain.isEmpty()) return domain

        // Handle trailing dot
        val hasTrailingDot = domain.endsWith('.')
        val domainToProcess = if (hasTrailingDot) domain.dropLast(1) else domain

        // Split by dots
        val labels = domainToProcess.split('.')

        // Decode each label
        val unicodeLabels = labels.map { label ->
            if (label.startsWith(Punycode.PREFIX_STRING, ignoreCase = true)) {
                // OkHttp's Punycode.decode() expects full label with xn-- prefix
                Punycode.decode(label) ?: label // Return original if decode fails
            } else {
                // Not Punycode - return as-is
                label
            }
        }

        // Join and return
        val result = unicodeLabels.joinToString(".")
        return if (hasTrailingDot) "$result." else result
    }

    /**
     * Returns true if label contains non-ASCII characters requiring encoding.
     */
    private fun requiresEncoding(label: String): Boolean {
        return label.any { it.code >= 0x80 }
    }

    /**
     * Validates ASCII label (length, hyphens, valid chars).
     */
    private fun validateAsciiLabel(label: String): Boolean {
        // Length check (DNS limit: 63 octets per label)
        if (label.isEmpty() || label.length > 63) return false

        // No leading/trailing hyphens
        if (label.startsWith('-') || label.endsWith('-')) return false

        // No hyphens at positions 3-4 (unless valid A-label like xn--)
        if (label.length >= 4 &&
            label[2] == '-' &&
            label[3] == '-' &&
            !label.startsWith(Punycode.PREFIX_STRING, ignoreCase = true)
        ) {
            return false
        }

        // All characters must be LDH (letters, digits, hyphen)
        return label.all { c ->
            c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '-' || c == '_'
        }
    }

    /**
     * Validates an existing Punycode label.
     */
    private fun validatePunycodeLabel(label: String): Boolean {
        // Must start with xn--
        if (!label.startsWith(Punycode.PREFIX_STRING, ignoreCase = true)) return false

        // Length check
        if (label.length > 63) return false

        // Try to decode and re-encode to verify validity
        // OkHttp's decode() expects full label with xn-- prefix
        val decoded = Punycode.decode(label) ?: return false

        // Roundtrip check - OkHttp's encode() returns full label with xn-- prefix
        val reEncoded = Punycode.encode(decoded) ?: return false
        return label.equals(reEncoded, ignoreCase = true)
    }
}
