/*
 * Copyright (C) 2014 Square, Inc.
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class CacheControlTest {
    @Test
    fun emptyBuilderIsEmpty() {
        val cacheControl = CacheControlBuilder().build()
        assertEquals("", cacheControl.toString())
        assertFalse(cacheControl.noCache)
        assertFalse(cacheControl.noStore)
        assertEquals(-1, cacheControl.maxAgeSeconds)
        assertEquals(-1, cacheControl.sMaxAgeSeconds)
        assertFalse(cacheControl.isPrivate)
        assertFalse(cacheControl.isPublic)
        assertFalse(cacheControl.mustRevalidate)
        assertEquals(-1, cacheControl.maxStaleSeconds)
        assertEquals(-1, cacheControl.minFreshSeconds)
        assertFalse(cacheControl.onlyIfCached)
        assertFalse(cacheControl.mustRevalidate)
    }

    @Test
    fun completeBuilder() {
        val cacheControl =
            CacheControlBuilder()
                .noCache()
                .noStore()
                .maxAge(1.seconds)
                .maxStale(2.seconds)
                .minFresh(3.seconds)
                .onlyIfCached()
                .noTransform()
                .immutable()
                .build()
        assertEquals(
            "no-cache, no-store, max-age=1, max-stale=2, min-fresh=3, only-if-cached, no-transform, immutable",
            cacheControl.toString()
        )
        assertTrue(cacheControl.noCache)
        assertTrue(cacheControl.noStore)
        assertEquals(1, cacheControl.maxAgeSeconds)
        assertEquals(2, cacheControl.maxStaleSeconds)
        assertEquals(3, cacheControl.minFreshSeconds)
        assertTrue(cacheControl.onlyIfCached)
        assertTrue(cacheControl.noTransform)
        assertTrue(cacheControl.immutable)

        // These members are accessible to response headers only.
        assertEquals(-1, cacheControl.sMaxAgeSeconds)
        assertFalse(cacheControl.isPrivate)
        assertFalse(cacheControl.isPublic)
        assertFalse(cacheControl.mustRevalidate)
    }

    @Test
    fun parseEmpty() {
        val headers = HeadersBuilder().build()
        val cacheControl = CacheControl.parse(headers)
        assertEquals("", cacheControl.toString())
        assertFalse(cacheControl.noCache)
        assertFalse(cacheControl.noStore)
        assertEquals(-1, cacheControl.maxAgeSeconds)
        assertEquals(-1, cacheControl.sMaxAgeSeconds)
        assertFalse(cacheControl.isPrivate)
        assertFalse(cacheControl.isPublic)
        assertFalse(cacheControl.mustRevalidate)
        assertEquals(-1, cacheControl.maxStaleSeconds)
        assertEquals(-1, cacheControl.minFreshSeconds)
        assertFalse(cacheControl.onlyIfCached)
    }

    @Test
    fun parseCacheControlHeaderWithAllValues() {
        val headers = HeadersBuilder()
            .add("Cache-Control", "no-cache, no-store, max-age=1, s-maxage=2, private, public, must-revalidate, max-stale=3, min-fresh=4, only-if-cached, no-transform, immutable")
            .build()
        val cacheControl = CacheControl.parse(headers)
        assertTrue(cacheControl.noCache)
        assertTrue(cacheControl.noStore)
        assertEquals(1, cacheControl.maxAgeSeconds)
        assertEquals(2, cacheControl.sMaxAgeSeconds)
        assertTrue(cacheControl.isPrivate)
        assertTrue(cacheControl.isPublic)
        assertTrue(cacheControl.mustRevalidate)
        assertEquals(3, cacheControl.maxStaleSeconds)
        assertEquals(4, cacheControl.minFreshSeconds)
        assertTrue(cacheControl.onlyIfCached)
        assertTrue(cacheControl.noTransform)
        assertTrue(cacheControl.immutable)
    }

    @Test
    fun parseCacheControlHeaderWithSomeValues() {
        val headers = HeadersBuilder()
            .add("Cache-Control", "max-age=120, public")
            .build()
        val cacheControl = CacheControl.parse(headers)
        assertFalse(cacheControl.noCache)
        assertFalse(cacheControl.noStore)
        assertEquals(120, cacheControl.maxAgeSeconds)
        assertEquals(-1, cacheControl.sMaxAgeSeconds)
        assertFalse(cacheControl.isPrivate)
        assertTrue(cacheControl.isPublic)
        assertFalse(cacheControl.mustRevalidate)
        assertEquals(-1, cacheControl.maxStaleSeconds)
        assertEquals(-1, cacheControl.minFreshSeconds)
        assertFalse(cacheControl.onlyIfCached)
    }

    @Test
    fun parsePragmaNoCache() {
        val headers = HeadersBuilder()
            .add("Pragma", "no-cache")
            .build()
        // Pragma: no-cache is treated as Cache-Control: no-cache for HTTP/1.0 compatibility
        val cacheControl = CacheControl.parse(headers)
        assertTrue(cacheControl.noCache)
    }

    @Test
    fun parseCacheControlWithQuotedValues() {
        val headers = HeadersBuilder()
            .add("Cache-Control", "max-age=\"120\"")
            .build()
        val cacheControl = CacheControl.parse(headers)
        assertEquals(120, cacheControl.maxAgeSeconds)
    }

    @Test
    fun forceNetwork() {
        assertTrue(CacheControl.FORCE_NETWORK.noCache)
    }

    @Test
    fun forceCache() {
        assertTrue(CacheControl.FORCE_CACHE.onlyIfCached)
        assertEquals(Int.MAX_VALUE, CacheControl.FORCE_CACHE.maxStaleSeconds)
    }
}
