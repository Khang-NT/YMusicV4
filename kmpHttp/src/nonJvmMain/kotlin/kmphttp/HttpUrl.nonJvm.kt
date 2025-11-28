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

import kmphttp.HttpUrl.Companion.defaultPort
import kmphttp.internal.canParseAsIpAddress
import kmphttp.internal.delimiterOffset
import kmphttp.internal.indexOfFirstNonAsciiWhitespace
import kmphttp.internal.indexOfLastNonAsciiWhitespace
import kmphttp.internal.publicsuffix.PublicSuffixDatabase
import kmphttp.internal.toCanonicalHost
import kmphttp.internal.url.FRAGMENT_ENCODE_SET
import kmphttp.internal.url.FRAGMENT_ENCODE_SET_URI
import kmphttp.internal.url.PASSWORD_ENCODE_SET
import kmphttp.internal.url.PATH_SEGMENT_ENCODE_SET
import kmphttp.internal.url.PATH_SEGMENT_ENCODE_SET_URI
import kmphttp.internal.url.QUERY_COMPONENT_ENCODE_SET
import kmphttp.internal.url.QUERY_COMPONENT_ENCODE_SET_URI
import kmphttp.internal.url.QUERY_COMPONENT_REENCODE_SET
import kmphttp.internal.url.QUERY_ENCODE_SET
import kmphttp.internal.url.USERNAME_ENCODE_SET
import kmphttp.internal.url.canonicalize
import kmphttp.internal.url.percentDecode

actual class HttpUrl internal constructor(
    actual val scheme: String,
    actual val username: String,
    actual val password: String,
    actual val host: String,
    actual val port: Int,
    actual val pathSegments: List<String>,
    /**
     * Alternating, decoded query names and values, or null for no query. Names may be empty or
     * non-empty, but never null. Values are null if the name has no corresponding '=' separator, or
     * empty, or non-empty.
     */
    private val queryNamesAndValues: List<String?>?,
    actual val fragment: String?,
    /** Canonical URL. */
    private val url: String,
) {
    actual val isHttps: Boolean
        get() = scheme == "https"

    actual val encodedUsername: String
        get() {
            if (username.isEmpty()) return ""
            val usernameStart = scheme.length + 3 // "://".length() == 3.
            val usernameEnd = url.delimiterOffset(":@", usernameStart, url.length)
            return url.substring(usernameStart, usernameEnd)
        }

    actual val encodedPassword: String
        get() {
            if (password.isEmpty()) return ""
            val passwordStart = url.indexOf(':', scheme.length + 3) + 1
            val passwordEnd = url.indexOf('@')
            return url.substring(passwordStart, passwordEnd)
        }

    actual val pathSize: Int
        get() = pathSegments.size

    actual val encodedPath: String
        get() {
            val pathStart = url.indexOf('/', scheme.length + 3) // "://".length() == 3.
            val pathEnd = url.delimiterOffset("?#", pathStart, url.length)
            return url.substring(pathStart, pathEnd)
        }

    actual val encodedPathSegments: List<String>
        get() {
            val pathStart = url.indexOf('/', scheme.length + 3)
            val pathEnd = url.delimiterOffset("?#", pathStart, url.length)
            val result = mutableListOf<String>()
            var i = pathStart
            while (i < pathEnd) {
                i++ // Skip the '/'.
                val segmentEnd = url.delimiterOffset('/', i, pathEnd)
                result.add(url.substring(i, segmentEnd))
                i = segmentEnd
            }
            return result
        }

    actual val encodedQuery: String?
        get() {
            if (queryNamesAndValues == null) return null // No query.
            val queryStart = url.indexOf('?') + 1
            val queryEnd = url.delimiterOffset('#', queryStart, url.length)
            return url.substring(queryStart, queryEnd)
        }

    actual val query: String?
        get() {
            if (queryNamesAndValues == null) return null // No query.
            val result = StringBuilder()
            queryNamesAndValues.toQueryString(result)
            return result.toString()
        }

    actual val querySize: Int
        get() {
            return if (queryNamesAndValues != null) queryNamesAndValues.size / 2 else 0
        }

    actual fun queryParameter(name: String): String? {
        if (queryNamesAndValues == null) return null
        for (i in 0 until queryNamesAndValues.size step 2) {
            if (name == queryNamesAndValues[i]) {
                return queryNamesAndValues[i + 1]
            }
        }
        return null
    }

    actual val queryParameterNames: Set<String>
        get() {
            if (queryNamesAndValues == null) return emptySet()
            val result = LinkedHashSet<String>(queryNamesAndValues.size / 2, 1.0F)
            for (i in 0 until queryNamesAndValues.size step 2) {
                result.add(queryNamesAndValues[i]!!)
            }
            return result
        }

    actual fun queryParameterValues(name: String): List<String?> {
        if (queryNamesAndValues == null) return emptyList()
        val result = ArrayList<String?>(4)
        for (i in 0 until queryNamesAndValues.size step 2) {
            if (name == queryNamesAndValues[i]) {
                result.add(queryNamesAndValues[i + 1])
            }
        }
        return result
    }

    actual fun queryParameterName(index: Int): String {
        if (queryNamesAndValues == null) throw IndexOutOfBoundsException()
        return queryNamesAndValues[index * 2]!!
    }

    actual fun queryParameterValue(index: Int): String? {
        if (queryNamesAndValues == null) throw IndexOutOfBoundsException()
        return queryNamesAndValues[index * 2 + 1]
    }

    actual val encodedFragment: String?
        get() {
            if (fragment == null) return null
            val fragmentStart = url.indexOf('#') + 1
            return url.substring(fragmentStart)
        }

    actual fun redact(): String =
        newBuilder("/...")!!
            .username("")
            .password("")
            .build()
            .toString()

    actual fun resolve(link: String): HttpUrl? = newBuilder(link)?.build()

    actual fun newBuilder(): HttpUrlBuilder {
        val result = HttpUrlBuilder()
        result.scheme = scheme
        result.encodedUsername = encodedUsername
        result.encodedPassword = encodedPassword
        result.host = host
        // If we're set to a default port, unset it in case of a scheme change.
        result.port = if (port != defaultPort(scheme)) port else -1
        result.encodedPathSegments.clear()
        result.encodedPathSegments.addAll(encodedPathSegments)
        result.encodedQuery(encodedQuery)
        result.encodedFragment = encodedFragment
        return result
    }

    actual fun newBuilder(link: String): HttpUrlBuilder? =
        try {
            HttpUrlBuilder().parse(this, link)
        } catch (_: IllegalArgumentException) {
            null
        }

    actual override fun equals(other: Any?): Boolean = other is HttpUrl && other.url == url

    actual override fun hashCode(): Int = url.hashCode()

    actual override fun toString(): String = url

    /**
     * Returns the domain name of this URL's [host] that is one level beneath the public suffix by
     * consulting the [public suffix list](https://publicsuffix.org). Returns null if this URL's
     * [host] is an IP address or is considered a public suffix by the public suffix list.
     *
     * In general this method **should not** be used to test whether a domain is valid or routable.
     * Instead, DNS is the recommended source for that information.
     *
     * | URL                           | `topPrivateDomain()` |
     * | :---------------------------- | :------------------- |
     * | `http://google.com`           | `"google.com"`       |
     * | `http://adwords.google.co.uk` | `"google.co.uk"`     |
     * | `http://square`               | null                 |
     * | `http://co.uk`                | null                 |
     * | `http://localhost`            | null                 |
     * | `http://127.0.0.1`            | null                 |
     */
    suspend fun topPrivateDomain(): String? =
        if (host.canParseAsIpAddress()) {
            null
        } else {
            PublicSuffixDatabase.get().getEffectiveTldPlusOne(host)
        }

    companion object {
        /** Returns 80 if `scheme.equals("http")`, 443 if `scheme.equals("https")` and -1 otherwise. */
        fun defaultPort(scheme: String): Int =
            when (scheme) {
                "http" -> 80
                "https" -> 443
                else -> -1
            }
    }
}

actual class HttpUrlBuilder {
    internal var scheme: String? = null
    internal var encodedUsername = ""
    internal var encodedPassword = ""
    internal var host: String? = null
    internal var port = -1
    internal val encodedPathSegments = mutableListOf<String>("")
    internal var encodedQueryNamesAndValues: MutableList<String?>? = null
    internal var encodedFragment: String? = null

    actual fun scheme(scheme: String) =
        apply {
            when {
                scheme.equals("http", ignoreCase = true) -> this.scheme = "http"
                scheme.equals("https", ignoreCase = true) -> this.scheme = "https"
                else -> throw IllegalArgumentException("unexpected scheme: $scheme")
            }
        }

    actual fun username(username: String) =
        apply {
            this.encodedUsername = username.canonicalize(encodeSet = USERNAME_ENCODE_SET)
        }

    actual fun encodedUsername(encodedUsername: String) =
        apply {
            this.encodedUsername =
                encodedUsername.canonicalize(
                    encodeSet = USERNAME_ENCODE_SET,
                    alreadyEncoded = true,
                )
        }

    actual fun password(password: String) =
        apply {
            this.encodedPassword = password.canonicalize(encodeSet = PASSWORD_ENCODE_SET)
        }

    actual fun encodedPassword(encodedPassword: String) =
        apply {
            this.encodedPassword =
                encodedPassword.canonicalize(
                    encodeSet = PASSWORD_ENCODE_SET,
                    alreadyEncoded = true,
                )
        }

    actual fun host(host: String) =
        apply {
            val encoded =
                host.percentDecode().toCanonicalHost()
                    ?: throw IllegalArgumentException("unexpected host: $host")
            this.host = encoded
        }

    actual fun port(port: Int) =
        apply {
            require(port in 1..65535) { "unexpected port: $port" }
            this.port = port
        }

    actual fun addPathSegment(pathSegment: String) =
        apply {
            push(
                pathSegment,
                0,
                pathSegment.length,
                addTrailingSlash = false,
                alreadyEncoded = false
            )
        }

    actual fun addPathSegments(pathSegments: String) = addPathSegments(pathSegments, false)

    actual fun addEncodedPathSegment(encodedPathSegment: String) =
        apply {
            push(
                encodedPathSegment,
                0,
                encodedPathSegment.length,
                addTrailingSlash = false,
                alreadyEncoded = true,
            )
        }

    actual fun addEncodedPathSegments(encodedPathSegments: String) =
        addPathSegments(encodedPathSegments, true)

    private fun addPathSegments(
        pathSegments: String,
        alreadyEncoded: Boolean,
    ) = apply {
        var offset = 0
        do {
            val segmentEnd = pathSegments.delimiterOffset("/\\", offset, pathSegments.length)
            val addTrailingSlash = segmentEnd < pathSegments.length
            push(pathSegments, offset, segmentEnd, addTrailingSlash, alreadyEncoded)
            offset = segmentEnd + 1
        } while (offset <= pathSegments.length)
    }

    actual fun setPathSegment(
        index: Int,
        pathSegment: String,
    ) = apply {
        val canonicalPathSegment = pathSegment.canonicalize(encodeSet = PATH_SEGMENT_ENCODE_SET)
        require(!isDot(canonicalPathSegment) && !isDotDot(canonicalPathSegment)) {
            "unexpected path segment: $pathSegment"
        }
        encodedPathSegments[index] = canonicalPathSegment
    }

    actual fun setEncodedPathSegment(
        index: Int,
        encodedPathSegment: String,
    ) = apply {
        val canonicalPathSegment =
            encodedPathSegment.canonicalize(
                encodeSet = PATH_SEGMENT_ENCODE_SET,
                alreadyEncoded = true,
            )
        encodedPathSegments[index] = canonicalPathSegment
        require(!isDot(canonicalPathSegment) && !isDotDot(canonicalPathSegment)) {
            "unexpected path segment: $encodedPathSegment"
        }
    }

    actual fun removePathSegment(index: Int) =
        apply {
            encodedPathSegments.removeAt(index)
            if (encodedPathSegments.isEmpty()) {
                encodedPathSegments.add("") // Always leave at least one '/'.
            }
        }

    actual fun encodedPath(encodedPath: String) =
        apply {
            require(encodedPath.startsWith("/")) { "unexpected encodedPath: $encodedPath" }
            resolvePath(encodedPath, 0, encodedPath.length)
        }

    actual fun query(query: String?) =
        apply {
            this.encodedQueryNamesAndValues =
                query
                    ?.canonicalize(
                        encodeSet = QUERY_ENCODE_SET,
                        plusIsSpace = true,
                    )?.toQueryNamesAndValues()
        }

    actual fun encodedQuery(encodedQuery: String?) =
        apply {
            this.encodedQueryNamesAndValues =
                encodedQuery
                    ?.canonicalize(
                        encodeSet = QUERY_ENCODE_SET,
                        alreadyEncoded = true,
                        plusIsSpace = true,
                    )?.toQueryNamesAndValues()
        }

    actual fun addQueryParameter(
        name: String,
        value: String?,
    ) = apply {
        if (encodedQueryNamesAndValues == null) encodedQueryNamesAndValues = mutableListOf()
        encodedQueryNamesAndValues!!.add(
            name.canonicalize(
                encodeSet = QUERY_COMPONENT_ENCODE_SET,
                plusIsSpace = true,
            ),
        )
        encodedQueryNamesAndValues!!.add(
            value?.canonicalize(
                encodeSet = QUERY_COMPONENT_ENCODE_SET,
                plusIsSpace = true,
            ),
        )
    }

    actual fun addEncodedQueryParameter(
        encodedName: String,
        encodedValue: String?,
    ) = apply {
        if (encodedQueryNamesAndValues == null) encodedQueryNamesAndValues = mutableListOf()
        encodedQueryNamesAndValues!!.add(
            encodedName.canonicalize(
                encodeSet = QUERY_COMPONENT_REENCODE_SET,
                alreadyEncoded = true,
                plusIsSpace = true,
            ),
        )
        encodedQueryNamesAndValues!!.add(
            encodedValue?.canonicalize(
                encodeSet = QUERY_COMPONENT_REENCODE_SET,
                alreadyEncoded = true,
                plusIsSpace = true,
            ),
        )
    }

    actual fun setQueryParameter(
        name: String,
        value: String?,
    ) = apply {
        removeAllQueryParameters(name)
        addQueryParameter(name, value)
    }

    actual fun setEncodedQueryParameter(
        encodedName: String,
        encodedValue: String?,
    ) = apply {
        removeAllEncodedQueryParameters(encodedName)
        addEncodedQueryParameter(encodedName, encodedValue)
    }

    actual fun removeAllQueryParameters(name: String) =
        apply {
            if (encodedQueryNamesAndValues == null) return this
            val nameToRemove =
                name.canonicalize(
                    encodeSet = QUERY_COMPONENT_ENCODE_SET,
                    plusIsSpace = true,
                )
            removeAllCanonicalQueryParameters(nameToRemove)
        }

    actual fun removeAllEncodedQueryParameters(encodedName: String) =
        apply {
            if (encodedQueryNamesAndValues == null) return this
            removeAllCanonicalQueryParameters(
                encodedName.canonicalize(
                    encodeSet = QUERY_COMPONENT_REENCODE_SET,
                    alreadyEncoded = true,
                    plusIsSpace = true,
                ),
            )
        }

    private fun removeAllCanonicalQueryParameters(canonicalName: String) {
        for (i in encodedQueryNamesAndValues!!.size - 2 downTo 0 step 2) {
            if (canonicalName == encodedQueryNamesAndValues!![i]) {
                encodedQueryNamesAndValues!!.removeAt(i + 1)
                encodedQueryNamesAndValues!!.removeAt(i)
                if (encodedQueryNamesAndValues!!.isEmpty()) {
                    encodedQueryNamesAndValues = null
                    return
                }
            }
        }
    }

    actual fun fragment(fragment: String?) =
        apply {
            this.encodedFragment =
                fragment?.canonicalize(
                    encodeSet = FRAGMENT_ENCODE_SET,
                    unicodeAllowed = true,
                )
        }

    actual fun encodedFragment(encodedFragment: String?) =
        apply {
            this.encodedFragment =
                encodedFragment?.canonicalize(
                    encodeSet = FRAGMENT_ENCODE_SET,
                    alreadyEncoded = true,
                    unicodeAllowed = true,
                )
        }

    /**
     * Re-encodes the components of this URL so that it satisfies (obsolete) RFC 2396, which is
     * particularly strict for certain components.
     */
    internal fun reencodeForUri() =
        apply {
            host = host?.replace(Regex("[\"<>^`{|}]"), "")

            for (i in 0 until encodedPathSegments.size) {
                encodedPathSegments[i] =
                    encodedPathSegments[i].canonicalize(
                        encodeSet = PATH_SEGMENT_ENCODE_SET_URI,
                        alreadyEncoded = true,
                        strict = true,
                    )
            }

            val encodedQueryNamesAndValues = this.encodedQueryNamesAndValues
            if (encodedQueryNamesAndValues != null) {
                for (i in 0 until encodedQueryNamesAndValues.size) {
                    encodedQueryNamesAndValues[i] =
                        encodedQueryNamesAndValues[i]?.canonicalize(
                            encodeSet = QUERY_COMPONENT_ENCODE_SET_URI,
                            alreadyEncoded = true,
                            strict = true,
                            plusIsSpace = true,
                        )
                }
            }

            encodedFragment =
                encodedFragment?.canonicalize(
                    encodeSet = FRAGMENT_ENCODE_SET_URI,
                    alreadyEncoded = true,
                    strict = true,
                    unicodeAllowed = true,
                )
        }

    actual fun build(): HttpUrl {
        @Suppress("UNCHECKED_CAST") // percentDecode returns either List<String?> or List<String>.
        return HttpUrl(
            scheme = scheme ?: throw IllegalStateException("scheme == null"),
            username = encodedUsername.percentDecode(),
            password = encodedPassword.percentDecode(),
            host = host ?: throw IllegalStateException("host == null"),
            port = effectivePort(),
            pathSegments = encodedPathSegments.map { it.percentDecode() },
            queryNamesAndValues = encodedQueryNamesAndValues?.map { it?.percentDecode(plusIsSpace = true) },
            fragment = encodedFragment?.percentDecode(),
            url = toString(),
        )
    }

    private fun effectivePort(): Int = if (port != -1) port else defaultPort(scheme!!)

    actual override fun toString(): String =
        buildString {
            if (scheme != null) {
                append(scheme)
                append("://")
            } else {
                append("//")
            }

            if (encodedUsername.isNotEmpty() || encodedPassword.isNotEmpty()) {
                append(encodedUsername)
                if (encodedPassword.isNotEmpty()) {
                    append(':')
                    append(encodedPassword)
                }
                append('@')
            }

            if (host != null) {
                if (':' in host!!) {
                    // Host is an IPv6 address.
                    append('[')
                    append(host)
                    append(']')
                } else {
                    append(host)
                }
            }

            if (port != -1 || scheme != null) {
                val effectivePort = effectivePort()
                if (scheme == null || effectivePort != defaultPort(scheme!!)) {
                    append(':')
                    append(effectivePort)
                }
            }

            encodedPathSegments.toPathString(this)

            if (encodedQueryNamesAndValues != null) {
                append('?')
                encodedQueryNamesAndValues!!.toQueryString(this)
            }

            if (encodedFragment != null) {
                append('#')
                append(encodedFragment)
            }
        }

    /** Returns a path string for this list of path segments. */
    private fun List<String>.toPathString(out: StringBuilder) {
        for (i in 0 until size) {
            out.append('/')
            out.append(this[i])
        }
    }

    internal fun parse(
        base: HttpUrl?,
        input: String,
    ): HttpUrlBuilder {
        var pos = input.indexOfFirstNonAsciiWhitespace()
        val limit = input.indexOfLastNonAsciiWhitespace(pos)

        // Scheme.
        val schemeDelimiterOffset = schemeDelimiterOffset(input, pos, limit)
        if (schemeDelimiterOffset != -1) {
            when {
                input.startsWith("https:", ignoreCase = true, startIndex = pos) -> {
                    this.scheme = "https"
                    pos += "https:".length
                }

                input.startsWith("http:", ignoreCase = true, startIndex = pos) -> {
                    this.scheme = "http"
                    pos += "http:".length
                }

                else -> throw IllegalArgumentException(
                    "Expected URL scheme 'http' or 'https' but was '" +
                            input.substring(0, schemeDelimiterOffset) + "'",
                )
            }
        } else if (base != null) {
            this.scheme = base.scheme
        } else {
            val truncated = if (input.length > 6) input.take(6) + "..." else input
            throw IllegalArgumentException(
                "Expected URL scheme 'http' or 'https' but no scheme was found for $truncated",
            )
        }

        // Authority.
        var hasUsername = false
        var hasPassword = false
        val slashCount = input.slashCount(pos, limit)
        if (slashCount >= 2 || base == null || base.scheme != this.scheme) {
            // Read an authority if either:
            //  * The input starts with 2 or more slashes. These follow the scheme if it exists.
            //  * The input scheme exists and is different from the base URL's scheme.
            //
            // The structure of an authority is:
            //   username:password@host:port
            //
            // Username, password and port are optional.
            //   [username[:password]@]host[:port]
            pos += slashCount
            authority@ while (true) {
                val componentDelimiterOffset = input.delimiterOffset("@/\\?#", pos, limit)
                val c =
                    if (componentDelimiterOffset != limit) {
                        input[componentDelimiterOffset].code
                    } else {
                        -1
                    }
                when (c) {
                    '@'.code -> {
                        // User info precedes.
                        if (!hasPassword) {
                            val passwordColonOffset =
                                input.delimiterOffset(':', pos, componentDelimiterOffset)
                            val canonicalUsername =
                                input.canonicalize(
                                    pos = pos,
                                    limit = passwordColonOffset,
                                    encodeSet = USERNAME_ENCODE_SET,
                                    alreadyEncoded = true,
                                )
                            this.encodedUsername =
                                if (hasUsername) {
                                    this.encodedUsername + "%40" + canonicalUsername
                                } else {
                                    canonicalUsername
                                }
                            if (passwordColonOffset != componentDelimiterOffset) {
                                hasPassword = true
                                this.encodedPassword =
                                    input.canonicalize(
                                        pos = passwordColonOffset + 1,
                                        limit = componentDelimiterOffset,
                                        encodeSet = PASSWORD_ENCODE_SET,
                                        alreadyEncoded = true,
                                    )
                            }
                            hasUsername = true
                        } else {
                            this.encodedPassword = this.encodedPassword + "%40" +
                                    input.canonicalize(
                                        pos = pos,
                                        limit = componentDelimiterOffset,
                                        encodeSet = PASSWORD_ENCODE_SET,
                                        alreadyEncoded = true,
                                    )
                        }
                        pos = componentDelimiterOffset + 1
                    }

                    -1, '/'.code, '\\'.code, '?'.code, '#'.code -> {
                        // Host info precedes.
                        val portColonOffset = portColonOffset(input, pos, componentDelimiterOffset)
                        if (portColonOffset + 1 < componentDelimiterOffset) {
                            host = input.percentDecode(pos = pos, limit = portColonOffset)
                                .toCanonicalHost()
                            port = parsePort(input, portColonOffset + 1, componentDelimiterOffset)
                            require(port != -1) {
                                "Invalid URL port: \"${
                                    input.substring(
                                        portColonOffset + 1,
                                        componentDelimiterOffset,
                                    )
                                }\""
                            }
                        } else {
                            host = input.percentDecode(pos = pos, limit = portColonOffset)
                                .toCanonicalHost()
                            port = defaultPort(scheme!!)
                        }
                        require(host != null) {
                            "Invalid URL host: \"${input.substring(pos, portColonOffset)}\""
                        }
                        pos = componentDelimiterOffset
                        break@authority
                    }
                }
            }
        } else {
            // This is a relative link. Copy over all authority components. Also maybe the path & query.
            this.encodedUsername = base.encodedUsername
            this.encodedPassword = base.encodedPassword
            this.host = base.host
            this.port = base.port
            this.encodedPathSegments.clear()
            this.encodedPathSegments.addAll(base.encodedPathSegments)
            if (pos == limit || input[pos] == '#') {
                encodedQuery(base.encodedQuery)
            }
        }

        // Resolve the relative path.
        val pathDelimiterOffset = input.delimiterOffset("?#", pos, limit)
        resolvePath(input, pos, pathDelimiterOffset)
        pos = pathDelimiterOffset

        // Query.
        if (pos < limit && input[pos] == '?') {
            val queryDelimiterOffset = input.delimiterOffset('#', pos, limit)
            this.encodedQueryNamesAndValues =
                input
                    .canonicalize(
                        pos = pos + 1,
                        limit = queryDelimiterOffset,
                        encodeSet = QUERY_ENCODE_SET,
                        alreadyEncoded = true,
                        plusIsSpace = true,
                    ).toQueryNamesAndValues()
            pos = queryDelimiterOffset
        }

        // Fragment.
        if (pos < limit && input[pos] == '#') {
            this.encodedFragment =
                input.canonicalize(
                    pos = pos + 1,
                    limit = limit,
                    encodeSet = FRAGMENT_ENCODE_SET,
                    alreadyEncoded = true,
                    unicodeAllowed = true,
                )
        }

        return this
    }

    private fun resolvePath(
        input: String,
        startPos: Int,
        limit: Int,
    ) {
        var pos = startPos
        // Read a delimiter.
        if (pos == limit) {
            // Empty path: keep the base path as-is.
            return
        }
        val c = input[pos]
        if (c == '/' || c == '\\') {
            // Absolute path: reset to the default "/".
            encodedPathSegments.clear()
            encodedPathSegments.add("")
            pos++
        } else {
            // Relative path: clear everything after the last '/'.
            encodedPathSegments[encodedPathSegments.size - 1] = ""
        }

        // Read path segments.
        var i = pos
        while (i < limit) {
            val pathSegmentDelimiterOffset = input.delimiterOffset("/\\", i, limit)
            val segmentHasTrailingSlash = pathSegmentDelimiterOffset < limit
            push(input, i, pathSegmentDelimiterOffset, segmentHasTrailingSlash, true)
            i = pathSegmentDelimiterOffset
            if (segmentHasTrailingSlash) i++
        }
    }

    /** Adds a path segment. If the input is ".." or equivalent, this pops a path segment. */
    private fun push(
        input: String,
        pos: Int,
        limit: Int,
        addTrailingSlash: Boolean,
        alreadyEncoded: Boolean,
    ) {
        val segment =
            input.canonicalize(
                pos = pos,
                limit = limit,
                encodeSet = PATH_SEGMENT_ENCODE_SET,
                alreadyEncoded = alreadyEncoded,
            )
        if (isDot(segment)) {
            return // Skip '.' path segments.
        }
        if (isDotDot(segment)) {
            pop()
            return
        }
        if (encodedPathSegments[encodedPathSegments.size - 1].isEmpty()) {
            encodedPathSegments[encodedPathSegments.size - 1] = segment
        } else {
            encodedPathSegments.add(segment)
        }
        if (addTrailingSlash) {
            encodedPathSegments.add("")
        }
    }

    /**
     * Removes a path segment. When this method returns the last segment is always "", which means
     * the encoded path will have a trailing '/'.
     *
     * Popping "/a/b/c/" yields "/a/b/". In this case the list of path segments goes from ["a",
     * "b", "c", ""] to ["a", "b", ""].
     *
     * Popping "/a/b/c" also yields "/a/b/". The list of path segments goes from ["a", "b", "c"]
     * to ["a", "b", ""].
     */
    private fun pop() {
        val removed = encodedPathSegments.removeAt(encodedPathSegments.size - 1)

        // Make sure the path ends with a '/' by either adding an empty string or clearing a segment.
        if (removed.isEmpty() && encodedPathSegments.isNotEmpty()) {
            encodedPathSegments[encodedPathSegments.size - 1] = ""
        } else {
            encodedPathSegments.add("")
        }
    }

    private fun isDot(input: String): Boolean =
        input == "." || input.equals("%2e", ignoreCase = true)

    private fun isDotDot(input: String): Boolean =
        input == ".." ||
                input.equals("%2e.", ignoreCase = true) ||
                input.equals(".%2e", ignoreCase = true) ||
                input.equals("%2e%2e", ignoreCase = true)

    /**
     * Cuts this string up into alternating parameter names and values. This divides a query string
     * like `subject=math&easy&problem=5-2=3` into the list `["subject", "math", "easy", null,
     * "problem", "5-2=3"]`. Note that values may be null and may contain '=' characters.
     */
    private fun String.toQueryNamesAndValues(): MutableList<String?> {
        val result = mutableListOf<String?>()
        var pos = 0
        while (pos <= length) {
            var ampersandOffset = indexOf('&', pos)
            if (ampersandOffset == -1) ampersandOffset = length

            val equalsOffset = indexOf('=', pos)
            if (equalsOffset == -1 || equalsOffset > ampersandOffset) {
                result.add(substring(pos, ampersandOffset))
                result.add(null) // No value for this name.
            } else {
                result.add(substring(pos, equalsOffset))
                result.add(substring(equalsOffset + 1, ampersandOffset))
            }
            pos = ampersandOffset + 1
        }
        return result
    }

    /**
     * Returns the index of the ':' in `input` that is after scheme characters. Returns -1 if
     * `input` does not have a scheme that starts at `pos`.
     */
    private fun schemeDelimiterOffset(
        input: String,
        pos: Int,
        limit: Int,
    ): Int {
        if (limit - pos < 2) return -1

        val c0 = input[pos]
        if ((c0 < 'a' || c0 > 'z') && (c0 < 'A' || c0 > 'Z')) return -1 // Not a scheme start char.

        characters@ for (i in pos + 1 until limit) {
            return when (input[i]) {
                // Scheme character. Keep going.
                in 'a'..'z', in 'A'..'Z', in '0'..'9', '+', '-', '.' -> continue@characters

                // Scheme prefix!
                ':' -> i

                // Non-scheme character before the first ':'.
                else -> -1
            }
        }

        return -1 // No ':'; doesn't start with a scheme.
    }

    /** Returns the number of '/' and '\' slashes in this, starting at `pos`. */
    private fun String.slashCount(
        pos: Int,
        limit: Int,
    ): Int {
        var slashCount = 0
        for (i in pos until limit) {
            val c = this[i]
            if (c == '\\' || c == '/') {
                slashCount++
            } else {
                break
            }
        }
        return slashCount
    }

    /** Finds the first ':' in `input`, skipping characters between square braces "[...]". */
    private fun portColonOffset(
        input: String,
        pos: Int,
        limit: Int,
    ): Int {
        var i = pos
        while (i < limit) {
            when (input[i]) {
                '[' -> {
                    while (++i < limit) {
                        if (input[i] == ']') break
                    }
                }

                ':' -> return i
            }
            i++
        }
        return limit // No colon.
    }

    private fun parsePort(
        input: String,
        pos: Int,
        limit: Int,
    ): Int =
        try {
            // Canonicalize the port string to skip '\n' etc.
            val portString = input.canonicalize(pos = pos, limit = limit, encodeSet = "")
            val i = portString.toInt()
            if (i in 1..65535) i else -1
        } catch (_: NumberFormatException) {
            -1 // Invalid port.
        }
}

/** Returns a string for this list of query names and values. */
private fun List<String?>.toQueryString(out: StringBuilder) {
    for (i in 0 until size step 2) {
        val name = this[i]
        val value = this[i + 1]
        if (i > 0) out.append('&')
        out.append(name)
        if (value != null) {
            out.append('=')
            out.append(value)
        }
    }
}

/**
 * Returns a new [HttpUrl] representing this.
 *
 * @throws IllegalArgumentException If this is not a well-formed HTTP or HTTPS URL.
 */
actual fun String.toHttpUrl(): HttpUrl = HttpUrlBuilder().parse(null, this).build()

/**
 * Returns a new `HttpUrl` representing `url` if it is a well-formed HTTP or HTTPS URL, or null
 * if it isn't.
 */
actual fun String.toHttpUrlOrNull(): HttpUrl? =
    try {
        toHttpUrl()
    } catch (_: IllegalArgumentException) {
        null
    }

actual suspend fun HttpUrl.getTopPrivateDomain(): String? = this.topPrivateDomain()