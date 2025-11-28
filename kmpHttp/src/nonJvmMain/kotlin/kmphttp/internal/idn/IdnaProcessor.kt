package kmphttp.internal.idn

import doist.x.normalize.Form
import doist.x.normalize.normalize
import okio.Buffer

/**
 * Internal IDNA processing pipeline.
 *
 * Applies UTS#46 mapping, NFC normalization, and validation rules.
 * Uses OkHttp's IDNA mapping table for full UTS#46 compliance.
 */
internal object IdnaProcessor {
    /**
     * Process label through UTS#46 mapping and normalization.
     *
     * Steps:
     * 1. Map each codepoint (case-folding, normalization)
     * 2. NFC normalize
     * 3. Validate combining marks
     * 4. Validate characters
     * 5. Validate joiners
     * 6. Validate bidi rules
     *
     * @return processed label or null if invalid
     */
    fun processLabel(label: String): String? {
        if (label.isEmpty()) return null

        // Step 1: Map each codepoint using full UTS#46 mapping table
        val mapped = Buffer()
        var i = 0
        while (i < label.length) {
            val codePoint = label.codePointAt(i)
            if (!IDNA_MAPPING_TABLE.map(codePoint, mapped)) {
                return null // Disallowed character
            }
            i += charCount(codePoint)
        }

        // Step 2: NFC normalize
        val normalized = mapped.readUtf8().normalize(Form.NFC)

        // Step 3: Validate combining marks (must not begin with combining mark)
        if (!IdnaValidation.validateCombiningMarks(normalized)) return null

        // Step 4: Validate characters
        if (!IdnaValidation.validateCharacters(normalized)) return null

        // Step 5: Validate joiners (ZWJ/ZWNJ context)
        if (!IdnaValidation.validateJoiners(normalized)) return null

        // Step 6: Validate bidi rules
        if (!IdnaValidation.validateBidi(normalized)) return null

        // Step 7: Must be NFC (idempotence check)
        if (normalized != normalized.normalize(Form.NFC)) return null

        return normalized
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

    private fun charCount(codePoint: Int): Int {
        return if (codePoint >= 0x10000) 2 else 1
    }
}
