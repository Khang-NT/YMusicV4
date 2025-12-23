/**
 * RFC 3986 Compliant URI Implementation for Kotlin Multiplatform
 *
 * A Uniform Resource Identifier (URI) is a compact sequence of characters
 * that identifies an abstract or physical resource.
 *
 * URI Syntax (Section 3):
 *   URI = scheme ":" hier-part [ "?" query ] [ "#" fragment ]
 *   hier-part = "//" authority path-abempty
 *             / path-absolute
 *             / path-rootless
 *             / path-empty
 *   authority = [ userinfo "@" ] host [ ":" port ]
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc3986.html">RFC 3986</a>
 */
package kmpcommon.uri

import kmpcommon.LinkedList
import kmpcommon.indexOf
import kmpcommon.isAlphabetLetter
import kmpcommon.isAlphabetLetterOrDigit
import kmpcommon.splitSequence
import kmpcommon.subSequence

/**
 * An immutable string Uri, inspired by [andorid.net.Uri].
 *
 * In the interest of performance, this class performs little to no validation.
 * Behavior is undefined for invalid input. This class is very forgiving--in the face of invalid input,
 * it will return garbage rather than throw an exception unless otherwise specified.
 */
class Uri private constructor(
    val uriString: String,
    private val schemePart: IntRange?,
    private val authorityPart: IntRange?,
    private val encodedPathPart: IntRange,
    private val encodedQueryPart: IntRange?,
    private val encodedFragmentPart: IntRange?
) {

    val scheme: CharSequence?
        get() = schemePart?.let { uriString.subSequence(it) }

    val authority: CharSequence?
        get() = authorityPart?.let { uriString.subSequence(it) }

    /**
     * Returns true if this URI is absolute (has a scheme).
     * Section 4.3: absolute-URI = scheme ":" hier-part [ "?" query ]
     */
    val isAbsolute: Boolean get() = schemePart != null

    /**
     * Returns true if this URI is opaque (has a scheme but no authority).
     */
    val isOpaque: Boolean
        get() = schemePart != null
                && authorityPart == null
                && (encodedPathPart.isEmpty() || uriString[encodedPathPart.first] != '/')

    val encodedQuery: CharSequence?
        get() = encodedQueryPart?.let { uriString.subSequence(it) }

    /**
     * Decoded query parameters
     */
    private val queryParameters: List<Pair<CharSequence, CharSequence?>>?
            by lazy(LazyThreadSafetyMode.NONE) {
                encodedQueryPart ?: return@lazy null
                parseEncodedQuery(uriString.subSequence(encodedQueryPart))
            }

    fun queryNames(): Set<CharSequence>? = queryParameters?.mapTo(mutableSetOf()) { it.first }

    fun queryParamValue(name: CharSequence): CharSequence? {
        return queryParameters?.firstOrNull { it.first.contentEquals(name) }?.second
    }

    fun queryParamValues(name: CharSequence): List<CharSequence?>? {
        return queryParameters?.filter { it.first.contentEquals(name) }?.map { it.second }
    }

    val encodedPath: CharSequence
        get() = if (encodedPathPart.isEmpty()) "" else uriString.subSequence(encodedPathPart)

    val path: CharSequence
        get() = encodedPath.splitSequence('/')
            .joinToString("/") {
                PercentEncoder.decode(it)
            }

    val canonicalPath: CharSequence
        get() = removeDotSegments(encodedPath)

    val encodedFragment: CharSequence?
        get() = encodedFragmentPart?.let { uriString.subSequence(it) }

    val fragment: CharSequence?
        get() = encodedFragment?.let { PercentEncoder.decode(it) }


    /**
     * Resolves a URI-reference against this URI as base, per RFC 3986 Section 5.2.2.
     *
     * Examples (with base "http://a/b/c/d;p?q"):
     * - resolve("g") -> "http://a/b/c/g"
     * - resolve("./g") -> "http://a/b/c/g"
     * - resolve("g/") -> "http://a/b/c/g/"
     * - resolve("/g") -> "http://a/g"
     * - resolve("//g") -> "http://g"
     * - resolve("?y") -> "http://a/b/c/d;p?y"
     * - resolve("g?y") -> "http://a/b/c/g?y"
     * - resolve("#s") -> "http://a/b/c/d;p?q#s"
     * - resolve("g#s") -> "http://a/b/c/g#s"
     * - resolve("../g") -> "http://a/b/g"
     * - resolve("../../g") -> "http://a/g"
     *
     * @param reference The URI-reference to resolve (can be relative or absolute)
     * @return The resolved URI
     */
    fun resolve(reference: Uri): Uri {
        // RFC 3986 Section 5.2.2 - Transform References
        return if (reference.schemePart != null) {
            // Reference has scheme - use it directly (with dot segment removal)
            recompose(
                scheme = reference.scheme,
                authority = reference.authority,
                encodedPath = removeDotSegments(reference.encodedPath),
                encodedQuery = reference.encodedQuery,
                encodedFragment = reference.encodedFragment
            )
        } else {
            if (reference.authorityPart != null) {
                // Reference has authority
                recompose(
                    scheme = this.scheme,
                    authority = reference.authority,
                    encodedPath = removeDotSegments(reference.encodedPath),
                    encodedQuery = reference.encodedQuery,
                    encodedFragment = reference.encodedFragment
                )
            } else {
                if (reference.encodedPathPart.isEmpty()) {
                    // Empty path reference
                    recompose(
                        scheme = this.scheme,
                        authority = this.authority,
                        encodedPath = this.encodedPath,
                        encodedQuery = reference.encodedQuery ?: this.encodedQuery,
                        encodedFragment = reference.encodedFragment
                    )
                } else {
                    val targetPath = if (reference.encodedPath.startsWith("/")) {
                        // Absolute path reference
                        removeDotSegments(reference.encodedPath)
                    } else {
                        // Relative path reference - merge with base
                        removeDotSegments(merge(reference.encodedPath))
                    }
                    recompose(
                        scheme = this.scheme,
                        authority = this.authority,
                        encodedPath = targetPath,
                        encodedQuery = reference.encodedQuery,
                        encodedFragment = reference.encodedFragment
                    )
                }
            }
        }
    }

    /**
     * Resolves a URI-reference string against this URI as base.
     * @see resolve(Uri)
     */
    fun resolve(reference: String): Uri = resolve(parse(reference))

    /**
     * Merge path per RFC 3986 Section 5.2.3
     */
    private fun merge(referencePath: CharSequence): CharSequence {
        return if (authorityPart != null && encodedPath.isEmpty()) {
            // Base has authority and empty path
            "/$referencePath"
        } else {
            // Remove last segment from base path and append reference
            val lastSlash = encodedPath.lastIndexOf('/')
            if (lastSlash >= 0) {
                encodedPath.substring(0, lastSlash + 1) + referencePath
            } else {
                referencePath
            }
        }
    }

    override fun toString(): String = uriString

    override fun equals(other: Any?): Boolean {
        if (other !is Uri) return false
        return this.uriString.contentEquals(other.uriString)
    }

    override fun hashCode(): Int = uriString.hashCode()

    companion object {
        /**
         * Parses a URI string according to RFC 3986.
         * In the interest of performance, this method performs little to no validation.
         * Behavior is undefined for invalid input. This class is very forgiving--in the face of invalid input,
         * it will return garbage rather than throw an exception unless otherwise specified.
         *
         * @throws UriSyntaxException if the [uriString] is malformed.
         */
        fun parse(str: CharSequence): Uri {
            // Trim leading and trailing whitespace
            var start = 0
            var end = str.length
            while (start < end && str[start].isWhitespace()) start++
            while (end > start && str[end - 1].isWhitespace()) end--
            val trimmed = if (start == 0 && end == str.length) str else str.subSequence(start, end)

            val len = trimmed.length
            if (len == 0) {
                return create("", null, null, IntRange.EMPTY, null, null)
            }

            var pos = 0

            // 1. Find fragment
            var fragmentStart: Int = -1
            var fragmentPart: IntRange? = null
            for (i in 0 until len) {
                if (trimmed[i] == '#') {
                    fragmentStart = i
                    fragmentPart = if (i + 1 < len) (i + 1) until len else IntRange.EMPTY
                    break
                }
            }
            val endBeforeFragment = if (fragmentStart >= 0) fragmentStart else len

            // 2. Find query (look for '?' before fragment)
            var queryStart: Int = -1
            var queryPart: IntRange? = null
            for (i in 0 until endBeforeFragment) {
                if (trimmed[i] == '?') {
                    queryStart = i
                    queryPart =
                        if (i + 1 < endBeforeFragment) (i + 1) until endBeforeFragment else IntRange.EMPTY
                    break
                }
            }
            val endBeforeQuery = if (queryStart >= 0) queryStart else endBeforeFragment

            // 3. Find scheme (first ':' that appears before any '/', '?', '#')
            var schemePart: IntRange? = null
            var schemeEnd = -1
            for (i in 0 until endBeforeQuery) {
                val c = trimmed[i]
                if (c == ':') {
                    if (i > 0) {
                        schemePart = 0 until i
                        schemeEnd = i
                        validateScheme(trimmed, schemePart)
                    }
                    break
                }
                if (c == '/' || c == '?' || c == '#') break
            }
            pos = if (schemeEnd >= 0) schemeEnd + 1 else 0

            // 4. Find authority (if starts with "//")
            var authorityPart: IntRange? = null
            if (pos + 1 < endBeforeQuery && trimmed[pos] == '/' && trimmed[pos + 1] == '/') {
                pos += 2
                val authorityStart = pos
                // Authority ends at next '/' or end
                while (pos < endBeforeQuery && trimmed[pos] != '/') {
                    pos++
                }
                authorityPart =
                    if (pos > authorityStart) authorityStart until pos else IntRange.EMPTY
            }

            // 5. Remaining is path
            val pathPart = if (pos < endBeforeQuery) pos until endBeforeQuery else IntRange.EMPTY

            val uriString = trimmed as? String ?: trimmed.toString()
            return create(uriString, schemePart, authorityPart, pathPart, queryPart, fragmentPart)
        }

        /**
         * Recomposes URI components into a new Uri (RFC 3986 Section 5.3).
         */
        internal fun recompose(
            scheme: CharSequence?,
            authority: CharSequence?,
            encodedPath: CharSequence,
            encodedQuery: CharSequence?,
            encodedFragment: CharSequence?
        ): Uri {
            var schemePart: IntRange? = null
            var authorityPart: IntRange? = null
            var pathPart: IntRange = IntRange.EMPTY
            var queryPart: IntRange? = null
            var fragmentPart: IntRange? = null

            val str = buildString {
                scheme?.let {
                    val start = length
                    append(it)
                    schemePart = start until length
                    append(':')
                }
                authority?.let {
                    append("//")
                    val start = length
                    append(it)
                    authorityPart = start until length
                }
                if (encodedPath.isNotEmpty()) {
                    val start = length
                    append(encodedPath)
                    pathPart = start until length
                }
                encodedQuery?.let {
                    append('?')
                    val start = length
                    append(it)
                    queryPart = start until length
                }
                encodedFragment?.let {
                    append('#')
                    val start = length
                    append(it)
                    fragmentPart = start until length
                }
            }
            return Uri(str, schemePart, authorityPart, pathPart, queryPart, fragmentPart)
        }

        fun parseEncodedQuery(encodedQuery: CharSequence): List<Pair<CharSequence, CharSequence?>> {
            if (encodedQuery.isBlank()) return emptyList()
            return encodedQuery.splitSequence('&').mapTo(mutableListOf()) { pair ->
                val equalsIndex = pair.indexOf('=')
                if (equalsIndex > -1) {
                    PercentEncoder.decode(pair.subSequence(0, equalsIndex)) to
                            PercentEncoder.decode(pair.subSequence(equalsIndex + 1))
                } else {
                    PercentEncoder.decode(pair) to null
                }
            }
        }


        /**
         * Attempts to parse a URI string, returning null if invalid.
         */
        fun parseOrNull(input: CharSequence): Uri? {
            return try {
                parse(input)
            } catch (_: UriSyntaxException) {
                null
            }
        }

        /**
         * Creates a URI from uri string with the index of following components:
         * - scheme: The URI scheme (e.g., "http", "https", "ftp")
         * - authority: The authority component (contains userinfo, host, port)
         * - encodedPath: The path component encoded, characters which are not allowed in path will be percent-encode automatically.
         * - queryParameters: The query component.
         * - encodedFragment: The fragment component encoded (without the leading "#"), characters which are not allowed in fragment will be percent-encode automatically.
         * @throws UriSyntaxException if parameters are not a valid URI
         */
        internal fun create(
            uriString: String,
            scheme: IntRange?,
            authority: IntRange?,
            encodedPath: IntRange,
            encodedQuery: IntRange?,
            encodedFragment: IntRange?
        ): Uri {
            fun checkUriSyntax(check: Boolean) {
                if (!check) throw UriSyntaxException("Invalid uri: $uriString")
            }

            var lastComponentIndexExclusive = 0
            if (scheme != null) {
                checkUriSyntax(!scheme.isEmpty())
                validateScheme(uriString, scheme)
                lastComponentIndexExclusive = scheme.endInclusive + 1
                checkUriSyntax(lastComponentIndexExclusive < uriString.length)
                checkUriSyntax(uriString[lastComponentIndexExclusive] == ':')
                lastComponentIndexExclusive++ // move past ':'
            }
            if (authority != null) {
                // Authority can be empty (e.g., file:///path), check "//" delimiter
                checkUriSyntax(uriString[lastComponentIndexExclusive] == '/')
                checkUriSyntax(uriString[lastComponentIndexExclusive + 1] == '/')
                if (!authority.isEmpty()) {
                    checkUriSyntax(authority.first >= lastComponentIndexExclusive + 2) // after "//"
                    lastComponentIndexExclusive = authority.endInclusive + 1
                } else {
                    lastComponentIndexExclusive += 2 // move past "//"
                }
            }
            checkUriSyntax(encodedPath.isEmpty() || encodedPath.first >= lastComponentIndexExclusive)
            if (!encodedPath.isEmpty()) {
                lastComponentIndexExclusive = encodedPath.endInclusive + 1
            }
            if (encodedQuery != null) {
                // Query can be empty (e.g., http://example.com?)
                checkUriSyntax(uriString[lastComponentIndexExclusive] == '?')
                if (!encodedQuery.isEmpty()) {
                    checkUriSyntax(encodedQuery.first >= lastComponentIndexExclusive + 1) // after '?'
                    lastComponentIndexExclusive = encodedQuery.endInclusive + 1
                } else {
                    lastComponentIndexExclusive++ // move past '?'
                }
            }
            if (encodedFragment != null) {
                // Fragment can be empty (e.g., http://example.com#)
                checkUriSyntax(uriString[lastComponentIndexExclusive] == '#')
                if (!encodedFragment.isEmpty()) {
                    checkUriSyntax(encodedFragment.first >= lastComponentIndexExclusive + 1) // after '#'
                    lastComponentIndexExclusive = encodedFragment.endInclusive + 1
                } else {
                    lastComponentIndexExclusive++ // move past '#'
                }
            }
            checkUriSyntax(lastComponentIndexExclusive == uriString.length)

            if (authority != null) {
                if (!encodedPath.isEmpty() && uriString[encodedPath.first] != '/') {
                    throw UriSyntaxException(
                        "Invalid uri: (\"${uriString}\"). If a URI contains an authority component, then the path component" +
                                "must either be empty or begin with a slash (\"/\") character."
                    )
                }
            } else {
                if ((encodedPath.start + 1 <= encodedPath.endInclusive)
                    && uriString[encodedPath.start] == '/'
                    && uriString[encodedPath.start + 1] == '/'
                ) {
                    throw UriSyntaxException(
                        "If a URI does not contain an authority component, " +
                                "then the path cannot begin with two slash characters (\"//\")"
                    )
                }
                if (scheme == null) {
                    val firstSlashIndex = uriString.indexOf('/', within = encodedPath)
                    if (firstSlashIndex > -1) {
                        val colonIndex =
                            uriString.indexOf(':', within = encodedPath.start until firstSlashIndex)
                        if (colonIndex > -1) {
                            throw UriSyntaxException(
                                "Invalid uri: (\"${uriString}\"). `path-noscheme` must begin with a non-colon segment"
                            )
                        }
                    }
                }
            }

            return Uri(uriString, scheme, authority, encodedPath, encodedQuery, encodedFragment)
        }

        /**
         * Validates scheme according to Section 3.1
         * scheme = ALPHA *( ALPHA / DIGIT / "+" / "-" / "." )
         */
        private fun validateScheme(uri: CharSequence, scheme: IntRange) {
            if (scheme.isEmpty()) {
                throw UriSyntaxException("Scheme cannot be empty")
            }
            if (!uri[scheme.start].isAlphabetLetter()) {
                throw UriSyntaxException("Scheme must start with a letter: $scheme")
            }
            for (i in scheme) {
                val c = uri[i]
                if (!c.isAlphabetLetterOrDigit() && c != '+' && c != '-' && c != '.') {
                    throw UriSyntaxException("Invalid character in scheme: $c")
                }
            }
        }

        private fun isDot(input: CharSequence) =
            input == "." || input.contentEquals("%2e", ignoreCase = true)

        private fun isDotDot(input: CharSequence): Boolean =
            input == ".." ||
                    input.contentEquals("%2e.", ignoreCase = true) ||
                    input.contentEquals(".%2e", ignoreCase = true) ||
                    input.contentEquals("%2e%2e", ignoreCase = true)

        /**
         * Remove dot segments from path per RFC 3986 Section 5.2.4
         *
         * This algorithm removes "." and ".." segments from a path to produce
         * a normalized path that can be compared with other paths.
         */
        internal fun removeDotSegments(encodedPath: CharSequence): CharSequence {
            if (encodedPath.isEmpty()) return encodedPath

            val output = LinkedList<CharSequence>()
            val startsWithSlash = encodedPath[0] == '/'

            var endsWithDotOrSlash = false
            encodedPath.splitSequence('/').forEach { segment ->
                when {
                    isDot(segment) -> {
                        /* skip single dot */
                        endsWithDotOrSlash = true
                    }

                    isDotDot(segment) -> {
                        // Go up one level - remove last segment if exists
                        if (output.isNotEmpty()) {
                            output.removeLast()
                        }
                        endsWithDotOrSlash = true
                    }

                    segment.isNotEmpty() -> {
                        output.add(segment)
                        endsWithDotOrSlash = false
                    }

                    else -> {
                        endsWithDotOrSlash = true
                    }
                }
            }

            return buildString {
                if (startsWithSlash) append('/')
                output.forEachIndexed { index, seg ->
                    if (index > 0) append('/')
                    append(seg)
                }
                if (endsWithDotOrSlash && output.isNotEmpty() && !endsWith('/')) {
                    append('/')
                }
            }
        }
    }
}

/**
 * Exception thrown when URI parsing fails.
 */
class UriSyntaxException(message: String) : Exception(message)