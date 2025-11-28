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
package kmphttp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class CookieTest {
    private val url = "https://example.com/".toHttpUrl()

    @Test
    fun simpleCookie(): Unit = runBlocking {
        val cookie = parseCookie(url, "SID=31d4d96e407aad42")
        assertEquals("SID=31d4d96e407aad42; path=/", cookie.toString())
    }

    @Test
    fun noEqualsSign(): Unit = runBlocking {
        assertNull(parseCookie(url, "foo"))
        assertNull(parseCookie(url, "foo; Path=/"))
    }

    @Test
    fun emptyName(): Unit = runBlocking {
        assertNull(parseCookie(url, "=b"))
        assertNull(parseCookie(url, " =b"))
        assertNull(parseCookie(url, "\r\t \n=b"))
    }

    @Test
    fun spaceInName(): Unit = runBlocking {
        assertEquals("a b", parseCookie(url, "a b=cd")!!.name)
    }

    @Test
    fun spaceInValue(): Unit = runBlocking {
        assertEquals("c d", parseCookie(url, "ab=c d")!!.value)
    }

    @Test
    fun trimLeadingAndTrailingWhitespaceFromName(): Unit = runBlocking {
        assertEquals("a", parseCookie(url, " a=b")!!.name)
        assertEquals("a", parseCookie(url, "a =b")!!.name)
        assertEquals("a", parseCookie(url, "\r\t \na\n\t \n=b")!!.name)
    }

    @Test
    fun emptyValue(): Unit = runBlocking {
        assertEquals("", parseCookie(url, "a=")!!.value)
        assertEquals("", parseCookie(url, "a= ")!!.value)
        assertEquals("", parseCookie(url, "a=\r\t \n")!!.value)
    }

    @Test
    fun trimLeadingAndTrailingWhitespaceFromValue(): Unit = runBlocking {
        assertEquals("", parseCookie(url, "a= ")!!.value)
        assertEquals("b", parseCookie(url, "a= b")!!.value)
        assertEquals("b", parseCookie(url, "a=b ")!!.value)
        assertEquals("b", parseCookie(url, "a=\r\t \nb\n\t \n")!!.value)
    }

    @Test
    fun invalidCharacters(): Unit = runBlocking {
        assertNull(parseCookie(url, "a\u0000b=cd"))
        assertNull(parseCookie(url, "ab=c\u0000d"))
        assertNull(parseCookie(url, "a\u0001b=cd"))
        assertNull(parseCookie(url, "ab=c\u0001d"))
        assertNull(parseCookie(url, "a\u0009b=cd"))
        assertNull(parseCookie(url, "ab=c\u0009d"))
        assertNull(parseCookie(url, "a\u001fb=cd"))
        assertNull(parseCookie(url, "ab=c\u001fd"))
        assertNull(parseCookie(url, "a\u007fb=cd"))
        assertNull(parseCookie(url, "ab=c\u007fd"))
        assertNull(parseCookie(url, "a\u0080b=cd"))
        assertNull(parseCookie(url, "ab=c\u0080d"))
        assertNull(parseCookie(url, "a\u00ffb=cd"))
        assertNull(parseCookie(url, "ab=c\u00ffd"))
    }

    @Test
    fun domainAndPath(): Unit = runBlocking {
        val cookie = parseCookie(url, "SID=31d4d96e407aad42; Path=/; Domain=example.com")
        assertEquals("example.com", cookie!!.domain)
        assertEquals("/", cookie.path)
        assertFalse(cookie.hostOnly)
        assertEquals("SID=31d4d96e407aad42; domain=example.com; path=/", cookie.toString())
    }

    @Test
    fun secureAndHttpOnly(): Unit = runBlocking {
        val cookie = parseCookie(url, "SID=31d4d96e407aad42; Path=/; Secure; HttpOnly")
        assertTrue(cookie!!.secure)
        assertTrue(cookie.httpOnly)
        assertEquals("SID=31d4d96e407aad42; path=/; secure; httponly", cookie.toString())
    }

    @Test
    fun domainMatches(): Unit = runBlocking {
        val cookie = parseCookie(url, "a=b; domain=example.com")
        assertTrue(cookie!!.matches("http://example.com".toHttpUrl()))
        assertTrue(cookie.matches("http://www.example.com".toHttpUrl()))
        assertFalse(cookie.matches("http://square.com".toHttpUrl()))
    }

    @Test
    fun domainMatchesNoDomain(): Unit = runBlocking {
        val cookie = parseCookie(url, "a=b")
        assertTrue(cookie!!.matches("http://example.com".toHttpUrl()))
        assertFalse(cookie.matches("http://www.example.com".toHttpUrl()))
        assertFalse(cookie.matches("http://square.com".toHttpUrl()))
    }

    @Test
    fun domainMatchesIgnoresLeadingDot(): Unit = runBlocking {
        val cookie = parseCookie(url, "a=b; domain=.example.com")
        assertTrue(cookie!!.matches("http://example.com".toHttpUrl()))
        assertTrue(cookie.matches("http://www.example.com".toHttpUrl()))
        assertFalse(cookie.matches("http://square.com".toHttpUrl()))
    }

    @Test
    fun domainIgnoredWithTrailingDot(): Unit = runBlocking {
        val cookie = parseCookie(url, "a=b; domain=example.com.")
        assertTrue(cookie!!.matches("http://example.com".toHttpUrl()))
        assertFalse(cookie.matches("http://www.example.com".toHttpUrl()))
        assertFalse(cookie.matches("http://square.com".toHttpUrl()))
    }

    @Test
    fun hostOnly(): Unit = runBlocking {
        assertTrue(parseCookie(url, "a=b")!!.hostOnly)
        assertFalse(parseCookie(url, "a=b; domain=example.com")!!.hostOnly)
    }

    @Test
    fun defaultPath(): Unit = runBlocking {
        assertEquals("/foo", parseCookie("http://example.com/foo/bar".toHttpUrl(), "a=b")!!.path)
        assertEquals("/foo", parseCookie("http://example.com/foo/".toHttpUrl(), "a=b")!!.path)
        assertEquals("/", parseCookie("http://example.com/foo".toHttpUrl(), "a=b")!!.path)
        assertEquals("/", parseCookie("http://example.com/".toHttpUrl(), "a=b")!!.path)
    }

    @Test
    fun defaultPathIsUsedIfPathDoesntHaveLeadingSlash(): Unit = runBlocking {
        assertEquals("/foo", parseCookie("http://example.com/foo/bar".toHttpUrl(), "a=b; path=quux")!!.path)
        assertEquals("/foo", parseCookie("http://example.com/foo/bar".toHttpUrl(), "a=b; path=")!!.path)
    }

    @Test
    fun pathAttributeDoesntNeedToMatch(): Unit = runBlocking {
        assertEquals("/quux", parseCookie("http://example.com/".toHttpUrl(), "a=b; path=/quux")!!.path)
        assertEquals("/quux", parseCookie("http://example.com/foo/bar".toHttpUrl(), "a=b; path=/quux")!!.path)
    }

    @Test
    fun httpOnly(): Unit = runBlocking {
        assertFalse(parseCookie(url, "a=b")!!.httpOnly)
        assertTrue(parseCookie(url, "a=b; HttpOnly")!!.httpOnly)
    }

    @Test
    fun secure(): Unit = runBlocking {
        assertFalse(parseCookie(url, "a=b")!!.secure)
        assertTrue(parseCookie(url, "a=b; Secure")!!.secure)
    }

    @Test
    fun parseAll(): Unit = runBlocking {
        val headers =
            HeadersBuilder()
                .add("Set-Cookie: a=b")
                .add("Set-Cookie: c=d")
                .build()
        val cookies = parseAllCookies(url, headers)
        assertEquals(2, cookies.size)
        assertEquals("a=b; path=/", cookies[0].toString())
        assertEquals("c=d; path=/", cookies[1].toString())
    }

    @Test
    fun builder() {
        val cookie =
            CookieBuilder()
                .name("a")
                .value("b")
                .domain("example.com")
                .build()
        assertEquals("a", cookie.name)
        assertEquals("b", cookie.value)
        assertEquals("example.com", cookie.domain)
        assertEquals("/", cookie.path)
        assertFalse(cookie.secure)
        assertFalse(cookie.httpOnly)
        assertFalse(cookie.hostOnly)
        assertNull(cookie.sameSite)
    }

    @Test
    fun builderNameValidation() {
        assertFailsWith<IllegalArgumentException> {
            CookieBuilder().name(" a ")
        }
    }

    @Test
    fun builderValueValidation() {
        assertFailsWith<IllegalArgumentException> {
            CookieBuilder().value(" b ")
        }
    }

    @Test
    fun builderDomainValidation() {
        assertFailsWith<IllegalArgumentException> {
            CookieBuilder().hostOnlyDomain("a/b")
        }
    }

    @Test
    fun builderDomain() {
        val cookie =
            CookieBuilder()
                .name("a")
                .value("b")
                .hostOnlyDomain("squareup.com")
                .build()
        assertEquals("squareup.com", cookie.domain)
        assertTrue(cookie.hostOnly)
    }

    @Test
    fun builderPath() {
        val cookie =
            CookieBuilder()
                .name("a")
                .value("b")
                .hostOnlyDomain("example.com")
                .path("/foo")
                .build()
        assertEquals("/foo", cookie.path)
    }

    @Test
    fun builderPathValidation() {
        assertFailsWith<IllegalArgumentException> {
            CookieBuilder().path("foo")
        }
    }

    @Test
    fun builderSecure() {
        val cookie =
            CookieBuilder()
                .name("a")
                .value("b")
                .hostOnlyDomain("example.com")
                .secure()
                .build()
        assertTrue(cookie.secure)
    }

    @Test
    fun builderHttpOnly() {
        val cookie =
            CookieBuilder()
                .name("a")
                .value("b")
                .hostOnlyDomain("example.com")
                .httpOnly()
                .build()
        assertTrue(cookie.httpOnly)
    }

    @Test
    fun builderIpv6() {
        val cookie =
            CookieBuilder()
                .name("a")
                .value("b")
                .domain("0:0:0:0:0:0:0:1")
                .build()
        assertEquals("::1", cookie.domain)
    }

    @Test
    fun emptySameSite(): Unit = runBlocking {
        assertEquals("", parseCookie(url, "a=b; SameSite=")!!.sameSite)
        assertEquals("", parseCookie(url, "a=b; SameSite= ")!!.sameSite)
        assertEquals("", parseCookie(url, "a=b; SameSite=\r\t \n")!!.sameSite)
    }

    @Test
    fun spaceInSameSite(): Unit = runBlocking {
        assertEquals("a b", parseCookie(url, "a=b; SameSite=a b")!!.sameSite)
    }

    @Test
    fun trimLeadingAndTrailingWhitespaceFromSameSite(): Unit = runBlocking {
        assertEquals("", parseCookie(url, "a=b; SameSite= ")!!.sameSite)
        assertEquals("Lax", parseCookie(url, "a= b; SameSite= Lax")!!.sameSite)
        assertEquals("Lax", parseCookie(url, "a=b ; SameSite=Lax ;")!!.sameSite)
        assertEquals("Lax", parseCookie(url, "a=\r\t \nb\n; \rSameSite=\n \tLax")!!.sameSite)
    }

    @Test
    fun builderSameSiteTrimmed() {
        val cookieBuilder =
            CookieBuilder()
                .name("a")
                .value("b")
                .domain("example.com")

        assertFailsWith<IllegalArgumentException> {
            cookieBuilder.sameSite(" a").build()
        }
        assertFailsWith<IllegalArgumentException> {
            cookieBuilder.sameSite("a ").build()
        }
        assertFailsWith<IllegalArgumentException> {
            cookieBuilder.sameSite(" a ").build()
        }

        // This should succeed
        cookieBuilder.sameSite("a").build()
    }

    @Test
    fun builderSameSiteLax() {
        val cookie =
            CookieBuilder()
                .name("a")
                .value("b")
                .domain("example.com")
                .sameSite("Lax")
                .build()
        assertEquals("Lax", cookie.sameSite)
    }

    @Test
    fun builderSameSiteStrict() {
        val cookie =
            CookieBuilder()
                .name("a")
                .value("b")
                .domain("example.com")
                .sameSite("Strict")
                .build()
        assertEquals("Strict", cookie.sameSite)
    }

    @Test
    fun builderSameSiteNoneDoesNotRequireSecure() {
        val cookie =
            CookieBuilder()
                .name("a")
                .value("b")
                .domain("example.com")
                .sameSite("None")
                .build()
        assertEquals("None", cookie.sameSite)
    }
}
