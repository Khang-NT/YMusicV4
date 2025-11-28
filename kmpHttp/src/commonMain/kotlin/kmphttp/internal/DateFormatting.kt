package kmphttp.internal

import kotlinx.datetime.UtcOffset
import kotlinx.datetime.format
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.format.DateTimeFormat
import kotlinx.datetime.format.DateTimeFormatBuilder.WithDateTimeComponents
import kotlinx.datetime.format.DayOfWeekNames
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.Padding
import kotlinx.datetime.format.alternativeParsing
import kotlinx.datetime.format.char
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

internal const val MAX_DATE = 253402300799999L

/**
 * Most websites serve cookies in the blessed format. Eagerly create the parser to ensure such
 * cookies are on the fast path.
 */
private val rfc7231Format = DateTimeComponents.Format {
    dayOfWeek(DayOfWeekNames.ENGLISH_ABBREVIATED)
    chars(", ")
    day(Padding.ZERO)
    char(' ')
    monthName(MonthNames.ENGLISH_ABBREVIATED)
    char(' ')
    year()
    char(' ')
    hour()
    char(':')
    minute()
    char(':')
    second()
    chars(" GMT")
}

private fun WithDateTimeComponents.timeWithAlternativeSeparators() {
    hour()
    alternativeParsing({ char('-') }) { char(':') }
    minute()
    alternativeParsing({ char('-') }) { char(':') }
    second()
}

private fun WithDateTimeComponents.dateSeparator() {
    alternativeParsing({ char('-') }, { char('/') }) { char(' ') }
}

private fun WithDateTimeComponents.dayOfWeekFlexible() {
    alternativeParsing({
        dayOfWeek(DayOfWeekNames.ENGLISH_FULL)
    }) {
        dayOfWeek(DayOfWeekNames.ENGLISH_ABBREVIATED)
    }
}

val httpDateFormats: Array<DateTimeFormat<DateTimeComponents>> = arrayOf(
    // RFC 822/1123 with 4-digit year
    DateTimeComponents.Format {
        dayOfWeekFlexible()
        alternativeParsing({ char(',') }, { char(' ') }) { chars(", ")  }
        day(Padding.ZERO)
        dateSeparator()
        alternativeParsing({ monthName(MonthNames.ENGLISH_ABBREVIATED) }) { monthNumber(Padding.ZERO) }
        dateSeparator()
        alternativeParsing({ yearTwoDigits(1900) }) { year() }
        char(' ')
        timeWithAlternativeSeparators()
        char(' ')
        timeZoneId()
    },

    // ANSI C's asctime() format: "EEE MMM d HH:mm:ss yyyy"
    DateTimeComponents.Format {
        dayOfWeekFlexible()
        char(' ')
        monthName(MonthNames.ENGLISH_ABBREVIATED)
        char(' ')
        alternativeParsing({ day(Padding.SPACE) }) { day(Padding.NONE) }
        char(' ')
        timeWithAlternativeSeparators()
        char(' ')
        year()
    },

    // Yahoo format: "EEE MMM d yyyy HH:mm:ss z"
    DateTimeComponents.Format {
        dayOfWeekFlexible()
        char(' ')
        monthName(MonthNames.ENGLISH_ABBREVIATED)
        char(' ')
        alternativeParsing({ day(Padding.SPACE) }) {  day(Padding.NONE) }
        char(' ')
        year()
        char(' ')
        timeWithAlternativeSeparators()
        char(' ')
        timeZoneId()
    },
)

/** Returns the date for this string, or null if the value couldn't be parsed. */
@OptIn(ExperimentalTime::class)
fun String.toHttpDateOrNull(): Instant? {
    if (isEmpty()) return null

    return httpDateFormats.firstNotNullOfOrNull { format ->
        format.parseOrNull(this)?.apply {
            // Handle 2-digit years parsed with base 1900:
            // 1970-1999 are correct (from input 70-99)
            // 1900-1969 need +100 to become 2000-2069 (from input 00-69)
            val y = year
            if (y != null && y in 1900..1969) {
                year = y + 100
            }
        }?.toInstantUsingOffset()
    }
}

/** Returns the string for this date. */
@OptIn(ExperimentalTime::class)
fun Instant.toHttpDateString(): String = format(rfc7231Format, UtcOffset.ZERO)