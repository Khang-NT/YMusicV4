package kmphttp.internal.idn

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class IdnaTest {
    @Test
    fun testToAsciiSimple() {
        assertEquals("xn--mnchen-3ya.de", Idna.toAscii("münchen.de"))
        assertEquals("xn--n3h.net", Idna.toAscii("☃.net"))
    }

    @Test
    fun testToAsciiAlreadyAscii() {
        assertEquals("example.com", Idna.toAscii("example.com"))
        assertEquals("test.org", Idna.toAscii("test.org"))
    }

    @Test
    fun testToAsciiCaseFolding() {
        assertEquals("example.com", Idna.toAscii("EXAMPLE.COM"))
        assertEquals("example.com", Idna.toAscii("Example.Com"))
    }

    @Test
    fun testToAsciiEmpty() {
        assertNull(Idna.toAscii(""))
    }

    @Test
    fun testToAsciiEmptyLabel() {
        // Empty label (double dot)
        assertNull(Idna.toAscii("example..com"))
    }

    @Test
    fun testToAsciiLeadingHyphen() {
        assertNull(Idna.toAscii("-test.com"))
    }

    @Test
    fun testToAsciiTrailingHyphen() {
        assertNull(Idna.toAscii("test-.com"))
    }

    @Test
    fun testToAsciiTrailingDot() {
        // Trailing dot is valid in DNS
        assertEquals("example.com.", Idna.toAscii("example.com."))
        assertEquals("xn--mnchen-3ya.de.", Idna.toAscii("münchen.de."))
    }

    @Test
    fun testToUnicodeSimple() {
        assertEquals("münchen.de", Idna.toUnicode("xn--mnchen-3ya.de"))
        assertEquals("☃.net", Idna.toUnicode("xn--n3h.net"))
    }

    @Test
    fun testToUnicodeAlreadyUnicode() {
        assertEquals("example.com", Idna.toUnicode("example.com"))
    }

    @Test
    fun testToUnicodeEmpty() {
        assertEquals("", Idna.toUnicode(""))
    }

    @Test
    fun testToUnicodeInvalidPunycode() {
        // Invalid punycode - the decode may return something or original
        // Per spec, ToUnicode never throws - returns best effort
        val result = Idna.toUnicode("xn--invalid--")
        assertNotNull(result) // Should not throw
    }

    @Test
    fun testToUnicodeTrailingDot() {
        assertEquals("münchen.de.", Idna.toUnicode("xn--mnchen-3ya.de."))
    }

    @Test
    fun testToUnicodeMixedCase() {
        // Case-insensitive prefix detection
        assertEquals("münchen.de", Idna.toUnicode("XN--mnchen-3ya.de"))
        assertEquals("münchen.de", Idna.toUnicode("Xn--mnchen-3ya.de"))
    }

    @Test
    fun testRoundTrip() {
        val domains = listOf(
            "münchen.de",
            "☃.net",
            "東京.jp",
            "example.com",
            "日本語.jp",
            "한국.kr"
        )

        domains.forEach { original ->
            val ascii = Idna.toAscii(original)
            assertNotNull(ascii, "toAscii failed for: $original")
            val unicode = Idna.toUnicode(ascii)
            assertEquals(original, unicode, "Roundtrip failed for: $original")
        }
    }

    @Test
    fun testMultiLabelUnicode() {
        assertEquals(
            "xn--mnchen-3ya.xn--n3h.example",
            Idna.toAscii("münchen.☃.example")
        )
    }

    @Test
    fun testExistingPunycode() {
        // Already Punycode - should validate and return lowercase
        assertEquals("xn--mnchen-3ya.de", Idna.toAscii("xn--mnchen-3ya.de"))
        assertEquals("xn--mnchen-3ya.de", Idna.toAscii("XN--MNCHEN-3YA.DE"))
    }

    @Test
    fun testJapanese() {
        val domain = "日本語.jp"
        val ascii = Idna.toAscii(domain)
        assertNotNull(ascii)
        assertEquals(domain, Idna.toUnicode(ascii))
    }

    @Test
    fun testChinese() {
        val domain = "中文.cn"
        val ascii = Idna.toAscii(domain)
        assertNotNull(ascii)
        assertEquals(domain, Idna.toUnicode(ascii))
    }

    @Test
    fun testKorean() {
        val domain = "한글.kr"
        val ascii = Idna.toAscii(domain)
        assertNotNull(ascii)
        assertEquals(domain, Idna.toUnicode(ascii))
    }

    @Test
    fun testArabic() {
        val domain = "العربية.مثال"
        val ascii = Idna.toAscii(domain)
        assertNotNull(ascii, "Arabic domain should be valid")
        assertEquals(domain, Idna.toUnicode(ascii))
    }

    @Test
    fun testHebrew() {
        val domain = "עברית.טסט"
        val ascii = Idna.toAscii(domain)
        assertNotNull(ascii, "Hebrew domain should be valid")
        assertEquals(domain, Idna.toUnicode(ascii))
    }

    @Test
    fun testEmoji() {
        val domain = "😀.example"
        val ascii = Idna.toAscii(domain)
        assertNotNull(ascii, "Emoji domain should be valid")
        assertEquals(domain, Idna.toUnicode(ascii))
    }

    @Test
    fun testLabelLengthLimit() {
        // 63 chars is max for a label
        val longLabel = "a".repeat(63)
        assertNotNull(Idna.toAscii("$longLabel.com"))

        // 64 chars should fail
        val tooLongLabel = "a".repeat(64)
        assertNull(Idna.toAscii("$tooLongLabel.com"))
    }

    @Test
    fun testDisallowedCharacters() {
        // Space - disallowed
        assertNull(Idna.toAscii("test example.com"))

        // @ - disallowed
        assertNull(Idna.toAscii("test@example.com"))

        // NULL - disallowed
        assertNull(Idna.toAscii("test\u0000.com"))
    }
}
