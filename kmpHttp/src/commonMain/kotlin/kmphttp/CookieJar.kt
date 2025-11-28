package kmphttp

/**
 * Provides policy and persistence for HTTP cookies.
 * Inspired by OkHttp's CookieJar.
 *
 * As a policy, implementations of this interface are responsible for:
 * - Selecting which cookies to accept
 * - Selecting which cookies to send
 *
 * As a persistence mechanism, implementations of this interface may:
 * - Store cookies in memory
 * - Store cookies on the file system
 * - Store cookies in a database
 */
interface CookieJar {
    /**
     * Saves cookies from an HTTP response to this jar according to this jar's policy.
     *
     * Note that this method may be called a second time for a single HTTP response if the response
     * includes a trailer. For this obscure HTTP feature, [cookies] contains only the trailer's
     * cookies.
     */
    suspend fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>)

    /**
     * Load cookies from this jar for an HTTP request to [url].
     * This method returns a possibly empty list of cookies for the network request.
     *
     * Simple implementations will return the accepted cookies that have not yet expired and that
     * match [url].
     */
    suspend fun loadForRequest(url: HttpUrl): List<Cookie>

    companion object {
        /**
         * A cookie jar that never accepts any cookies.
         */
        val NO_COOKIES: CookieJar = object : CookieJar {
            override suspend fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                // Reject all cookies
            }

            override suspend fun loadForRequest(url: HttpUrl): List<Cookie> {
                return emptyList()
            }
        }
    }
}
