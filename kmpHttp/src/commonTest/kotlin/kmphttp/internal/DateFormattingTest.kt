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
package kmphttp.internal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.ExperimentalTime

/**
 * Tests for HTTP date parsing and formatting.
 * Inspired by OkHttp's HttpDateTest.
 */
@OptIn(ExperimentalTime::class)
class DateFormattingTest {

    @Test
    fun parseRfc1123Format() {
        // RFC 822, updated by RFC 1123 with GMT
        assertEquals(0L, "Thu, 01 Jan 1970 00:00:00 GMT".toHttpDateOrNull()?.toEpochMilliseconds())
        assertEquals(1402057830000L, "Fri, 06 Jun 2014 12:30:30 GMT".toHttpDateOrNull()?.toEpochMilliseconds())
    }

    @Test
    fun parseRfc850FormatWithTwoDigitYear() {
        // RFC 850 with 2-digit year: 70-99 -> 1970-1999, 0-69 -> 2000-2069
        assertEquals(0L, "Thursday, 01-Jan-70 00:00:00 GMT".toHttpDateOrNull()?.toEpochMilliseconds())  // 1970
        assertEquals(1402057830000L, "Friday, 06-Jun-14 12:30:30 GMT".toHttpDateOrNull()?.toEpochMilliseconds())  // 2014
        assertEquals(946684800000L, "Saturday, 01-Jan-00 00:00:00 GMT".toHttpDateOrNull()?.toEpochMilliseconds())  // 2000
        assertEquals(915148800000L, "Friday, 01-Jan-99 00:00:00 GMT".toHttpDateOrNull()?.toEpochMilliseconds())  // 1999
    }

    @Test
    fun parseRfc850FormatWithFourDigitYear() {
        // RFC 850 with 4-digit year
        assertEquals(0L, "Thursday, 01-Jan-1970 00:00:00 GMT".toHttpDateOrNull()?.toEpochMilliseconds())
        assertEquals(1402057830000L, "Friday, 06-Jun-2014 12:30:30 GMT".toHttpDateOrNull()?.toEpochMilliseconds())
    }

    @Test
    fun parseAscTimeFormat() {
        // ANSI C's asctime() format: should use GMT
        assertEquals(0L, "Thu Jan  1 00:00:00 1970".toHttpDateOrNull()?.toEpochMilliseconds())
        assertEquals(1402057830000L, "Fri Jun  6 12:30:30 2014".toHttpDateOrNull()?.toEpochMilliseconds())
    }

    @Test
    fun parseAscTimeFormatWithoutPaddedDay() {
        // ANSI C's asctime() without space-padded day
        assertEquals(0L, "Thu Jan 1 00:00:00 1970".toHttpDateOrNull()?.toEpochMilliseconds())
        assertEquals(1402057830000L, "Fri Jun 6 12:30:30 2014".toHttpDateOrNull()?.toEpochMilliseconds())
    }

    @Test
    fun formatToRfc1123() {
        assertEquals(
            "Thu, 01 Jan 1970 00:00:00 GMT",
            kotlin.time.Instant.fromEpochMilliseconds(0L).toHttpDateString()
        )
        assertEquals(
            "Fri, 06 Jun 2014 12:30:30 GMT",
            kotlin.time.Instant.fromEpochMilliseconds(1402057830000L).toHttpDateString()
        )
    }

    @Test
    fun parseYahooFormat() {
        // Yahoo format: "EEE MMM d yyyy HH:mm:ss z"
        assertEquals(1402057830000L, "Fri Jun 6 2014 12:30:30 GMT".toHttpDateOrNull()?.toEpochMilliseconds())
        assertEquals(1402057830000L, "Fri Jun  6 2014 12:30:30 GMT".toHttpDateOrNull()?.toEpochMilliseconds())
    }

    @Test
    fun parseEmptyStringReturnsNull() {
        assertNull("".toHttpDateOrNull())
    }

    @Test
    fun parseInvalidStringReturnsNull() {
        assertNull("not a date".toHttpDateOrNull())
        assertNull("2014-06-06".toHttpDateOrNull())
    }

    @Test
    fun roundTrip() {
        val timestamps = listOf(
            0L,
            1402057830000L,
            1234567890000L,
            1703116800000L  // 2023-12-21 00:00:00 UTC
        )

        for (timestamp in timestamps) {
            val instant = kotlin.time.Instant.fromEpochMilliseconds(timestamp)
            val formatted = instant.toHttpDateString()
            val parsed = formatted.toHttpDateOrNull()

            assertNotNull(parsed, "Failed to parse formatted date: $formatted")
            // Note: Round-trip may lose milliseconds since HTTP date format only has second precision
            assertEquals(
                timestamp / 1000 * 1000,  // Truncate to seconds
                parsed.toEpochMilliseconds(),
                "Round-trip failed for timestamp $timestamp"
            )
        }
    }

    @Test
    fun parseVariousValidFormats() {
        // Fri, 06 Jun 2014 12:30:30 GMT = 1402057830000L
        val expectedTimestamp = 1402057830000L

        val validFormats = listOf(
            "Fri, 06 Jun 2014 12:30:30 GMT",           // RFC 1123
            "Friday, 06-Jun-2014 12:30:30 GMT",        // RFC 850 with 4-digit year
        )

        for (format in validFormats) {
            val parsed = format.toHttpDateOrNull()
            assertNotNull(parsed, "Failed to parse: $format")
            assertEquals(expectedTimestamp, parsed.toEpochMilliseconds(), "Wrong timestamp for: $format")
        }
    }

    @Test
    fun parseWithSlashSeparator() {
        // Some servers use slashes instead of dashes or spaces
        assertEquals(0L, "Thu, 01/Jan/1970 00:00:00 GMT".toHttpDateOrNull()?.toEpochMilliseconds())
    }

    @Test
    fun parseWithHyphenTimeSeparator() {
        // Some servers use hyphens in time instead of colons
        assertEquals(0L, "Thu, 01 Jan 1970 00-00-00 GMT".toHttpDateOrNull()?.toEpochMilliseconds())
    }

    @Test
    fun parseWithNumericMonth() {
        // Some servers use numeric month
        assertEquals(0L, "Thu, 01 01 1970 00:00:00 GMT".toHttpDateOrNull()?.toEpochMilliseconds())
    }
}
