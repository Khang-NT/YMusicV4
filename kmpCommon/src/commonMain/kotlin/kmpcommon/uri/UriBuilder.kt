/**
 * Builder pattern for constructing URIs according to RFC 3986.
 */
package kmpcommon.uri

/**
 * Fluent builder for constructing URIs.
 *
 * Example usage:
 * ```kotlin
 * val uri = UriBuilder()
 *     .scheme("https")
 *     .authority("example.com:8080")
 *     .path("/api/users")
 *     .appendQueryParam("page", "1")
 *     .fragment("top")
 *     .build()
 * ```
 */
class UriBuilder private constructor(
    private var scheme: String? = null,
    private var authority: String? = null,
    private var encodedPath: StringBuilder = StringBuilder(),
    private var encodedQuery: StringBuilder? = null,
    private var encodedFragment: String? = null
) {
    constructor() : this(scheme = null)

    /**
     * Creates a builder initialized from an existing URI.
     * Canonicalizes all components to ensure well-formed output.
     */
    constructor(uri: Uri) : this(
        // Scheme is case-insensitive, canonicalize to lowercase
        scheme = uri.scheme?.toString()?.lowercase(),
        // Authority is already encoded in Uri
        authority = uri.authority?.toString(),
        // Re-canonicalize path to ensure proper encoding
        encodedPath = StringBuilder(PercentEncoder.canonicalizePath(uri.encodedPath, alreadyEncoded = true)),
        // Re-canonicalize query to ensure proper encoding
        encodedQuery = uri.encodedQuery?.let { StringBuilder(PercentEncoder.canonicalizeQuery(it, alreadyEncoded = true)) },
        // Re-canonicalize fragment to ensure proper encoding
        encodedFragment = uri.encodedFragment?.let { PercentEncoder.canonicalizeFragment(it, alreadyEncoded = true).toString() }
    )

    /**
     * Creates a builder initialized from a URI string.
     */
    constructor(uriString: String) : this(Uri.parse(uriString))

    fun scheme(scheme: String?): UriBuilder = apply {
        this.scheme = scheme
    }

    fun authority(authority: String?): UriBuilder = apply {
        this.authority = authority
    }

    fun path(path: String, encoded: Boolean = false): UriBuilder = apply {
        this.encodedPath = StringBuilder(PercentEncoder.canonicalizePath(path, alreadyEncoded = encoded))
    }

    fun appendPath(path: String, encoded: Boolean = false): UriBuilder = apply {
        val canonicalized = PercentEncoder.canonicalizePath(path, alreadyEncoded = encoded)
        if (encodedPath.isNotEmpty() && !encodedPath.endsWith('/') && !canonicalized.startsWith('/')) {
            encodedPath.append('/')
        }
        encodedPath.append(canonicalized)
    }

    fun appendPathSegment(segment: String, encoded: Boolean = false): UriBuilder = apply {
        if (!encodedPath.endsWith('/')) {
            encodedPath.append('/')
        }
        encodedPath.append(PercentEncoder.canonicalizePathSegment(segment, alreadyEncoded = encoded))
    }

    fun encodedQuery(query: String?): UriBuilder = apply {
        this.encodedQuery = query?.let { StringBuilder(PercentEncoder.canonicalizeQuery(it, alreadyEncoded = true)) }
    }

    fun appendQueryParam(name: String, value: String?): UriBuilder = apply {
        val encodedName = PercentEncoder.canonicalizeQueryParamName(name)
        val query = encodedQuery ?: StringBuilder().also { encodedQuery = it }
        if (query.isNotEmpty()) {
            query.append('&')
        }
        query.append(encodedName)
        if (value != null) {
            query.append('=')
            query.append(PercentEncoder.canonicalizeQueryParamValue(value))
        }
    }

    fun fragment(fragment: String?, encoded: Boolean = false): UriBuilder = apply {
        this.encodedFragment = fragment?.let { PercentEncoder.canonicalizeFragment(it, encoded).toString() }
    }

    fun build(): Uri = Uri.recompose(scheme, authority, encodedPath, encodedQuery, encodedFragment)

    fun buildString(): String = build().toString()

    companion object {
        fun from(uri: Uri): UriBuilder = UriBuilder(uri)
        fun from(uriString: String): UriBuilder = UriBuilder(uriString)
    }
}

fun Uri.newBuilder(): UriBuilder = UriBuilder(this)
