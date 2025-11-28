package kmphttp.internal.idn

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Tests for OkHttp's Punycode implementation.
 *
 * Note: OkHttp's Punycode.encode() automatically adds "xn--" prefix for non-ASCII labels,
 * and Punycode.decode() only decodes labels that start with "xn--".
 * Both methods handle multi-label domains (split by '.').
 */
class PunycodeTest {
    @Test
    fun testEncodeBasic() {
        // OkHttp's encode adds xn-- prefix for non-ASCII labels
        assertEquals("xn--bcher-kva", Punycode.encode("bücher"))
        assertEquals("xn--n3h", Punycode.encode("☃"))
        assertEquals("xn--mnchen-3ya", Punycode.encode("münchen"))
    }

    @Test
    fun testEncodeAscii() {
        // Pure ASCII - no encoding needed, returns as-is
        assertEquals("example", Punycode.encode("example"))
        assertEquals("test-123", Punycode.encode("test-123"))
    }

    @Test
    fun testEncodeEmpty() {
        assertEquals("", Punycode.encode(""))
    }

    @Test
    fun testDecodeBasic() {
        // OkHttp's decode requires xn-- prefix
        assertEquals("bücher", Punycode.decode("xn--bcher-kva"))
        assertEquals("münchen", Punycode.decode("xn--mnchen-3ya"))
    }

    @Test
    fun testDecodeNoPrefix() {
        // Without xn-- prefix, decode returns as-is
        assertEquals("bcher-kva", Punycode.decode("bcher-kva"))
        assertEquals("n3h", Punycode.decode("n3h"))
    }

    @Test
    fun testDecodeWithPrefix() {
        // Punycode without delimiter (no ASCII chars before encoding)
        assertEquals("☃", Punycode.decode("xn--n3h"))
    }

    @Test
    fun testDecodeEmpty() {
        assertEquals("", Punycode.decode(""))
    }

    @Test
    fun testRoundTrip() {
        val inputs = listOf(
            "münchen",
            "東京",
            "☃",
            "日本語",
            "한국어"
        )
        inputs.forEach { input ->
            val encoded = Punycode.encode(input)
            assertNotNull(encoded, "Encoding failed for: $input")
            val decoded = Punycode.decode(encoded)
            assertEquals(input, decoded, "Roundtrip failed for: $input")
        }
    }

    @Test
    fun testRoundTripAscii() {
        // Pure ASCII roundtrip
        val inputs = listOf("test", "example", "a-b-c")
        inputs.forEach { input ->
            val encoded = Punycode.encode(input)
            assertNotNull(encoded, "Encoding failed for: $input")
            assertEquals(input, encoded, "ASCII should not be modified")
        }
    }

    @Test
    fun testMultiLabelDomain() {
        // OkHttp's Punycode handles multi-label domains
        assertEquals("xn--mnchen-3ya.de", Punycode.encode("münchen.de"))
        assertEquals("münchen.de", Punycode.decode("xn--mnchen-3ya.de"))
    }

    @Test
    fun testPlainAsciiNoEncode() {
        // ASCII labels pass through unchanged
        val decoded = Punycode.decode("example")
        assertNotNull(decoded)
        assertEquals("example", decoded)
    }

    /**
     * RFC 3492 Section 7.1 Sample Strings
     * Note: OkHttp's encode adds xn-- prefix, so we test round-trip instead of exact output.
     * @see <a href="https://www.rfc-editor.org/rfc/rfc3492.html#section-7.1">RFC 3492 Section 7.1</a>
     */
    @Test
    fun testRfc3492SampleA_Arabic() {
        // (A) Arabic (Egyptian)
        val unicode = "\u0644\u064A\u0647\u0645\u0627\u0628\u062A\u0643\u0644\u0645\u0648\u0634\u0639\u0631\u0628\u064A\u061F"
        val encoded = Punycode.encode(unicode)
        assertNotNull(encoded)
        assertEquals(unicode, Punycode.decode(encoded))
    }

    @Test
    fun testRfc3492SampleB_ChineseSimplified() {
        // (B) Chinese (simplified)
        val unicode = "\u4ED6\u4EEC\u4E3A\u4EC0\u4E48\u4E0D\u8BF4\u4E2D\u6587"
        val encoded = Punycode.encode(unicode)
        assertNotNull(encoded)
        assertEquals(unicode, Punycode.decode(encoded))
    }

    @Test
    fun testRfc3492SampleC_ChineseTraditional() {
        // (C) Chinese (traditional)
        val unicode = "\u4ED6\u5011\u7232\u4EC0\u9EBD\u4E0D\u8AAA\u4E2D\u6587"
        val encoded = Punycode.encode(unicode)
        assertNotNull(encoded)
        assertEquals(unicode, Punycode.decode(encoded))
    }

    @Test
    fun testRfc3492SampleD_Czech() {
        // (D) Czech: Pročprostěnemluvíčesky
        val unicode = "Pro\u010Dprost\u011Bnemluv\u00ED\u010Desky"
        val encoded = Punycode.encode(unicode)
        assertNotNull(encoded)
        assertEquals(unicode, Punycode.decode(encoded))
    }

    @Test
    fun testRfc3492SampleE_Hebrew() {
        // (E) Hebrew
        val unicode = "\u05DC\u05DE\u05D4\u05D4\u05DD\u05E4\u05E9\u05D5\u05D8\u05DC\u05D0\u05DE\u05D3\u05D1\u05E8\u05D9\u05DD\u05E2\u05D1\u05E8\u05D9\u05EA"
        val encoded = Punycode.encode(unicode)
        assertNotNull(encoded)
        assertEquals(unicode, Punycode.decode(encoded))
    }

    @Test
    fun testRfc3492SampleF_Hindi() {
        // (F) Hindi (Devanagari)
        val unicode = "\u092F\u0939\u0932\u094B\u0917\u0939\u093F\u0928\u094D\u0926\u0940\u0915\u094D\u092F\u094B\u0902\u0928\u0939\u0940\u0902\u092C\u094B\u0932\u0938\u0915\u0924\u0947\u0939\u0948\u0902"
        val encoded = Punycode.encode(unicode)
        assertNotNull(encoded)
        assertEquals(unicode, Punycode.decode(encoded))
    }

    @Test
    fun testRfc3492SampleG_Japanese() {
        // (G) Japanese (kanji and hiragana)
        val unicode = "\u306A\u305C\u307F\u3093\u306A\u65E5\u672C\u8A9E\u3092\u8A71\u3057\u3066\u304F\u308C\u306A\u3044\u306E\u304B"
        val encoded = Punycode.encode(unicode)
        assertNotNull(encoded)
        assertEquals(unicode, Punycode.decode(encoded))
    }

    @Test
    fun testRfc3492SampleH_Korean() {
        // (H) Korean (Hangul syllables)
        val unicode = "\uC138\uACC4\uC758\uBAA8\uB4E0\uC0AC\uB78C\uB4E4\uC774\uD55C\uAD6D\uC5B4\uB97C\uC774\uD574\uD55C\uB2E4\uBA74\uC5BC\uB9C8\uB098\uC88B\uC744\uAE4C"
        val encoded = Punycode.encode(unicode)
        assertNotNull(encoded)
        assertEquals(unicode, Punycode.decode(encoded))
    }

    @Test
    fun testRfc3492SampleI_Russian() {
        // (I) Russian (Cyrillic)
        val unicode = "\u043F\u043E\u0447\u0435\u043C\u0443\u0436\u0435\u043E\u043D\u0438\u043D\u0435\u0433\u043E\u0432\u043E\u0440\u044F\u0442\u043F\u043E\u0440\u0443\u0441\u0441\u043A\u0438"
        val encoded = Punycode.encode(unicode)
        assertNotNull(encoded)
        assertEquals(unicode, Punycode.decode(encoded))
    }

    @Test
    fun testRfc3492SampleJ_Spanish() {
        // (J) Spanish: Porqu<é>nopuedensimplementehablarenEspa<ñ>ol
        val unicode = "Porqu\u00E9nopuedensimplementehablarenEspa\u00F1ol"
        val encoded = Punycode.encode(unicode)
        assertNotNull(encoded)
        assertEquals(unicode, Punycode.decode(encoded))
    }

    @Test
    fun testRfc3492SampleL_Japanese3nen() {
        // (L) 3<nen>B<gumi><kinpachi><sensei>
        val unicode = "3\u5E74B\u7D44\u91D1\u516B\u5148\u751F"
        val encoded = Punycode.encode(unicode)
        assertNotNull(encoded)
        assertEquals(unicode, Punycode.decode(encoded))
    }

    @Test
    fun testRfc3492SampleM_JapaneseAmuro() {
        // (M) <amuro><namie>-with-SUPER-MONKEYS
        val unicode = "\u5B89\u5BA4\u5948\u7F8E\u6075-with-SUPER-MONKEYS"
        val encoded = Punycode.encode(unicode)
        assertNotNull(encoded)
        assertEquals(unicode, Punycode.decode(encoded))
    }

    @Test
    fun testRfc3492SampleN_HelloAnotherWay() {
        // (N) Hello-Another-Way-<sorezore><no><basho>
        val unicode = "Hello-Another-Way-\u305D\u308C\u305E\u308C\u306E\u5834\u6240"
        val encoded = Punycode.encode(unicode)
        assertNotNull(encoded)
        assertEquals(unicode, Punycode.decode(encoded))
    }

    @Test
    fun testRfc3492SampleO_JapaneseHitotsu() {
        // (O) <hitotsu><yane><no><shita>2
        val unicode = "\u3072\u3068\u3064\u5C4B\u6839\u306E\u4E0B2"
        val encoded = Punycode.encode(unicode)
        assertNotNull(encoded)
        assertEquals(unicode, Punycode.decode(encoded))
    }

    @Test
    fun testRfc3492SampleP_JapaneseMaji() {
        // (P) Maji<de>Koi<suru>5<byou><mae>
        val unicode = "Maji\u3067Koi\u3059\u308B5\u79D2\u524D"
        val encoded = Punycode.encode(unicode)
        assertNotNull(encoded)
        assertEquals(unicode, Punycode.decode(encoded))
    }

    @Test
    fun testRfc3492SampleQ_JapanesePafii() {
        // (Q) <pafii>de<runba>
        val unicode = "\u30D1\u30D5\u30A3\u30FCde\u30EB\u30F3\u30D0"
        val encoded = Punycode.encode(unicode)
        assertNotNull(encoded)
        assertEquals(unicode, Punycode.decode(encoded))
    }

    @Test
    fun testRfc3492SampleR_JapaneseSono() {
        // (R) <sono><supiido><de>
        val unicode = "\u305D\u306E\u30B9\u30D4\u30FC\u30C9\u3067"
        val encoded = Punycode.encode(unicode)
        assertNotNull(encoded)
        assertEquals(unicode, Punycode.decode(encoded))
    }

    @Test
    fun testSurrogatePairs() {
        // Emoji (surrogate pairs)
        val emoji = "😀"
        val encoded = Punycode.encode(emoji)
        assertNotNull(encoded)
        val decoded = Punycode.decode(encoded)
        assertEquals(emoji, decoded)
    }

    @Test
    fun testMixedAsciiUnicode() {
        // Mixed ASCII and Unicode
        val input = "aüb"
        val encoded = Punycode.encode(input)
        assertNotNull(encoded)
        assertEquals(input, Punycode.decode(encoded))
    }
}
