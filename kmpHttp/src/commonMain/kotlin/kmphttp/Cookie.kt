package kmphttp

/**
 * An [RFC 6265](http://tools.ietf.org/html/rfc6265) Cookie.
 *
 * This class doesn't support additional attributes on cookies, like
 * [Chromium's Priority=HIGH extension][chromium_extension].
 *
 * [chromium_extension]: https://code.google.com/p/chromium/issues/detail?id=232693
 */
expect class Cookie {
    /** Returns a non-empty string with this cookie's name. */
    val name: String

    /** Returns a possibly-empty string with this cookie's value. */
    val value: String

    /**
     * Returns the time that this cookie expires, in epoch milliseconds. This is December 31, 9999 if
     * the cookie is not [persistent], in which case it will expire at the end of the current session.
     *
     * This may return a value less than the current time, in which case the cookie is already
     * expired. Webservers may return expired cookies as a mechanism to delete previously set cookies
     * that may or may not themselves be expired.
     */
    val expiresAt: Long

    /**
     * Returns the cookie's domain. If [hostOnly] returns true this is the only domain that matches
     * this cookie; otherwise it matches this domain and all subdomains.
     */
    val domain: String

    /**
     * Returns this cookie's path. This cookie matches URLs prefixed with path segments that match
     * this path's segments. For example, if this path is `/foo` this cookie matches requests to
     * `/foo` and `/foo/bar`, but not `/` or `/football`.
     */
    val path: String

    /** Returns true if this cookie should be limited to only HTTPS requests. */
    val secure: Boolean

    /**
     * Returns true if this cookie should be limited to only HTTP APIs. In web browsers this prevents
     * the cookie from being accessible to scripts.
     */
    val httpOnly: Boolean

    /**
     * Returns true if this cookie does not expire at the end of the current session.
     *
     * This is true if either 'expires' or 'max-age' is present.
     */
    val persistent: Boolean

    /**
     * Returns true if this cookie's domain should be interpreted as a single host name, or false if
     * it should be interpreted as a pattern. This flag will be false if its `Set-Cookie` header
     * included a `domain` attribute.
     *
     * For example, suppose the cookie's domain is `example.com`. If this flag is true it matches
     * **only** `example.com`. If this flag is false it matches `example.com` and all subdomains
     * including `api.example.com`, `www.example.com`, and `beta.api.example.com`.
     *
     * This is true unless 'domain' is present.
     */
    val hostOnly: Boolean

    /**
     * Returns a string describing whether this cookie is sent for cross-site calls.
     *
     * Two URLs are on the same site if they share a top private domain.
     * Otherwise, they are cross-site URLs.
     *
     * When a URL is requested, it may be in the context of another URL.
     *
     *  * **Embedded resources like images and iframes** in browsers use the context as the page in
     *    the address bar and the subject is the URL of an embedded resource.
     *
     *  * **Potentially-destructive navigations such as HTTP POST calls** use the context as the page
     *    originating the navigation, and the subject is the page being navigated to.
     *
     * The values of this attribute determine whether this cookie is sent for cross-site calls:
     *
     *  - "Strict": the cookie is omitted when the subject URL is an embedded resource or a
     *    potentially-destructive navigation.
     *
     *  - "Lax": the cookie is omitted when the subject URL is an embedded resource. It is sent for
     *    potentially-destructive navigation. This is the default value.
     *
     *  - "None": the cookie is always sent. The "Secure" attribute must also be set when setting this
     *    value.
     */
    val sameSite: String?

    /**
     * Returns true if this cookie should be included on a request to [url]. In addition to this
     * check callers should also confirm that this cookie has not expired.
     */
    fun matches(url: HttpUrl): Boolean

    override fun equals(other: Any?): Boolean

    override fun hashCode(): Int

    override fun toString(): String

    fun newBuilder(): CookieBuilder
}

/**
 * Attempt to parse a `Set-Cookie` HTTP header value [setCookie] as a cookie. Returns null if
 * [setCookie] is not a well-formed cookie.
 */
expect suspend fun parseCookie(
    url: HttpUrl,
    setCookie: String,
): Cookie?

/** Returns all of the cookies from a set of HTTP response headers. */
expect suspend fun parseAllCookies(
    url: HttpUrl,
    headers: Headers,
): List<Cookie>

/**
 * Builds a cookie. The [name], [value], and [domain] values must all be set before calling
 * [build].
 */
expect class CookieBuilder {
    constructor()
    fun name(name: String): CookieBuilder

    fun value(value: String): CookieBuilder

    fun expiresAt(expiresAt: Long): CookieBuilder

    /**
     * Set the domain pattern for this cookie. The cookie will match [domain] and all of its
     * subdomains.
     */
    fun domain(domain: String): CookieBuilder

    /**
     * Set the host-only domain for this cookie. The cookie will match [domain] but none of
     * its subdomains.
     */
    fun hostOnlyDomain(domain: String): CookieBuilder


    fun path(path: String): CookieBuilder

    fun secure(): CookieBuilder

    fun httpOnly(): CookieBuilder

    fun sameSite(sameSite: String): CookieBuilder

    fun build(): Cookie
}