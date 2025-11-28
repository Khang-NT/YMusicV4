package kmphttp

import kmphttp.internal.MAX_DATE
import kmphttp.internal.canParseAsIpAddress
import kmphttp.internal.delimiterOffset
import kmphttp.internal.indexOfControlOrNonAscii
import kmphttp.internal.publicsuffix.PublicSuffixDatabase
import kmphttp.internal.toCanonicalHost
import kmphttp.internal.toHttpDateString
import kmphttp.internal.trimSubstring
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.toInstant
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant


@OptIn(ExperimentalTime::class)
actual class Cookie internal constructor(
    actual val name: String,
    actual val value: String,
    actual val expiresAt: Long,
    actual val domain: String,
    actual val path: String,
    actual val secure: Boolean,
    actual val httpOnly: Boolean,
    actual val persistent: Boolean,
    actual val hostOnly: Boolean,
    actual val sameSite: String?,
) {
    actual fun matches(url: HttpUrl): Boolean {
        val domainMatch =
            if (hostOnly) {
                url.host == domain
            } else {
                domainMatch(url.host, domain)
            }
        if (!domainMatch) return false

        if (!pathMatch(url, path)) return false

        return !secure || url.isHttps
    }

    actual override fun equals(other: Any?): Boolean =
        other is Cookie &&
                other.name == name &&
                other.value == value &&
                other.expiresAt == expiresAt &&
                other.domain == domain &&
                other.path == path &&
                other.secure == secure &&
                other.httpOnly == httpOnly &&
                other.persistent == persistent &&
                other.hostOnly == hostOnly &&
                other.sameSite == sameSite

    actual override fun hashCode(): Int {
        var result = 17
        result = 31 * result + name.hashCode()
        result = 31 * result + value.hashCode()
        result = 31 * result + expiresAt.hashCode()
        result = 31 * result + domain.hashCode()
        result = 31 * result + path.hashCode()
        result = 31 * result + secure.hashCode()
        result = 31 * result + httpOnly.hashCode()
        result = 31 * result + persistent.hashCode()
        result = 31 * result + hostOnly.hashCode()
        result = 31 * result + sameSite.hashCode()
        return result
    }

    actual override fun toString(): String = toString(false)

    /**
     * @param forObsoleteRfc2965 true to include a leading `.` on the domain pattern. This is
     *     necessary for `example.com` to match `www.example.com` under RFC 2965. This extra dot is
     *     ignored by more recent specifications.
     */
    internal fun toString(forObsoleteRfc2965: Boolean): String {
        return buildString {
            append(name)
            append('=')
            append(value)

            if (persistent) {
                if (expiresAt == Long.MIN_VALUE) {
                    append("; max-age=0")
                } else {
                    append("; expires=").append(
                        Instant.fromEpochMilliseconds(expiresAt).toHttpDateString()
                    )
                }
            }

            if (!hostOnly) {
                append("; domain=")
                if (forObsoleteRfc2965) {
                    append(".")
                }
                append(domain)
            }

            append("; path=").append(path)

            if (secure) {
                append("; secure")
            }

            if (httpOnly) {
                append("; httponly")
            }

            if (sameSite != null) {
                append("; samesite=").append(sameSite)
            }

            return toString()
        }
    }

    actual fun newBuilder() = CookieBuilder(this)

    companion object {
        private val YEAR_PATTERN = "(\\d{2,4})[^\\d]*".toRegex()
        private val MONTH_PATTERN =
            "(?i)(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec).*".toRegex()
        private val DAY_OF_MONTH_PATTERN = "(\\d{1,2})[^\\d]*".toRegex()
        private val TIME_PATTERN = "(\\d{1,2}):(\\d{1,2}):(\\d{1,2})[^\\d]*".toRegex()

        private fun domainMatch(
            urlHost: String,
            domain: String,
        ): Boolean {
            if (urlHost == domain) {
                return true // As in 'example.com' matching 'example.com'.
            }

            return urlHost.endsWith(domain) &&
                    urlHost[urlHost.length - domain.length - 1] == '.' &&
                    !urlHost.canParseAsIpAddress()
        }

        private fun pathMatch(
            url: HttpUrl,
            path: String,
        ): Boolean {
            val urlPath = url.encodedPath

            if (urlPath == path) {
                return true // As in '/foo' matching '/foo'.
            }

            if (urlPath.startsWith(path)) {
                if (path.endsWith("/")) return true // As in '/' matching '/foo'.
                if (urlPath[path.length] == '/') return true // As in '/foo' matching '/foo/bar'.
            }

            return false
        }

        /**
         * Attempt to parse a `Set-Cookie` HTTP header value [setCookie] as a cookie. Returns null if
         * [setCookie] is not a well-formed cookie.
         */
        suspend fun parse(
            url: HttpUrl,
            setCookie: String,
        ): Cookie? = parse(Clock.System.now().toEpochMilliseconds(), url, setCookie)

        suspend fun parse(
            currentTimeMillis: Long,
            url: HttpUrl,
            setCookie: String,
        ): Cookie? {
            val cookiePairEnd = setCookie.delimiterOffset(';')

            val pairEqualsSign = setCookie.delimiterOffset('=', endIndex = cookiePairEnd)
            if (pairEqualsSign == cookiePairEnd) return null

            val cookieName = setCookie.trimSubstring(endIndex = pairEqualsSign)
            if (cookieName.isEmpty() || cookieName.indexOfControlOrNonAscii() != -1) return null

            val cookieValue = setCookie.trimSubstring(pairEqualsSign + 1, cookiePairEnd)
            if (cookieValue.indexOfControlOrNonAscii() != -1) return null

            var expiresAt = MAX_DATE
            var deltaSeconds = -1L
            var domain: String? = null
            var path: String? = null
            var secureOnly = false
            var httpOnly = false
            var hostOnly = true
            var persistent = false
            var sameSite: String? = null

            var pos = cookiePairEnd + 1
            val limit = setCookie.length
            while (pos < limit) {
                val attributePairEnd = setCookie.delimiterOffset(';', pos, limit)

                val attributeEqualsSign = setCookie.delimiterOffset('=', pos, attributePairEnd)
                val attributeName = setCookie.trimSubstring(pos, attributeEqualsSign)
                val attributeValue =
                    if (attributeEqualsSign < attributePairEnd) {
                        setCookie.trimSubstring(attributeEqualsSign + 1, attributePairEnd)
                    } else {
                        ""
                    }

                when {
                    attributeName.equals("expires", ignoreCase = true) -> {
                        try {
                            expiresAt = parseExpires(attributeValue, 0, attributeValue.length)
                            persistent = true
                        } catch (_: IllegalArgumentException) {
                            // Ignore this attribute, it isn't recognizable as a date.
                        }
                    }

                    attributeName.equals("max-age", ignoreCase = true) -> {
                        try {
                            deltaSeconds = parseMaxAge(attributeValue)
                            persistent = true
                        } catch (_: NumberFormatException) {
                            // Ignore this attribute, it isn't recognizable as a max age.
                        }
                    }

                    attributeName.equals("domain", ignoreCase = true) -> {
                        try {
                            domain = parseDomain(attributeValue)
                            hostOnly = false
                        } catch (_: IllegalArgumentException) {
                            // Ignore this attribute, it isn't recognizable as a domain.
                        }
                    }

                    attributeName.equals("path", ignoreCase = true) -> {
                        path = attributeValue
                    }

                    attributeName.equals("secure", ignoreCase = true) -> {
                        secureOnly = true
                    }

                    attributeName.equals("httponly", ignoreCase = true) -> {
                        httpOnly = true
                    }

                    attributeName.equals("samesite", ignoreCase = true) -> {
                        sameSite = attributeValue
                    }
                }

                pos = attributePairEnd + 1
            }

            // If 'Max-Age' is present, it takes precedence over 'Expires', regardless of the order the two
            // attributes are declared in the cookie string.
            if (deltaSeconds == Long.MIN_VALUE) {
                expiresAt = Long.MIN_VALUE
            } else if (deltaSeconds != -1L) {
                val deltaMilliseconds =
                    if (deltaSeconds <= Long.MAX_VALUE / 1000) {
                        deltaSeconds * 1000
                    } else {
                        Long.MAX_VALUE
                    }
                expiresAt = currentTimeMillis + deltaMilliseconds
                if (expiresAt < currentTimeMillis || expiresAt > MAX_DATE) {
                    expiresAt = MAX_DATE // Handle overflow & limit the date range.
                }
            }

            // If the domain is present, it must domain match. Otherwise we have a host-only cookie.
            val urlHost = url.host
            if (domain == null) {
                domain = urlHost
            } else if (!domainMatch(urlHost, domain)) {
                return null // No domain match? This is either incompetence or malice!
            }

            // If the domain is a suffix of the url host, it must not be a public suffix.
            if (urlHost.length != domain.length &&
                PublicSuffixDatabase.get().getEffectiveTldPlusOne(domain) == null
            ) {
                return null
            }

            // If the path is absent or didn't start with '/', use the default path. It's a string like
            // '/foo/bar' for a URL like 'http://example.com/foo/bar/baz'. It always starts with '/'.
            if (path == null || !path.startsWith("/")) {
                val encodedPath = url.encodedPath
                val lastSlash = encodedPath.lastIndexOf('/')
                path = if (lastSlash != 0) encodedPath.substring(0, lastSlash) else "/"
            }

            return Cookie(
                cookieName,
                cookieValue,
                expiresAt,
                domain,
                path,
                secureOnly,
                httpOnly,
                persistent,
                hostOnly,
                sameSite,
            )
        }

        private inline fun Regex.find(str: CharSequence, onMatch: (MatchResult) -> Unit): Boolean {
            val match = find(str)
            if (match != null) {
                onMatch(match)
                return true
            }
            return false
        }

        /** Parse a date as specified in RFC 6265, section 5.1.1. */
        private fun parseExpires(
            s: String,
            pos: Int,
            limit: Int,
        ): Long {
            var pos = pos
            pos = dateCharacterOffset(s, pos, limit, false)

            var hour = -1
            var minute = -1
            var second = -1
            var dayOfMonth = -1
            var month = -1
            var year = -1

            while (pos < limit) {
                val end = dateCharacterOffset(s, pos + 1, limit, true)
                val subSeq = s.subSequence(pos, end)
                when {
                    hour == -1 && TIME_PATTERN.find(subSeq) { match ->
                        hour = match.groupValues[1].toInt()
                        minute = match.groupValues[2].toInt()
                        second = match.groupValues[3].toInt()
                    } -> Unit

                    dayOfMonth == -1 && DAY_OF_MONTH_PATTERN.find(subSeq) { match ->
                        dayOfMonth = match.groupValues[1].toInt()
                    } -> Unit

                    month == -1 && MONTH_PATTERN.find(subSeq) { match ->
                        val monthString = match.groupValues[1].lowercase()
                        month = MONTH_PATTERN.toString()
                            .indexOf(monthString) / 4 // Sneaky! jan=1, dec=12. wow
                    } -> Unit

                    year == -1 && YEAR_PATTERN.find(subSeq) { match ->
                        year = match.groupValues[1].toInt()
                    } -> Unit
                }

                pos = dateCharacterOffset(s, end + 1, limit, false)
            }

            // Convert two-digit years into four-digit years. 99 becomes 1999, 15 becomes 2015.
            if (year in 70..99) year += 1900
            if (year in 0..69) year += 2000

            // If any partial is omitted or out of range, return -1. The date is impossible. Note that leap
            // seconds are not supported by this syntax.
            require(year >= 1601)
            require(month != -1)
            require(dayOfMonth in 1..31)
            require(hour in 0..23)
            require(minute in 0..59)
            require(second in 0..59)

            return LocalDateTime(year, month, dayOfMonth, hour, minute, second, 0)
                .toInstant(UtcOffset.ZERO)
                .toEpochMilliseconds()
        }

        /**
         * Returns the index of the next date character in `input`, or if `invert` the index
         * of the next non-date character in `input`.
         */
        private fun dateCharacterOffset(
            input: String,
            pos: Int,
            limit: Int,
            invert: Boolean,
        ): Int {
            for (i in pos until limit) {
                val c = input[i].code
                val dateCharacter = (
                        c < ' '.code &&
                                c != '\t'.code ||
                                c >= '\u007f'.code ||
                                c in '0'.code..'9'.code ||
                                c in 'a'.code..'z'.code ||
                                c in 'A'.code..'Z'.code ||
                                c == ':'.code
                        )
                if (dateCharacter == !invert) return i
            }
            return limit
        }

        /**
         * Returns the positive value if [s] is positive, or [Long.MIN_VALUE] if it is either 0 or
         * negative. If the value is positive but out of range, this returns [Long.MAX_VALUE].
         *
         * @throws NumberFormatException if [s] is not an integer of any precision.
         */
        private fun parseMaxAge(s: String): Long {
            try {
                val parsed = s.toLong()
                return if (parsed <= 0L) Long.MIN_VALUE else parsed
            } catch (e: NumberFormatException) {
                // Check if the value is an integer (positive or negative) that's too big for a long.
                if (s.matches("-?\\d+".toRegex())) {
                    return if (s.startsWith("-")) Long.MIN_VALUE else Long.MAX_VALUE
                }
                throw e
            }
        }

        /**
         * Returns a domain string like `example.com` for an input domain like `EXAMPLE.COM`
         * or `.example.com`.
         */
        private fun parseDomain(s: String): String {
            require(!s.endsWith("."))
            return s.removePrefix(".").toCanonicalHost() ?: throw IllegalArgumentException()
        }

        /** Returns all of the cookies from a set of HTTP response headers. */
        suspend fun parseAll(
            url: HttpUrl,
            headers: Headers,
        ): List<Cookie> {
            val cookieStrings = headers.values("Set-Cookie")
            var cookies: MutableList<Cookie>? = null

            for (i in 0 until cookieStrings.size) {
                val cookie = parse(url, cookieStrings[i]) ?: continue
                if (cookies == null) cookies = mutableListOf()
                cookies.add(cookie)
            }

            return cookies.orEmpty()
        }
    }
}

actual class CookieBuilder actual constructor() {
    private var name: String? = null
    private var value: String? = null
    private var expiresAt = MAX_DATE
    private var domain: String? = null
    private var path = "/"
    private var secure = false
    private var httpOnly = false
    private var persistent = false
    private var hostOnly = false
    private var sameSite: String? = null

    internal constructor(cookie: Cookie) : this() {
        this.name = cookie.name
        this.value = cookie.value
        this.expiresAt = cookie.expiresAt
        this.domain = cookie.domain
        this.path = cookie.path
        this.secure = cookie.secure
        this.httpOnly = cookie.httpOnly
        this.persistent = cookie.persistent
        this.hostOnly = cookie.hostOnly
        this.sameSite = cookie.sameSite
    }

    actual fun name(name: String) =
        apply {
            require(name.trim() == name) { "name is not trimmed" }
            this.name = name
        }

    actual fun value(value: String) =
        apply {
            require(value.trim() == value) { "value is not trimmed" }
            this.value = value
        }

    actual fun expiresAt(expiresAt: Long) =
        apply {
            var expiresAt = expiresAt
            if (expiresAt <= 0L) expiresAt = Long.MIN_VALUE
            if (expiresAt > MAX_DATE) expiresAt = MAX_DATE
            this.expiresAt = expiresAt
            this.persistent = true
        }

    actual fun domain(domain: String) = domain(domain, false)

    actual fun hostOnlyDomain(domain: String) = domain(domain, true)

    private fun domain(
        domain: String,
        hostOnly: Boolean,
    ) = apply {
        val canonicalDomain =
            domain.toCanonicalHost()
                ?: throw IllegalArgumentException("unexpected domain: $domain")
        this.domain = canonicalDomain
        this.hostOnly = hostOnly
    }

    actual fun path(path: String) =
        apply {
            require(path.startsWith("/")) { "path must start with '/'" }
            this.path = path
        }

    actual fun secure() =
        apply {
            this.secure = true
        }

    actual fun httpOnly() =
        apply {
            this.httpOnly = true
        }

    actual fun sameSite(sameSite: String) =
        apply {
            require(sameSite.trim() == sameSite) { "sameSite is not trimmed" }
            this.sameSite = sameSite
        }

    actual fun build(): Cookie =
        Cookie(
            name ?: throw NullPointerException("builder.name == null"),
            value ?: throw NullPointerException("builder.value == null"),
            expiresAt,
            domain ?: throw NullPointerException("builder.domain == null"),
            path,
            secure,
            httpOnly,
            persistent,
            hostOnly,
            sameSite,
        )
}

actual suspend fun parseCookie(
    url: HttpUrl,
    setCookie: String,
): Cookie? = Cookie.parse(url, setCookie)

actual suspend fun parseAllCookies(
    url: HttpUrl,
    headers: Headers,
): List<Cookie> = Cookie.parseAll(url, headers)