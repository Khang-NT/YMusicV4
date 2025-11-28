/*
 * Copyright (C) 2012 Square, Inc.
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
import kotlin.test.assertNotEquals

class HeadersTest {
    @Test
    fun addWithColonDelimiter() {
        val headers = HeadersBuilder().add("User-Agent: OkHttp").build()
        assertEquals("User-Agent", headers.name(0))
        assertEquals("OkHttp", headers.value(0))
    }

    @Test
    fun addWithNameAndValue() {
        val headers = HeadersBuilder().add("User-Agent", "OkHttp").build()
        assertEquals("User-Agent", headers.name(0))
        assertEquals("OkHttp", headers.value(0))
    }

    @Test
    fun addThrowsOnEmptyName() {
        assertFailsWith<IllegalArgumentException> {
            HeadersBuilder().add("", "OkHttp")
        }
    }

    @Test
    fun addAcceptsEmptyValue() {
        val headers = HeadersBuilder().add("User-Agent", "").build()
        assertEquals("", headers.value(0))
    }

    @Test
    fun builderRejectsNullChar() {
        assertFailsWith<IllegalArgumentException> {
            HeadersBuilder().add("User-Agent", "Square\u0000OkHttp")
        }
    }

    @Test
    fun builderRejectsNonAsciiInName() {
        assertFailsWith<IllegalArgumentException> {
            HeadersBuilder().add("héader1", "value1")
        }
    }

    @Test
    fun builderRejectsNonAsciiInValue() {
        assertFailsWith<IllegalArgumentException> {
            HeadersBuilder().add("header1", "valué1")
        }
    }

    @Test
    fun headersEquals() {
        val headers1 =
            HeadersBuilder()
                .add("Connection", "close")
                .add("Transfer-Encoding", "chunked")
                .build()
        val headers2 =
            HeadersBuilder()
                .add("Connection", "close")
                .add("Transfer-Encoding", "chunked")
                .build()
        assertEquals(headers1, headers2)
        assertEquals(headers1.hashCode(), headers2.hashCode())
    }

    @Test
    fun headersNotEquals() {
        val headers1 =
            HeadersBuilder()
                .add("Connection", "close")
                .add("Transfer-Encoding", "chunked")
                .build()
        val headers2 =
            HeadersBuilder()
                .add("Connection", "keep-alive")
                .add("Transfer-Encoding", "chunked")
                .build()
        assertNotEquals(headers1, headers2)
        assertNotEquals(headers1.hashCode(), headers2.hashCode())
    }

    @Test
    fun headersToString() {
        val headers =
            HeadersBuilder()
                .add("A", "a")
                .add("B", "bb")
                .build()
        assertEquals("A: a\nB: bb\n", headers.toString())
    }

    @Test
    fun headersAddAll() {
        val sourceHeaders =
            HeadersBuilder()
                .add("A", "aa")
                .add("a", "aa")
                .add("B", "bb")
                .build()
        val headers =
            HeadersBuilder()
                .add("A", "a")
                .addAll(sourceHeaders)
                .add("C", "c")
                .build()
        assertEquals("A: a\nA: aa\na: aa\nB: bb\nC: c\n", headers.toString())
    }

    @Test
    fun nameIndexesAreStrict() {
        val headers = HeadersBuilder().add("a", "b").add("c", "d").build()
        assertFailsWith<IndexOutOfBoundsException> {
            headers.name(-1)
        }
        assertEquals("a", headers.name(0))
        assertEquals("c", headers.name(1))
        assertFailsWith<IndexOutOfBoundsException> {
            headers.name(2)
        }
    }

    @Test
    fun valueIndexesAreStrict() {
        val headers = HeadersBuilder().add("a", "b").add("c", "d").build()
        assertFailsWith<IndexOutOfBoundsException> {
            headers.value(-1)
        }
        assertEquals("b", headers.value(0))
        assertEquals("d", headers.value(1))
        assertFailsWith<IndexOutOfBoundsException> {
            headers.value(2)
        }
    }

    @Test
    fun getByName() {
        val headers = HeadersBuilder()
            .add("Content-Type", "text/html")
            .add("Cache-Control", "no-cache")
            .add("Cache-Control", "max-age=60")
            .build()
        // get() returns the last value
        assertEquals("max-age=60", headers["Cache-Control"])
        assertEquals("text/html", headers["Content-Type"])
        assertEquals(null, headers["Not-Present"])
    }

    @Test
    fun getByNameCaseInsensitive() {
        val headers = HeadersBuilder()
            .add("Content-Type", "text/html")
            .build()
        assertEquals("text/html", headers["content-type"])
        assertEquals("text/html", headers["CONTENT-TYPE"])
    }

    @Test
    fun valuesByName() {
        val headers = HeadersBuilder()
            .add("Cache-Control", "no-cache")
            .add("Cache-Control", "max-age=60")
            .build()
        val values = headers.values("Cache-Control")
        assertEquals(2, values.size)
        assertEquals("no-cache", values[0])
        assertEquals("max-age=60", values[1])
    }

    @Test
    fun size() {
        val headers = HeadersBuilder()
            .add("A", "a")
            .add("B", "b")
            .add("C", "c")
            .build()
        assertEquals(3, headers.size)
    }

    @Test
    fun removeAll() {
        val headers = HeadersBuilder()
            .add("A", "a1")
            .add("B", "b")
            .add("A", "a2")
            .removeAll("A")
            .build()
        assertEquals(1, headers.size)
        assertEquals("B", headers.name(0))
    }

    @Test
    fun set() {
        val headers = HeadersBuilder()
            .add("A", "a1")
            .add("A", "a2")
            .set("A", "a3")
            .build()
        assertEquals(1, headers.size)
        assertEquals("a3", headers["A"])
    }

    @Test
    fun iteratorWorks() {
        val headers = HeadersBuilder()
            .add("A", "a")
            .add("B", "b")
            .build()
        val pairs = headers.toList()
        assertEquals(2, pairs.size)
        assertEquals("A" to "a", pairs[0])
        assertEquals("B" to "b", pairs[1])
    }

    @Test
    fun newBuilder() {
        val headers = HeadersBuilder()
            .add("A", "a")
            .add("B", "b")
            .build()
        val newHeaders = headers.newBuilder()
            .add("C", "c")
            .build()
        assertEquals(3, newHeaders.size)
        assertEquals("a", newHeaders["A"])
        assertEquals("b", newHeaders["B"])
        assertEquals("c", newHeaders["C"])
        // Original is unchanged
        assertEquals(2, headers.size)
    }

    @Test
    fun emptyHeaders() {
        assertEquals(0, Headers.EMPTY.size)
    }
}
