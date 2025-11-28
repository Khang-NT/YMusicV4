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

import kmphttp.internal.MAX_DATE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * NonJvm-specific Cookie tests that use internal APIs like timestamp-based parsing
 * and public suffix validation.
 */
class CookieNonJvmTest {
    private val url = "https://example.com/".toHttpUrl()

    @Test
    fun maxAge(): Unit = runBlocking {
        assertEquals(51000L, Cookie.parse(50000L, url, "a=b; Max-Age=1")!!.expiresAt)
        assertEquals(MAX_DATE, Cookie.parse(50000L, url, "a=b; Max-Age=9223372036854724")!!.expiresAt)
        assertEquals(MAX_DATE, Cookie.parse(50000L, url, "a=b; Max-Age=9223372036854725")!!.expiresAt)
        assertEquals(MAX_DATE, Cookie.parse(50000L, url, "a=b; Max-Age=9223372036854726")!!.expiresAt)
        assertEquals(MAX_DATE, Cookie.parse(9223372036854773807L, url, "a=b; Max-Age=1")!!.expiresAt)
        assertEquals(MAX_DATE, Cookie.parse(9223372036854773807L, url, "a=b; Max-Age=2")!!.expiresAt)
        assertEquals(MAX_DATE, Cookie.parse(9223372036854773807L, url, "a=b; Max-Age=3")!!.expiresAt)
        assertEquals(MAX_DATE, Cookie.parse(50000L, url, "a=b; Max-Age=10000000000000000000")!!.expiresAt)
    }

    @Test
    fun maxAgeNonPositive(): Unit = runBlocking {
        assertEquals(Long.MIN_VALUE, Cookie.parse(50000L, url, "a=b; Max-Age=-1")!!.expiresAt)
        assertEquals(Long.MIN_VALUE, Cookie.parse(50000L, url, "a=b; Max-Age=0")!!.expiresAt)
        assertEquals(Long.MIN_VALUE, Cookie.parse(50000L, url, "a=b; Max-Age=-9223372036854775808")!!.expiresAt)
        assertEquals(Long.MIN_VALUE, Cookie.parse(50000L, url, "a=b; Max-Age=-9223372036854775809")!!.expiresAt)
        assertEquals(Long.MIN_VALUE, Cookie.parse(50000L, url, "a=b; Max-Age=-10000000000000000000")!!.expiresAt)
    }

    @Test
    fun maxAgeTakesPrecedenceOverExpires(): Unit = runBlocking {
        // Max-Age = 1, Expires = 2. In either order.
        assertEquals(1000L, Cookie.parse(0L, url, "a=b; Max-Age=1; Expires=Thu, 01 Jan 1970 00:00:02 GMT")!!.expiresAt)
        assertEquals(1000L, Cookie.parse(0L, url, "a=b; Expires=Thu, 01 Jan 1970 00:00:02 GMT; Max-Age=1")!!.expiresAt)
        // Max-Age = 2, Expires = 1. In either order.
        assertEquals(2000L, Cookie.parse(0L, url, "a=b; Max-Age=2; Expires=Thu, 01 Jan 1970 00:00:01 GMT")!!.expiresAt)
        assertEquals(2000L, Cookie.parse(0L, url, "a=b; Expires=Thu, 01 Jan 1970 00:00:01 GMT; Max-Age=2")!!.expiresAt)
    }

    @Test
    fun lastMaxAgeWins(): Unit = runBlocking {
        assertEquals(3000L, Cookie.parse(0L, url, "a=b; Max-Age=2; Max-Age=4; Max-Age=1; Max-Age=3")!!.expiresAt)
    }

    @Test
    fun lastExpiresAtWins(): Unit = runBlocking {
        assertEquals(
            3000L,
            Cookie.parse(
                0L,
                url,
                "a=b; " +
                    "Expires=Thu, 01 Jan 1970 00:00:02 GMT; " +
                    "Expires=Thu, 01 Jan 1970 00:00:04 GMT; " +
                    "Expires=Thu, 01 Jan 1970 00:00:01 GMT; " +
                    "Expires=Thu, 01 Jan 1970 00:00:03 GMT"
            )!!.expiresAt
        )
    }

    @Test
    fun maxAgeOrExpiresMakesCookiePersistent(): Unit = runBlocking {
        assertFalse(Cookie.parse(0L, url, "a=b")!!.persistent)
        assertTrue(Cookie.parse(0L, url, "a=b; Max-Age=1")!!.persistent)
        assertTrue(Cookie.parse(0L, url, "a=b; Expires=Thu, 01 Jan 1970 00:00:01 GMT")!!.persistent)
    }

    @Test
    fun builderClampsMaxDate() {
        val cookie =
            CookieBuilder()
                .name("a")
                .value("b")
                .hostOnlyDomain("example.com")
                .expiresAt(Long.MAX_VALUE)
                .build()
        assertEquals("a=b; expires=Fri, 31 Dec 9999 23:59:59 GMT; path=/", cookie.toString())
    }

    @Test
    fun builderClampsMinDate() {
        val cookie =
            CookieBuilder()
                .name("a")
                .value("b")
                .hostOnlyDomain("example.com")
                .expiresAt(0L)
                .build()
        assertEquals("a=b; max-age=0; path=/", cookie.toString())
    }

    @Test
    fun newBuilder(): Unit = runBlocking {
        val cookie =
            Cookie.parse(0L, url, "c=d; Max-Age=1")!!
                .newBuilder()
                .name("a")
                .value("b")
                .domain("example.com")
                .expiresAt(MAX_DATE)
                .build()
        assertEquals("a", cookie.name)
        assertEquals("b", cookie.value)
        assertEquals(MAX_DATE, cookie.expiresAt)
        assertEquals("example.com", cookie.domain)
        assertEquals("/", cookie.path)
        assertFalse(cookie.secure)
        assertFalse(cookie.httpOnly)
        // can't be unset
        assertTrue(cookie.persistent)
        assertFalse(cookie.hostOnly)
    }

    @Test
    fun equalsAndHashCode(): Unit = runBlocking {
        val cookieStrings = listOf(
            "a=b; Path=/c; Domain=example.com; Max-Age=5; Secure; HttpOnly",
            "a= ; Path=/c; Domain=example.com; Max-Age=5; Secure; HttpOnly",
            "a=b;          Domain=example.com; Max-Age=5; Secure; HttpOnly",
            "a=b; Path=/c;                     Max-Age=5; Secure; HttpOnly",
            "a=b; Path=/c; Domain=example.com;            Secure; HttpOnly",
            "a=b; Path=/c; Domain=example.com; Max-Age=5;         HttpOnly",
            "a=b; Path=/c; Domain=example.com; Max-Age=5; Secure;         ",
            "a=b; Path=/c; Domain=example.com; Max-Age=5; Secure; HttpOnly; SameSite=Lax",
        )
        for (stringA in cookieStrings) {
            val cookieA = Cookie.parse(0, url, stringA)
            for (stringB in cookieStrings) {
                val cookieB = Cookie.parse(0, url, stringB)
                if (stringA == stringB) {
                    assertEquals(cookieA.hashCode(), cookieB.hashCode())
                    assertEquals(cookieA, cookieB)
                } else {
                    assertNotEquals(cookieA.hashCode(), cookieB.hashCode())
                    assertNotEquals(cookieA, cookieB)
                }
            }
            assertNotEquals(cookieA, null)
        }
    }

    // ==================== Public Suffix Tests ====================

    @Test
    fun domainIsPublicSuffix(): Unit = runBlocking {
        assertNull(Cookie.parse(url, "a=b; domain=com"))
        assertNull(Cookie.parse(url, "a=b; domain=net"))
        assertNull(Cookie.parse(url, "a=b; domain=org"))
        assertNotNull(Cookie.parse("https://example.com".toHttpUrl(), "a=b; domain=example.com"))
    }

    @Test
    fun domainIsMultiLevelPublicSuffix(): Unit = runBlocking {
        assertNull(Cookie.parse("https://foo.co.uk".toHttpUrl(), "a=b; domain=co.uk"))
        assertNull(Cookie.parse("https://foo.ac.uk".toHttpUrl(), "a=b; domain=ac.uk"))
        assertNull(Cookie.parse("https://foo.com.cn".toHttpUrl(), "a=b; domain=com.cn"))
        assertNotNull(Cookie.parse("https://example.co.uk".toHttpUrl(), "a=b; domain=example.co.uk"))
    }

    @Test
    fun wildcardPublicSuffix(): Unit = runBlocking {
        assertNull(Cookie.parse("https://user.github.io".toHttpUrl(), "a=b; domain=github.io"))
        val cookie = Cookie.parse("https://user.github.io".toHttpUrl(), "a=b")
        assertNotNull(cookie)
        assertEquals("user.github.io", cookie.domain)
    }

    @Test
    fun publicSuffixExceptionRule(): Unit = runBlocking {
        val cookie = Cookie.parse("https://www.ck".toHttpUrl(), "a=b; domain=www.ck")
        assertNotNull(cookie)
        assertEquals("www.ck", cookie.domain)
        assertNull(Cookie.parse("https://foo.ck".toHttpUrl(), "a=b; domain=ck"))
    }

    @Test
    fun idnPublicSuffix(): Unit = runBlocking {
        assertNull(Cookie.parse("https://example.xn--fiqs8s".toHttpUrl(), "a=b; domain=xn--fiqs8s"))
        assertNotNull(Cookie.parse("https://example.xn--fiqs8s".toHttpUrl(), "a=b; domain=example.xn--fiqs8s"))
    }

    @Test
    fun amazonawsPublicSuffix(): Unit = runBlocking {
        assertNull(Cookie.parse("https://s3.amazonaws.com".toHttpUrl(), "a=b; domain=amazonaws.com"))
        val cookie = Cookie.parse("https://s3.amazonaws.com".toHttpUrl(), "a=b")
        assertNotNull(cookie)
        assertEquals("s3.amazonaws.com", cookie.domain)
    }

    @Test
    fun japanPublicSuffix(): Unit = runBlocking {
        assertNull(Cookie.parse("https://example.jp".toHttpUrl(), "a=b; domain=jp"))
        assertNull(Cookie.parse("https://example.co.jp".toHttpUrl(), "a=b; domain=co.jp"))
        assertNull(Cookie.parse("https://example.ac.jp".toHttpUrl(), "a=b; domain=ac.jp"))
        assertNotNull(Cookie.parse("https://example.co.jp".toHttpUrl(), "a=b; domain=example.co.jp"))
    }
}
