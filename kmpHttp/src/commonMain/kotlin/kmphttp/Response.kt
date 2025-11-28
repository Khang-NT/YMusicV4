package kmphttp

import okio.Closeable

/**
 * An HTTP response. Instances of this class are not immutable: the response body is a one-shot
 * value that may be consumed only once and then closed. All other properties are immutable.
 *
 * This class implements [Closeable]. Closing it simply closes its response body. See
 * [ResponseBody] for an explanation and examples.
 */
expect class Response : Closeable {
    /**
     * The request that initiated this HTTP response. This is not necessarily the same request issued
     * by the application:
     *
     * * It may be transformed by the user's interceptors. For example, an application interceptor
     *   may add headers like `User-Agent`.
     * * It may be the request generated in response to an HTTP redirect or authentication
     *   challenge. In this case the request URL may be different than the initial request URL.
     *
     * Use the `request` of the [networkResponse] field to get the wire-level request that was
     * transmitted. In the case of follow-ups and redirects, also look at the `request` of the
     * [priorResponse] objects, which have its own [priorResponse].
     */
    val request: Request

    /** Returns the HTTP protocol, such as [Protocol.HTTP_1_1] or [Protocol.HTTP_1_0]. */
    val protocol: Protocol

    /** Returns the HTTP status message. */
    val message: String

    /** Returns the HTTP status code. */
    val code: Int

    /** Returns the HTTP headers. */
    val headers: Headers

    /**
     * Returns a non-null stream with the server's response. The returned value must be
     * [closed][ResponseBody] and may be consumed only once.
     *
     * If this is a [cacheResponse], [networkResponse], or [priorResponse], the server's response body
     * is not available, and it is always an error to attempt read its streamed content. Reading from
     * [ResponseBody.source] always throws on such instances.
     *
     * It is safe and supported to call [ResponseBody.contentType] and [ResponseBody.contentLength] on
     * all instances of [ResponseBody].
     */
    val body: ResponseBody

    /**
     * Returns the raw response received from the network. Will be null if this response didn't use
     * the network, such as when the response is fully cached. The body of the returned response
     * should not be read.
     */
    val networkResponse: Response?

    /**
     * Returns the raw response received from the cache. Will be null if this response didn't use
     * the cache. For conditional get requests the cache response and network response may both be
     * non-null. The body of the returned response should not be read.
     */
    val cacheResponse: Response?
    /**
     * Returns the response for the HTTP redirect or authorization challenge that triggered this
     * response, or null if this response wasn't triggered by an automatic retry. The body of the
     * returned response should not be read because it has already been consumed by the redirecting
     * client.
     */
    val priorResponse: Response?
    /**
     * Returns a timestamp (epoch milliseconds) taken immediately before the initiating request was
     * transmitted over the network. If this response is being served from the cache then this is the
     * timestamp of the original request.
     */
    val sentRequestAtMillis: Long

    /**
     * Returns a timestamp (epoch milliseconds) taken immediately after this response's headers were
     * received from the network. If this response is being served from the cache then this is the
     * timestamp of the original response.
     */
    val receivedResponseAtMillis: Long

    /**
     * Returns true if the code is in [200..300), which means the request was successfully received,
     * understood, and accepted.
     */
    val isSuccessful: Boolean

    fun newBuilder(): ResponseBuilder

    /** Returns true if this response redirects to another resource. */
    val isRedirect: Boolean

    /**
     * Returns the cache control directives for this response. This is never null, even if this
     * response contains no `Cache-Control` header.
     */
    val cacheControl: CacheControl

    /**
     * Closes the response body. Equivalent to `body.close()`.
     *
     * It is safe to close a response that is not eligible for a body. This includes the responses
     * returned from [cacheResponse], [networkResponse], and [priorResponse].
     */
    override fun close()

    override fun toString(): String
}


fun Response.headers(name: String): List<String> = headers.values(name)
fun Response.header(name: String, defaultValue: String? = null): String? = headers[name] ?: defaultValue

expect open class ResponseBuilder {
    constructor()

    open fun request(request: Request): ResponseBuilder

    open fun protocol(protocol: Protocol): ResponseBuilder

    open fun code(code: Int): ResponseBuilder

    open fun message(message: String): ResponseBuilder

    /**
     * Sets the header named [name] to [value]. If this request already has any headers
     * with that name, they are all replaced.
     */
    open fun header(
        name: String,
        value: String,
    ): ResponseBuilder

    /**
     * Adds a header with [name] to [value]. Prefer this method for multiply-valued
     * headers like "Set-Cookie".
     */
    open fun addHeader(
        name: String,
        value: String,
    ): ResponseBuilder

    /** Removes all headers named [name] on this builder. */
    open fun removeHeader(name: String): ResponseBuilder

    /** Removes all headers on this builder and adds [headers]. */
    open fun headers(headers: Headers): ResponseBuilder

    open fun body(body: ResponseBody): ResponseBuilder

    open fun networkResponse(networkResponse: Response?): ResponseBuilder
    open fun cacheResponse(cacheResponse: Response?): ResponseBuilder
    open fun priorResponse(priorResponse: Response?): ResponseBuilder
    open fun sentRequestAtMillis(sentRequestAtMillis: Long): ResponseBuilder
    open fun receivedResponseAtMillis(receivedResponseAtMillis: Long): ResponseBuilder
    open fun build(): Response
}