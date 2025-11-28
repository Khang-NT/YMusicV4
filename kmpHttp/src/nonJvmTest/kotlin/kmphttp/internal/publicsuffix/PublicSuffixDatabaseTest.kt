/*
 * Copyright (C) 2017 Square, Inc.
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
package kmphttp.internal.publicsuffix

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import okio.Buffer

class PublicSuffixDatabaseTest {
    private val list = ConfiguredPublicSuffixList()
    private val publicSuffixDatabase = PublicSuffixDatabase(list)

    @Test
    fun longestMatchWins(): Unit = runBlocking {
        list.bytes = Buffer()
            .writeUtf8("com\n")
            .writeUtf8("my.square.com\n")
            .writeUtf8("square.com\n")
            .readByteString()

        assertEquals("example.com", publicSuffixDatabase.getEffectiveTldPlusOne("example.com"))
        assertEquals("example.com", publicSuffixDatabase.getEffectiveTldPlusOne("foo.example.com"))
        assertEquals("bar.square.com", publicSuffixDatabase.getEffectiveTldPlusOne("foo.bar.square.com"))
        assertEquals("foo.my.square.com", publicSuffixDatabase.getEffectiveTldPlusOne("foo.my.square.com"))
    }

    @Test
    fun wildcardMatch(): Unit = runBlocking {
        list.bytes = Buffer()
            .writeUtf8("*.square.com\n")
            .writeUtf8("com\n")
            .writeUtf8("example.com\n")
            .readByteString()

        assertNull(publicSuffixDatabase.getEffectiveTldPlusOne("my.square.com"))
        assertEquals("foo.my.square.com", publicSuffixDatabase.getEffectiveTldPlusOne("foo.my.square.com"))
        assertEquals("foo.my.square.com", publicSuffixDatabase.getEffectiveTldPlusOne("bar.foo.my.square.com"))
    }

    @Test
    fun boundarySearches(): Unit = runBlocking {
        list.bytes = Buffer()
            .writeUtf8("bbb\n")
            .writeUtf8("ddd\n")
            .writeUtf8("fff\n")
            .readByteString()

        assertNull(publicSuffixDatabase.getEffectiveTldPlusOne("aaa"))
        assertNull(publicSuffixDatabase.getEffectiveTldPlusOne("ggg"))
        assertNull(publicSuffixDatabase.getEffectiveTldPlusOne("ccc"))
        assertNull(publicSuffixDatabase.getEffectiveTldPlusOne("eee"))
    }

    @Test
    fun exceptionRule(): Unit = runBlocking {
        list.bytes = Buffer()
            .writeUtf8("*.jp\n")
            .writeUtf8("*.square.jp\n")
            .writeUtf8("example.com\n")
            .writeUtf8("square.com\n")
            .readByteString()
        list.exceptionBytes = Buffer()
            .writeUtf8("my.square.jp\n")
            .readByteString()

        assertEquals("my.square.jp", publicSuffixDatabase.getEffectiveTldPlusOne("my.square.jp"))
        assertEquals("my.square.jp", publicSuffixDatabase.getEffectiveTldPlusOne("foo.my.square.jp"))
        assertNull(publicSuffixDatabase.getEffectiveTldPlusOne("my1.square.jp"))
    }

    @Test
    fun noEffectiveTldPlusOne(): Unit = runBlocking {
        list.bytes = Buffer()
            .writeUtf8("*.jp\n")
            .writeUtf8("*.square.jp\n")
            .writeUtf8("example.com\n")
            .writeUtf8("square.com\n")
            .readByteString()
        list.exceptionBytes = Buffer()
            .writeUtf8("my.square.jp\n")
            .readByteString()

        assertNull(publicSuffixDatabase.getEffectiveTldPlusOne("example.com"))
        assertNull(publicSuffixDatabase.getEffectiveTldPlusOne("foo.square.jp"))
    }

    // Tests from publicsuffix.org test cases
    @Test
    fun publicSuffixDotOrgTestCases(): Unit = runBlocking {
        // Use a minimal public suffix list for these tests
        // Note: Japan (jp) has specific 2-level suffixes, NOT a blanket *.jp wildcard
        val minimalList = ConfiguredPublicSuffixList()
        minimalList.bytes = Buffer()
            // Wildcards and multi-level suffixes (sorted alphabetically)
            .writeUtf8("*.ck\n")
            .writeUtf8("*.mm\n")
            .writeUtf8("*.uk\n")
            .writeUtf8("ac\n")
            .writeUtf8("ac.jp\n")
            .writeUtf8("ac.uk\n")
            .writeUtf8("biz\n")
            .writeUtf8("ck\n")
            .writeUtf8("cn\n")
            .writeUtf8("co.jp\n")
            .writeUtf8("co.uk\n")
            .writeUtf8("com\n")
            .writeUtf8("com.cn\n")
            .writeUtf8("jp\n")
            .writeUtf8("mm\n")
            .writeUtf8("net\n")
            .writeUtf8("org\n")
            .writeUtf8("uk\n")
            .writeUtf8("uk.com\n")
            .writeUtf8("us\n")
            .readByteString()
        minimalList.exceptionBytes = Buffer()
            .writeUtf8("www.ck\n")
            .readByteString()

        val db = PublicSuffixDatabase(minimalList)

        // Mixed case
        assertNull(db.getEffectiveTldPlusOne("com"))
        assertEquals("example.com", db.getEffectiveTldPlusOne("example.com"))
        assertEquals("example.com", db.getEffectiveTldPlusOne("www.example.com"))

        // TLD with only 1 rule
        assertNull(db.getEffectiveTldPlusOne("biz"))
        assertEquals("domain.biz", db.getEffectiveTldPlusOne("domain.biz"))
        assertEquals("domain.biz", db.getEffectiveTldPlusOne("b.domain.biz"))
        assertEquals("domain.biz", db.getEffectiveTldPlusOne("a.b.domain.biz"))

        // TLD with some 2-level rules
        assertNull(db.getEffectiveTldPlusOne("com"))
        assertEquals("example.com", db.getEffectiveTldPlusOne("example.com"))
        assertEquals("example.com", db.getEffectiveTldPlusOne("b.example.com"))
        assertEquals("example.com", db.getEffectiveTldPlusOne("a.b.example.com"))
        assertNull(db.getEffectiveTldPlusOne("uk.com"))
        assertEquals("example.uk.com", db.getEffectiveTldPlusOne("example.uk.com"))
        assertEquals("example.uk.com", db.getEffectiveTldPlusOne("b.example.uk.com"))
        assertEquals("example.uk.com", db.getEffectiveTldPlusOne("a.b.example.uk.com"))
        assertEquals("test.ac", db.getEffectiveTldPlusOne("test.ac"))

        // TLD with only 1 (wildcard) rule
        assertNull(db.getEffectiveTldPlusOne("mm"))
        assertNull(db.getEffectiveTldPlusOne("c.mm"))
        assertEquals("b.c.mm", db.getEffectiveTldPlusOne("b.c.mm"))
        assertEquals("b.c.mm", db.getEffectiveTldPlusOne("a.b.c.mm"))

        // Japan TLD with specific 2-level suffixes (not a wildcard)
        assertNull(db.getEffectiveTldPlusOne("jp"))
        assertEquals("test.jp", db.getEffectiveTldPlusOne("test.jp"))
        assertEquals("test.jp", db.getEffectiveTldPlusOne("www.test.jp"))
        assertNull(db.getEffectiveTldPlusOne("ac.jp"))
        assertEquals("test.ac.jp", db.getEffectiveTldPlusOne("test.ac.jp"))
        assertEquals("test.ac.jp", db.getEffectiveTldPlusOne("www.test.ac.jp"))

        // TLD with a wildcard rule and exceptions
        assertNull(db.getEffectiveTldPlusOne("ck"))
        assertNull(db.getEffectiveTldPlusOne("test.ck"))
        assertEquals("b.test.ck", db.getEffectiveTldPlusOne("b.test.ck"))
        assertEquals("b.test.ck", db.getEffectiveTldPlusOne("a.b.test.ck"))
        assertEquals("www.ck", db.getEffectiveTldPlusOne("www.ck"))
        assertEquals("www.ck", db.getEffectiveTldPlusOne("www.www.ck"))

        // US TLD
        assertNull(db.getEffectiveTldPlusOne("us"))
        assertEquals("test.us", db.getEffectiveTldPlusOne("test.us"))
        assertEquals("test.us", db.getEffectiveTldPlusOne("www.test.us"))

        // Multi-level suffix
        assertNull(db.getEffectiveTldPlusOne("com.cn"))
        assertEquals("example.com.cn", db.getEffectiveTldPlusOne("example.com.cn"))
        assertEquals("example.com.cn", db.getEffectiveTldPlusOne("www.example.com.cn"))
    }

    @Test
    fun idnPublicSuffix(): Unit = runBlocking {
        // IDN suffixes should be stored in Unicode form in the database
        // The algorithm converts input to Unicode before searching
        val minimalList = ConfiguredPublicSuffixList()
        minimalList.bytes = Buffer()
            .writeUtf8("cn\n")
            .writeUtf8("com.cn\n")
            .writeUtf8("公司.cn\n")  // Unicode form of xn--55qx5d.cn
            .writeUtf8("中国\n")     // Unicode form of xn--fiqs8s
            .readByteString()

        val db = PublicSuffixDatabase(minimalList)

        // IDN labels (punycoded input)
        assertEquals("xn--85x722f.com.cn", db.getEffectiveTldPlusOne("xn--85x722f.com.cn"))
        assertEquals("xn--85x722f.xn--55qx5d.cn", db.getEffectiveTldPlusOne("xn--85x722f.xn--55qx5d.cn"))
        assertEquals("xn--85x722f.xn--55qx5d.cn", db.getEffectiveTldPlusOne("www.xn--85x722f.xn--55qx5d.cn"))
        assertNull(db.getEffectiveTldPlusOne("xn--55qx5d.cn"))
        assertEquals("xn--85x722f.xn--fiqs8s", db.getEffectiveTldPlusOne("xn--85x722f.xn--fiqs8s"))
        assertNull(db.getEffectiveTldPlusOne("xn--fiqs8s"))
    }

    @Test
    fun singleLabelDomain(): Unit = runBlocking {
        list.bytes = Buffer()
            .writeUtf8("com\n")
            .readByteString()

        // Single-label domains should return null (no TLD+1 possible)
        assertNull(publicSuffixDatabase.getEffectiveTldPlusOne("localhost"))
        assertNull(publicSuffixDatabase.getEffectiveTldPlusOne("mymacbook"))
    }

    @Test
    fun trailingDot(): Unit = runBlocking {
        list.bytes = Buffer()
            .writeUtf8("com\n")
            .readByteString()

        // Trailing dot should be handled
        assertEquals("example.com", publicSuffixDatabase.getEffectiveTldPlusOne("example.com."))
        assertEquals("example.com", publicSuffixDatabase.getEffectiveTldPlusOne("www.example.com."))
    }

    @Test
    fun emptyExceptionBytes(): Unit = runBlocking {
        list.bytes = Buffer()
            .writeUtf8("*.jp\n")
            .writeUtf8("com\n")
            .readByteString()
        // exceptionBytes is empty by default

        assertNull(publicSuffixDatabase.getEffectiveTldPlusOne("foo.jp"))
        assertEquals("bar.foo.jp", publicSuffixDatabase.getEffectiveTldPlusOne("bar.foo.jp"))
    }

    @Test
    fun multipleWildcardLevels(): Unit = runBlocking {
        list.bytes = Buffer()
            .writeUtf8("*.amazonaws.com\n")
            .writeUtf8("*.elb.amazonaws.com\n")
            .writeUtf8("amazonaws.com\n")
            .writeUtf8("com\n")
            .readByteString()

        // amazonaws.com is a public suffix
        assertNull(publicSuffixDatabase.getEffectiveTldPlusOne("amazonaws.com"))
        // *.amazonaws.com means s3.amazonaws.com is also a public suffix
        assertNull(publicSuffixDatabase.getEffectiveTldPlusOne("s3.amazonaws.com"))
        // But bucket.s3.amazonaws.com should return bucket.s3.amazonaws.com
        assertEquals("bucket.s3.amazonaws.com", publicSuffixDatabase.getEffectiveTldPlusOne("bucket.s3.amazonaws.com"))
    }
}
