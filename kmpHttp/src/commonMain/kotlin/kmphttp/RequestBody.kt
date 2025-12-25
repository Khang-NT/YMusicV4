package kmphttp

import okio.Buffer
import okio.BufferedSink
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import okio.FileSystem
import okio.Path
import okio.Source
import okio.Timeout
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

expect abstract class RequestBody {
    /** Returns the Content-Type header for this body. */
    abstract fun contentType(): MediaType?

    /**
     * Returns the number of bytes that will be written to sink in a call to [writeTo],
     * or -1 if that count is unknown.
     */
    open fun contentLength(): Long


    /**
     * Returns true if this body expects at most one call to [writeTo] and can be transmitted
     * at most once. This is typically used when writing the request body is destructive and it is not
     * possible to recreate the request body after it has been sent.
     *
     * This method returns false unless it is overridden by a subclass.
     *
     * By default OkHttp will attempt to retransmit request bodies when the original request fails
     * due to any of:
     *
     *  * A stale connection. The request was made on a reused connection and that reused connection
     *    has since been closed by the server.
     *  * A client timeout (HTTP 408).
     *  * A authorization challenge (HTTP 401 and 407) that is satisfied by the [Authenticator].
     *  * A retryable server failure (HTTP 503 with a `Retry-After: 0` response header).
     *  * A misdirected request (HTTP 421) on a coalesced connection.
     */
    open fun isOneShot(): Boolean

    companion object {
        /** Empty request body with no content-type. */
        val EMPTY: RequestBody
    }
}

/**
 * Request body will be implemented depends on platform
 * Technically we could create RequestBody via these functions
 */
expect object RequestBodyX {
    fun String.toRequestBody(contentType: MediaType? = null): RequestBody
    fun ByteString.toRequestBody(contentType: MediaType? = null): RequestBody


    fun ByteArray.toRequestBody(
        contentType: MediaType? = null,
        offset: Int = 0,
        byteCount: Int = size - offset,
    ): RequestBody

    fun Path.asRequestBody(
        fileSystem: FileSystem,
        contentType: MediaType? = null,
    ): RequestBody

    fun Source.asOneshotRequestBody(
        contentType: MediaType? = null,
        contentLength: Long = -1,
    ): RequestBody

    fun AsyncSource.asOneshotRequestBody(
        contentType: MediaType? = null,
        contentLength: Long = -1,
    ): RequestBody
}