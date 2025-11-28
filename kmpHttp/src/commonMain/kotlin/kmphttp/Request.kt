/*
 * Copyright (C) 2013 Square, Inc.
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

/**
 * An HTTP request. Instances of this class are immutable if their [body] is null or itself
 * immutable.
 */
expect class Request {
    val url: HttpUrl

    val method: String

    val headers: Headers

    val body: RequestBody?

    val cacheUrlOverride: HttpUrl?

    val isHttps: Boolean

    fun header(name: String): String?

    fun headers(name: String): List<String>

    fun newBuilder(): RequestBuilder

    /**
     * Returns the cache control directives for this response. This is never null, even if this
     * response contains no `Cache-Control` header.
     */
    val cacheControl: CacheControl

    override fun toString(): String
}

expect open class RequestBuilder {

    constructor()

    open fun url(url: HttpUrl): RequestBuilder

    /**
     * Sets the URL target of this request.
     *
     * @throws IllegalArgumentException if [url] is not a valid HTTP or HTTPS URL. Avoid this
     *     exception by calling [HttpUrl.parse]; it returns null for invalid URLs.
     */
    open fun url(url: String): RequestBuilder


    /**
     * Sets the header named [name] to [value]. If this request already has any headers
     * with that name, they are all replaced.
     */
    open fun header(
        name: String,
        value: String,
    ): RequestBuilder

    /**
     * Adds a header with [name] and [value]. Prefer this method for multiply-valued
     * headers like "Cookie".
     *
     * Note that for some headers including `Content-Length` and `Content-Encoding`,
     * the HTTP client may replace [value] with a header derived from the request body.
     */
    open fun addHeader(
        name: String,
        value: String,
    ): RequestBuilder

    /** Removes all headers named [name] on this builder. */
    open fun removeHeader(name: String): RequestBuilder

    /** Removes all headers on this builder and adds [headers]. */
    open fun headers(headers: Headers): RequestBuilder

    /**
     * Sets this request's `Cache-Control` header, replacing any cache control headers already
     * present. If [cacheControl] doesn't define any directives, this clears this request's
     * cache-control headers.
     */
    open fun cacheControl(cacheControl: CacheControl): RequestBuilder

    open fun get(): RequestBuilder

    open fun head(): RequestBuilder

    open fun post(body: RequestBody): RequestBuilder

    open fun put(body: RequestBody): RequestBuilder

    open fun patch(body: RequestBody): RequestBuilder

    /**
     * Sets this request's method to `QUERY`.
     *
     * By default, `QUERY` requests are not cached. You can use [cacheUrlOverride] to specify
     * how to cache them.
     *
     * A typical use case is to hash the request body:
     *
     * ```kotlin
     *     val hash = body.sha256().hex()
     *     val query = Request
     *         .Builder()
     *         .query(body)
     *         .url("https://example.com/query")
     *         .cacheUrlOverride("https://example.com/query/$hash".toHttpUrl())
     *         .build()
     * ```
     *
     * @see cacheUrlOverride
     */
    open fun query(body: RequestBody): RequestBuilder

    open fun method(
        method: String,
        body: RequestBody?,
    ): RequestBuilder

    /**
     * Override the [Request.url] for caching, if it is either polluted with
     * transient query params, or has a canonical URL possibly for a CDN.
     *
     * Note that POST requests will not be sent to the server if this URL is set
     * and matches a cached response.
     */
    fun cacheUrlOverride(cacheUrlOverride: HttpUrl?): RequestBuilder

    /**
     * Configures this request's body to be compressed when it is transmitted. This also adds the
     * 'Content-Encoding: gzip' header.
     *
     * Only use this method if you have prior knowledge that the receiving server supports
     * gzip-compressed requests.
     *
     * It is an error to call this multiple times on the same instance.
     *
     * @throws IllegalStateException if this request doesn't have a request body, or if it already
     *     has a 'Content-Encoding' header.
     */
    fun gzip(): RequestBuilder

    open fun build(): Request
}

/**
 * Workaround default parameter not allowed in expect/actual functions.
 */
expect fun RequestBuilder.deleteWithBody(body: RequestBody): RequestBuilder
expect fun RequestBuilder.deleteWithoutBody(): RequestBuilder