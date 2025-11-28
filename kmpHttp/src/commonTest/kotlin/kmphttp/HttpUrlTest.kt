/*
 * Copyright (C) 2015 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:Suppress("unused") // Suppress unused imports warning

package kmphttp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

@Suppress("HttpUrlsUsage") // Don't warn if we should be using https://.
class HttpUrlTest {
    private fun parse(url: String): HttpUrl = url.toHttpUrl()

    private fun assertInvalid(string: String, exceptionMessage: String?) {
        try {
            val result = string.toHttpUrl()
            if (exceptionMessage != null) {
                fail("Expected failure with $exceptionMessage but got $result")
            } else {
                fail("Expected failure but got $result")
            }
        } catch (iae: IllegalArgumentException) {
            if (exceptionMessage != null) {
                assertEquals(exceptionMessage, iae.message)
            }
        }
    }

    @Test
    fun parseTrimsAsciiWhitespace() {
        val expected = parse("http://host/")
        // Leading.
        assertEquals(expected, parse("http://host/\u000c\n\t \r"))
        // Trailing.
        assertEquals(expected, parse("\r\n\u000c \thttp://host/"))
        // Both.
        assertEquals(expected, parse(" http://host/ "))
        assertEquals(expected, parse("    http://host/    "))
        assertEquals(expected, parse("http://host/").resolve("   "))
        assertEquals(expected, parse("http://host/").resolve("  .  "))
    }

    @Test
    fun parseHostAsciiNonPrintable() {
        val host = "host\u0001"
        assertInvalid("http://$host/", "Invalid URL host: \"host\u0001\"")
    }

    @Test
    fun parseDoesNotTrimOtherWhitespaceCharacters() {
        // line tabulation
        assertEquals("/%0B", parse("http://h/\u000b").encodedPath)
        // information separator 4
        assertEquals("/%1C", parse("http://h/\u001c").encodedPath)
        // information separator 3
        assertEquals("/%1D", parse("http://h/\u001d").encodedPath)
        // information separator 2
        assertEquals("/%1E", parse("http://h/\u001e").encodedPath)
        // information separator 1
        assertEquals("/%1F", parse("http://h/\u001f").encodedPath)
    }

    @Test
    fun newBuilderResolve() {
        val base = parse("http://host/a/b")
        assertEquals(parse("https://host2/"), base.newBuilder("https://host2")!!.build())
        assertEquals(parse("http://host2/"), base.newBuilder("//host2")!!.build())
        assertEquals(parse("http://host/path"), base.newBuilder("/path")!!.build())
        assertEquals(parse("http://host/a/path"), base.newBuilder("path")!!.build())
        assertEquals(parse("http://host/a/b?query"), base.newBuilder("?query")!!.build())
        assertEquals(parse("http://host/a/b#fragment"), base.newBuilder("#fragment")!!.build())
        assertEquals(parse("http://host/a/b"), base.newBuilder("")!!.build())
        assertNull(base.newBuilder("ftp://b"))
        assertNull(base.newBuilder("ht+tp://b"))
        assertNull(base.newBuilder("ht-tp://b"))
        assertNull(base.newBuilder("ht.tp://b"))
    }

    @Test
    fun redactedUrl() {
        val baseWithPasswordAndUsername = parse("http://username:password@host/a/b#fragment")
        val baseWithUsernameOnly = parse("http://username@host/a/b#fragment")
        val baseWithPasswordOnly = parse("http://password@host/a/b#fragment")
        assertEquals("http://host/...", baseWithPasswordAndUsername.redact())
        assertEquals("http://host/...", baseWithUsernameOnly.redact())
        assertEquals("http://host/...", baseWithPasswordOnly.redact())
    }

    @Test
    fun resolveNoScheme() {
        val base = parse("http://host/a/b")
        assertEquals(parse("http://host2/"), base.resolve("//host2"))
        assertEquals(parse("http://host/path"), base.resolve("/path"))
        assertEquals(parse("http://host/a/path"), base.resolve("path"))
        assertEquals(parse("http://host/a/b?query"), base.resolve("?query"))
        assertEquals(parse("http://host/a/b#fragment"), base.resolve("#fragment"))
        assertEquals(parse("http://host/a/b"), base.resolve(""))
        assertEquals(parse("http://host/path"), base.resolve("\\path"))
    }

    @Test
    fun resolveUnsupportedScheme() {
        val base = parse("http://a/")
        assertNull(base.resolve("ftp://b"))
        assertNull(base.resolve("ht+tp://b"))
        assertNull(base.resolve("ht-tp://b"))
        assertNull(base.resolve("ht.tp://b"))
    }

    @Test
    fun resolveSchemeLikePath() {
        val base = parse("http://a/")
        assertEquals(parse("http://a/http//b/"), base.resolve("http//b/"))
        assertEquals(parse("http://a/ht+tp//b/"), base.resolve("ht+tp//b/"))
        assertEquals(parse("http://a/ht-tp//b/"), base.resolve("ht-tp//b/"))
        assertEquals(parse("http://a/ht.tp//b/"), base.resolve("ht.tp//b/"))
    }

    @Test
    fun rfc3886NormalExamples() {
        val url = parse("http://a/b/c/d;p?q")
        // No 'g:' scheme in HttpUrl.
        assertNull(url.resolve("g:h"))
        assertEquals(parse("http://a/b/c/g"), url.resolve("g"))
        assertEquals(parse("http://a/b/c/g"), url.resolve("./g"))
        assertEquals(parse("http://a/b/c/g/"), url.resolve("g/"))
        assertEquals(parse("http://a/g"), url.resolve("/g"))
        assertEquals(parse("http://g"), url.resolve("//g"))
        assertEquals(parse("http://a/b/c/d;p?y"), url.resolve("?y"))
        assertEquals(parse("http://a/b/c/g?y"), url.resolve("g?y"))
        assertEquals(parse("http://a/b/c/d;p?q#s"), url.resolve("#s"))
        assertEquals(parse("http://a/b/c/g#s"), url.resolve("g#s"))
        assertEquals(parse("http://a/b/c/g?y#s"), url.resolve("g?y#s"))
        assertEquals(parse("http://a/b/c/;x"), url.resolve(";x"))
        assertEquals(parse("http://a/b/c/g;x"), url.resolve("g;x"))
        assertEquals(parse("http://a/b/c/g;x?y#s"), url.resolve("g;x?y#s"))
        assertEquals(parse("http://a/b/c/d;p?q"), url.resolve(""))
        assertEquals(parse("http://a/b/c/"), url.resolve("."))
        assertEquals(parse("http://a/b/c/"), url.resolve("./"))
        assertEquals(parse("http://a/b/"), url.resolve(".."))
        assertEquals(parse("http://a/b/"), url.resolve("../"))
        assertEquals(parse("http://a/b/g"), url.resolve("../g"))
        assertEquals(parse("http://a/"), url.resolve("../.."))
        assertEquals(parse("http://a/"), url.resolve("../../"))
        assertEquals(parse("http://a/g"), url.resolve("../../g"))
    }

    @Test
    fun rfc3886AbnormalExamples() {
        val url = parse("http://a/b/c/d;p?q")
        assertEquals(parse("http://a/g"), url.resolve("../../../g"))
        assertEquals(parse("http://a/g"), url.resolve("../../../../g"))
        assertEquals(parse("http://a/g"), url.resolve("/./g"))
        assertEquals(parse("http://a/g"), url.resolve("/../g"))
        assertEquals(parse("http://a/b/c/g."), url.resolve("g."))
        assertEquals(parse("http://a/b/c/.g"), url.resolve(".g"))
        assertEquals(parse("http://a/b/c/g.."), url.resolve("g.."))
        assertEquals(parse("http://a/b/c/..g"), url.resolve("..g"))
        assertEquals(parse("http://a/b/g"), url.resolve("./../g"))
        assertEquals(parse("http://a/b/c/g/"), url.resolve("./g/."))
        assertEquals(parse("http://a/b/c/g/h"), url.resolve("g/./h"))
        assertEquals(parse("http://a/b/c/h"), url.resolve("g/../h"))
        assertEquals(parse("http://a/b/c/g;x=1/y"), url.resolve("g;x=1/./y"))
        assertEquals(parse("http://a/b/c/y"), url.resolve("g;x=1/../y"))
        assertEquals(parse("http://a/b/c/g?y/./x"), url.resolve("g?y/./x"))
        assertEquals(parse("http://a/b/c/g?y/../x"), url.resolve("g?y/../x"))
        assertEquals(parse("http://a/b/c/g#s/./x"), url.resolve("g#s/./x"))
        assertEquals(parse("http://a/b/c/g#s/../x"), url.resolve("g#s/../x"))
        // "http:g" also okay.
        assertEquals(parse("http://a/b/c/g"), url.resolve("http:g"))
    }

    @Test
    fun parseAuthoritySlashCountDoesntMatter() {
        assertEquals(parse("http://host/path"), parse("http:host/path"))
        assertEquals(parse("http://host/path"), parse("http:/host/path"))
        assertEquals(parse("http://host/path"), parse("http:\\host/path"))
        assertEquals(parse("http://host/path"), parse("http://host/path"))
        assertEquals(parse("http://host/path"), parse("http:\\/host/path"))
        assertEquals(parse("http://host/path"), parse("http:/\\host/path"))
        assertEquals(parse("http://host/path"), parse("http:\\\\host/path"))
        assertEquals(parse("http://host/path"), parse("http:///host/path"))
        assertEquals(parse("http://host/path"), parse("http:\\//host/path"))
        assertEquals(parse("http://host/path"), parse("http:/\\/host/path"))
        assertEquals(parse("http://host/path"), parse("http://\\host/path"))
        assertEquals(parse("http://host/path"), parse("http:\\\\/host/path"))
        assertEquals(parse("http://host/path"), parse("http:/\\\\host/path"))
        assertEquals(parse("http://host/path"), parse("http:\\\\\\host/path"))
        assertEquals(parse("http://host/path"), parse("http:////host/path"))
    }

    @Test
    fun username() {
        assertEquals(parse("http://host/path"), parse("http://@host/path"))
        assertEquals(parse("http://user@host/path"), parse("http://user@host/path"))
    }

    @Test
    fun authorityWithMultipleAtSigns() {
        val httpUrl = parse("http://foo@bar@baz/path")
        assertEquals("foo@bar", httpUrl.username)
        assertEquals("", httpUrl.password)
        assertEquals(parse("http://foo%40bar@baz/path"), httpUrl)
    }

    @Test
    fun authorityWithMultipleColons() {
        val httpUrl = parse("http://foo:pass1@bar:pass2@baz/path")
        assertEquals("foo", httpUrl.username)
        assertEquals("pass1@bar:pass2", httpUrl.password)
        assertEquals(parse("http://foo:pass1%40bar%3Apass2@baz/path"), httpUrl)
    }

    @Test
    fun usernameAndPassword() {
        assertEquals(parse("http://username:password@host/path"), parse("http://username:password@host/path"))
        assertEquals(parse("http://username@host/path"), parse("http://username:@host/path"))
    }

    @Test
    fun passwordWithEmptyUsername() {
        assertEquals(parse("http://host/path"), parse("http://:@host/path"))
        assertEquals("password%40", parse("http://:password@@host/path").encodedPassword)
    }

    @Test
    fun unprintableCharactersArePercentEncoded() {
        assertEquals("/%00", parse("http://host/\u0000").encodedPath)
        assertEquals("/%08", parse("http://host/\u0008").encodedPath)
        assertEquals("/%EF%BF%BD", parse("http://host/\ufffd").encodedPath)
    }

    @Test
    fun hostContainsIllegalCharacter() {
        assertInvalid("http://\n/", "Invalid URL host: \"\n\"")
        assertInvalid("http:// /", "Invalid URL host: \" \"")
        assertInvalid("http://%20/", "Invalid URL host: \"%20\"")
    }

    @Test
    fun hostnameLowercaseCharactersMappedDirectly() {
        assertEquals("abcd", parse("http://abcd").host)
        assertEquals("xn--4xa", parse("http://σ").host)
    }

    @Test
    fun hostnameUppercaseCharactersConvertedToLowercase() {
        assertEquals("abcd", parse("http://ABCD").host)
        assertEquals("xn--4xa", parse("http://Σ").host)
    }

    @Test
    fun hostnameIgnoredCharacters() {
        // The soft hyphen (­) should be ignored.
        assertEquals("abcd", parse("http://AB\u00adCD").host)
    }

    @Test
    fun hostnameMultipleCharacterMapping() {
        // Map the single character telephone symbol (℡) to the string "tel".
        assertEquals("tel", parse("http://\u2121").host)
    }

    @Test
    fun hostIpv6() {
        // Square braces are absent from host()...
        assertEquals("::1", parse("http://[::1]/").host)
        // ... but they're included in toString().
        assertEquals("http://[::1]/", parse("http://[::1]/").toString())
        // IPv6 colons don't interfere with port numbers or passwords.
        assertEquals(8080, parse("http://[::1]:8080/").port)
        assertEquals("password", parse("http://user:password@[::1]/").password)
        assertEquals("::1", parse("http://user:password@[::1]:8080/").host)
        // Permit the contents of IPv6 addresses to be percent-encoded...
        assertEquals("::1", parse("http://[%3A%3A%31]/").host)
        // Including the Square braces themselves! (This is what Chrome does.)
        assertEquals("::1", parse("http://%5B%3A%3A1%5D/").host)
    }

    @Test
    fun hostIpv6AddressDifferentFormats() {
        val a3 = "2001:db8::1:0:0:1"
        assertEquals(a3, parse("http://[2001:db8:0:0:1:0:0:1]").host)
        assertEquals(a3, parse("http://[2001:0db8:0:0:1:0:0:1]").host)
        assertEquals(a3, parse("http://[2001:db8::1:0:0:1]").host)
        assertEquals(a3, parse("http://[2001:db8::0:1:0:0:1]").host)
        assertEquals(a3, parse("http://[2001:0db8::1:0:0:1]").host)
        assertEquals(a3, parse("http://[2001:db8:0:0:1::1]").host)
        assertEquals(a3, parse("http://[2001:db8:0000:0:1::1]").host)
        assertEquals(a3, parse("http://[2001:DB8:0:0:1::1]").host)
    }

    @Test
    fun hostIpv6AddressLeadingCompression() {
        assertEquals("::1", parse("http://[::0001]").host)
        assertEquals("::1", parse("http://[0000::0001]").host)
        assertEquals("::1", parse("http://[0000:0000:0000:0000:0000:0000:0000:0001]").host)
        assertEquals("::1", parse("http://[0000:0000:0000:0000:0000:0000::0001]").host)
    }

    @Test
    fun hostIpv6AddressTrailingCompression() {
        assertEquals("1::", parse("http://[0001:0000::]").host)
        assertEquals("1::", parse("http://[0001::0000]").host)
        assertEquals("1::", parse("http://[0001::]").host)
        assertEquals("1::", parse("http://[1::]").host)
    }

    @Test
    fun hostIpv6AddressTooManyDigitsInGroup() {
        assertInvalid(
            "http://[00000:0000:0000:0000:0000:0000:0000:0001]",
            "Invalid URL host: \"[00000:0000:0000:0000:0000:0000:0000:0001]\""
        )
        assertInvalid("http://[::00001]", "Invalid URL host: \"[::00001]\"")
    }

    @Test
    fun hostIpv6AddressMisplacedColons() {
        assertInvalid(
            "http://[:0000:0000:0000:0000:0000:0000:0000:0001]",
            "Invalid URL host: \"[:0000:0000:0000:0000:0000:0000:0000:0001]\""
        )
        assertInvalid("http://[:1]", "Invalid URL host: \"[:1]\"")
        assertInvalid("http://[:::1]", "Invalid URL host: \"[:::1]\"")
        assertInvalid("http://[1:]", "Invalid URL host: \"[1:]\"")
        assertInvalid("http://[1:::]", "Invalid URL host: \"[1:::]\"")
        assertInvalid("http://[1:::1]", "Invalid URL host: \"[1:::1]\"")
    }

    @Test
    fun hostIpv6AddressTooManyGroups() {
        assertInvalid(
            "http://[0000:0000:0000:0000:0000:0000:0000:0000:0001]",
            "Invalid URL host: \"[0000:0000:0000:0000:0000:0000:0000:0000:0001]\""
        )
    }

    @Test
    fun hostIpv6AddressTooMuchCompression() {
        assertInvalid(
            "http://[0000::0000:0000:0000:0000::0001]",
            "Invalid URL host: \"[0000::0000:0000:0000:0000::0001]\""
        )
        assertInvalid(
            "http://[::0000:0000:0000:0000::0001]",
            "Invalid URL host: \"[::0000:0000:0000:0000::0001]\""
        )
    }

    @Test
    fun hostIpv6WithIpv4Suffix() {
        assertEquals("::1:ffff:ffff", parse("http://[::1:255.255.255.255]/").host)
        assertEquals("::1:0:0", parse("http://[0:0:0:0:0:1:0.0.0.0]/").host)
    }

    @Test
    fun hostIpv6Malformed() {
        assertInvalid("http://[::g]/", "Invalid URL host: \"[::g]\"")
    }

    @Test
    fun hostIpv6CanonicalForm() {
        assertEquals("abcd:ef01:2345:6789:abcd:ef01:2345:6789", parse("http://[abcd:ef01:2345:6789:abcd:ef01:2345:6789]/").host)
        assertEquals("a::b:0:0:0", parse("http://[a:0:0:0:b:0:0:0]/").host)
        assertEquals("a:b:0:0:c::", parse("http://[a:b:0:0:c:0:0:0]/").host)
        assertEquals("a:b::c:0:0", parse("http://[a:b:0:0:0:c:0:0]/").host)
        assertEquals("a::b:0:0:0", parse("http://[a:0:0:0:b:0:0:0]/").host)
        assertEquals("::a:b:0:0:0", parse("http://[0:0:0:a:b:0:0:0]/").host)
        assertEquals("::a:0:0:0:b", parse("http://[0:0:0:a:0:0:0:b]/").host)
        assertEquals("0:a:b:c:d:e:f:1", parse("http://[0:a:b:c:d:e:f:1]/").host)
        assertEquals("a:b:c:d:e:f:1:0", parse("http://[a:b:c:d:e:f:1:0]/").host)
        assertEquals("ff01::101", parse("http://[FF01:0:0:0:0:0:0:101]/").host)
        assertEquals("2001:db8::1", parse("http://[2001:db8::1]/").host)
        assertEquals("2001:db8::2:1", parse("http://[2001:db8:0:0:0:0:2:1]/").host)
        assertEquals("2001:db8:0:1:1:1:1:1", parse("http://[2001:db8:0:1:1:1:1:1]/").host)
        assertEquals("2001:db8::1:0:0:1", parse("http://[2001:db8:0:0:1:0:0:1]/").host)
        assertEquals("2001:0:0:1::1", parse("http://[2001:0:0:1:0:0:0:1]/").host)
        assertEquals("1::", parse("http://[1:0:0:0:0:0:0:0]/").host)
        assertEquals("::1", parse("http://[0:0:0:0:0:0:0:1]/").host)
        assertEquals("::", parse("http://[0:0:0:0:0:0:0:0]/").host)
        assertEquals("192.168.1.254", parse("http://[::ffff:c0a8:1fe]/").host)
    }

    @Test
    fun hostIpv6Builder() {
        val base = parse("http://example.com/")
        assertEquals("http://[::1]/", base.newBuilder().host("[::1]").build().toString())
        assertEquals("http://[::1]/", base.newBuilder().host("[::0001]").build().toString())
        assertEquals("http://[::1]/", base.newBuilder().host("::1").build().toString())
        assertEquals("http://[::1]/", base.newBuilder().host("::0001").build().toString())
    }

    @Test
    fun fragmentNonAscii() {
        val url = parse("http://host/#Σ")
        assertEquals("http://host/#Σ", url.toString())
        assertEquals("Σ", url.fragment)
        assertEquals("Σ", url.encodedFragment)
    }

    @Test
    fun fragmentPercentEncodedNonAscii() {
        val url = parse("http://host/#%C2%80")
        assertEquals("http://host/#%C2%80", url.toString())
        assertEquals("\u0080", url.fragment)
        assertEquals("%C2%80", url.encodedFragment)
    }

    @Test
    fun fragmentPercentEncodedPartialCodePoint() {
        val url = parse("http://host/#%80")
        assertEquals("http://host/#%80", url.toString())
        // Unicode replacement character.
        assertEquals("\ufffd", url.fragment)
        assertEquals("%80", url.encodedFragment)
    }

    @Test
    fun relativePath() {
        val base = parse("http://host/a/b/c")
        assertEquals(parse("http://host/a/b/d/e/f"), base.resolve("d/e/f"))
        assertEquals(parse("http://host/d/e/f"), base.resolve("../../d/e/f"))
        assertEquals(parse("http://host/a/"), base.resolve(".."))
        assertEquals(parse("http://host/"), base.resolve("../.."))
        assertEquals(parse("http://host/"), base.resolve("../../.."))
        assertEquals(parse("http://host/a/b/"), base.resolve("."))
        assertEquals(parse("http://host/a/"), base.resolve("././.."))
        assertEquals(parse("http://host/a/b/c/"), base.resolve("c/d/../e/../"))
        assertEquals(parse("http://host/a/b/..e/"), base.resolve("..e/"))
        assertEquals(parse("http://host/a/b/e/f../"), base.resolve("e/f../"))
        assertEquals(parse("http://host/a/"), base.resolve("%2E."))
        assertEquals(parse("http://host/a/"), base.resolve(".%2E"))
        assertEquals(parse("http://host/a/"), base.resolve("%2E%2E"))
        assertEquals(parse("http://host/a/"), base.resolve("%2e."))
        assertEquals(parse("http://host/a/"), base.resolve(".%2e"))
        assertEquals(parse("http://host/a/"), base.resolve("%2e%2e"))
        assertEquals(parse("http://host/a/b/"), base.resolve("%2E"))
        assertEquals(parse("http://host/a/b/"), base.resolve("%2e"))
    }

    @Test
    fun relativePathWithTrailingSlash() {
        val base = parse("http://host/a/b/c/")
        assertEquals(parse("http://host/a/b/"), base.resolve(".."))
        assertEquals(parse("http://host/a/b/"), base.resolve("../"))
        assertEquals(parse("http://host/a/"), base.resolve("../.."))
        assertEquals(parse("http://host/a/"), base.resolve("../../"))
        assertEquals(parse("http://host/"), base.resolve("../../.."))
        assertEquals(parse("http://host/"), base.resolve("../../../"))
        assertEquals(parse("http://host/"), base.resolve("../../../.."))
        assertEquals(parse("http://host/"), base.resolve("../../../../"))
        assertEquals(parse("http://host/a"), base.resolve("../../../../a"))
        assertEquals(parse("http://host/"), base.resolve("../../../../a/.."))
        assertEquals(parse("http://host/a/"), base.resolve("../../../../a/b/.."))
    }

    @Test
    fun pathWithBackslash() {
        val base = parse("http://host/a/b/c")
        assertEquals(parse("http://host/a/b/d/e/f"), base.resolve("d\\e\\f"))
        assertEquals(parse("http://host/d/e/f"), base.resolve("../..\\d\\e\\f"))
        assertEquals(parse("http://host/"), base.resolve("..\\.."))
    }

    @Test
    fun relativePathWithSameScheme() {
        val base = parse("http://host/a/b/c")
        assertEquals(parse("http://host/a/b/d/e/f"), base.resolve("http:d/e/f"))
        assertEquals(parse("http://host/d/e/f"), base.resolve("http:../../d/e/f"))
    }

    @Test
    fun decodeUsername() {
        assertEquals("user", parse("http://user@host/").username)
        assertEquals("\uD83C\uDF69", parse("http://%F0%9F%8D%A9@host/").username)
    }

    @Test
    fun decodePassword() {
        assertEquals("password", parse("http://user:password@host/").password)
        assertEquals("", parse("http://user:@host/").password)
        assertEquals("\uD83C\uDF69", parse("http://user:%F0%9F%8D%A9@host/").password)
    }

    @Test
    fun decodeSlashCharacterInDecodedPathSegment() {
        assertEquals(listOf("a/b/c"), parse("http://host/a%2Fb%2Fc").pathSegments)
    }

    @Test
    fun decodeEmptyPathSegments() {
        assertEquals(listOf(""), parse("http://host/").pathSegments)
    }

    @Test
    fun percentDecode() {
        assertEquals(listOf("\u0000"), parse("http://host/%00").pathSegments)
        assertEquals(listOf("a", "\u2603", "c"), parse("http://host/a/%E2%98%83/c").pathSegments)
        assertEquals(listOf("a", "\uD83C\uDF69", "c"), parse("http://host/a/%F0%9F%8D%A9/c").pathSegments)
        assertEquals(listOf("a", "b", "c"), parse("http://host/a/%62/c").pathSegments)
        assertEquals(listOf("a", "z", "c"), parse("http://host/a/%7A/c").pathSegments)
        assertEquals(listOf("a", "z", "c"), parse("http://host/a/%7a/c").pathSegments)
    }

    @Test
    fun malformedPercentEncoding() {
        assertEquals(listOf("a%f", "b"), parse("http://host/a%f/b").pathSegments)
        assertEquals(listOf("%", "b"), parse("http://host/%/b").pathSegments)
        assertEquals(listOf("%"), parse("http://host/%").pathSegments)
        assertEquals(listOf("%00"), parse("http://github.com/%%30%30").pathSegments)
    }

    @Test
    fun malformedUtf8Encoding() {
        // Replace a partial UTF-8 sequence with the Unicode replacement character.
        assertEquals(listOf("a", "\ufffdx", "c"), parse("http://host/a/%E2%98x/c").pathSegments)
    }

    @Test
    fun incompleteUrlComposition() {
        val noHost = assertFailsWith<IllegalStateException> {
            HttpUrlBuilder().scheme("http").build()
        }
        assertEquals("host == null", noHost.message)
        val noScheme = assertFailsWith<IllegalStateException> {
            HttpUrlBuilder().host("host").build()
        }
        assertEquals("scheme == null", noScheme.message)
    }

    @Test
    fun builderToString() {
        assertEquals("https://host.com/path", parse("https://host.com/path").newBuilder().toString())
    }

    @Test
    fun incompleteBuilderToString() {
        assertEquals("https:///path", HttpUrlBuilder().scheme("https").encodedPath("/path").toString())
        assertEquals("//host.com/path", HttpUrlBuilder().host("host.com").encodedPath("/path").toString())
        assertEquals("//host.com:8080/path", HttpUrlBuilder().host("host.com").encodedPath("/path").port(8080).toString())
    }

    @Test
    fun changingSchemeChangesDefaultPort() {
        assertEquals(443, parse("http://example.com").newBuilder().scheme("https").build().port)
        assertEquals(80, parse("https://example.com").newBuilder().scheme("http").build().port)
        assertEquals(1234, parse("https://example.com:1234").newBuilder().scheme("http").build().port)
    }

    @Test
    fun composeWithEncodedPath() {
        val url = HttpUrlBuilder()
            .scheme("http")
            .host("host")
            .encodedPath("/a%2Fb/c")
            .build()
        assertEquals("http://host/a%2Fb/c", url.toString())
        assertEquals("/a%2Fb/c", url.encodedPath)
        assertEquals(listOf("a/b", "c"), url.pathSegments)
    }

    @Test
    fun composeWithAddSegment() {
        val base = parse("http://host/a/b/c")
        assertEquals("/a/b/c/", base.newBuilder().addPathSegment("").build().encodedPath)
        assertEquals("/a/b/c/d", base.newBuilder().addPathSegment("").addPathSegment("d").build().encodedPath)
        assertEquals("/a/b/", base.newBuilder().addPathSegment("..").build().encodedPath)
        assertEquals("/a/b/", base.newBuilder().addPathSegment("").addPathSegment("..").build().encodedPath)
        assertEquals("/a/b/c/", base.newBuilder().addPathSegment("").addPathSegment("").build().encodedPath)
    }

    @Test
    fun queryParametersAreNotDoubleEncoded() {
        val url = HttpUrlBuilder()
            .scheme("http")
            .host("host")
            .addQueryParameter("a", "b c")
            .build()
        assertEquals("http://host/?a=b%20c", url.toString())
    }

    @Test
    fun queryParameterName() {
        val url = parse("http://host/?a=apple&b=banana")
        assertEquals("a", url.queryParameterName(0))
        assertEquals("b", url.queryParameterName(1))
    }

    @Test
    fun queryParameterValue() {
        val url = parse("http://host/?a=apple&b=banana")
        assertEquals("apple", url.queryParameterValue(0))
        assertEquals("banana", url.queryParameterValue(1))
    }

    @Test
    fun queryParameter() {
        val url = parse("http://host/?a=apple&b=banana")
        assertEquals("apple", url.queryParameter("a"))
        assertEquals("banana", url.queryParameter("b"))
        assertNull(url.queryParameter("c"))
    }

    @Test
    fun queryParameterNames() {
        val url = parse("http://host/?a=apple&b=banana")
        assertEquals(setOf("a", "b"), url.queryParameterNames)
    }

    @Test
    fun queryParameterValues() {
        val url = parse("http://host/?a=apple&a=apricot&b=banana")
        assertEquals(listOf("apple", "apricot"), url.queryParameterValues("a"))
        assertEquals(listOf("banana"), url.queryParameterValues("b"))
        assertEquals(emptyList(), url.queryParameterValues("c"))
    }

    @Test
    fun querySize() {
        assertEquals(0, parse("http://host/").querySize)
        assertEquals(1, parse("http://host/?").querySize)
        assertEquals(2, parse("http://host/?a=apple&b=banana").querySize)
    }

    @Test
    fun toHttpUrlOrNullReturnsNullOnInvalidUrl() {
        assertNull("invalid://url".toHttpUrlOrNull())
        assertNull("".toHttpUrlOrNull())
    }

    @Test
    fun toHttpUrlOrNullReturnsUrlOnValidUrl() {
        assertNotNull("http://example.com".toHttpUrlOrNull())
        assertNotNull("https://example.com".toHttpUrlOrNull())
    }

    @Test
    fun schemeProperty() {
        assertEquals("http", parse("http://example.com").scheme)
        assertEquals("https", parse("https://example.com").scheme)
    }

    @Test
    fun isHttpsProperty() {
        assertTrue(parse("https://example.com").isHttps)
        assertTrue(!parse("http://example.com").isHttps)
    }

    @Test
    fun portProperty() {
        assertEquals(80, parse("http://example.com").port)
        assertEquals(443, parse("https://example.com").port)
        assertEquals(8080, parse("http://example.com:8080").port)
    }

    @Test
    fun pathSizeProperty() {
        assertEquals(1, parse("http://host/").pathSize)
        assertEquals(3, parse("http://host/a/b/c").pathSize)
        assertEquals(4, parse("http://host/a/b/c/").pathSize)
    }

    @Test
    fun encodedPathSegmentsProperty() {
        assertEquals(listOf(""), parse("http://host/").encodedPathSegments)
        assertEquals(listOf("a", "b", "c"), parse("http://host/a/b/c").encodedPathSegments)
        assertEquals(listOf("a", "b%20c", "d"), parse("http://host/a/b%20c/d").encodedPathSegments)
    }

    @Test
    fun encodedQueryProperty() {
        assertNull(parse("http://host/").encodedQuery)
        assertEquals("", parse("http://host/?").encodedQuery)
        assertEquals("a=apple&k=key+lime", parse("http://host/?a=apple&k=key+lime").encodedQuery)
    }

    @Test
    fun queryProperty() {
        assertNull(parse("http://host/").query)
        assertEquals("", parse("http://host/?").query)
        assertEquals("a=apple&k=key lime", parse("http://host/?a=apple&k=key+lime").query)
    }

    @Test
    fun fragmentProperty() {
        assertNull(parse("http://host/").fragment)
        assertEquals("", parse("http://host/#").fragment)
        assertEquals("abc", parse("http://host/#abc").fragment)
    }
}
