package kmphttp

import okio.ByteString
import okio.Closeable

/**
 * A one-shot stream from the origin server to the client application with the raw bytes of the
 * response body. Each response body is supported by an active connection to the webserver. This
 * imposes both obligations and limits on the client application.
 *
 * ### The response body must be closed.
 *
 * Each response body is backed by a limited resource like a socket (live network responses) or
 * an open file (for cached responses). Failing to close the response body will leak resources and
 * may ultimately cause the application to slow down or crash.
 *
 * Both this class and [Response] implement [Closeable]. Closing a response simply
 * closes its response body. If you invoke [Call.execute] or implement [Callback.onResponse] you
 * must close this body by calling any of the following methods:
 *
 * * `Response.close()`
 * * `Response.body().close()`
 * * `Response.body().source().close()`
 * * `Response.body().charStream().close()`
 * * `Response.body().byteStream().close()`
 * * `Response.body().bytes()`
 * * `Response.body().string()`
 *
 * There is no benefit to invoking multiple `close()` methods for the same response body.
 *
 * For synchronous calls, the easiest way to make sure a response body is closed is with a `try`
 * block. With this structure the compiler inserts an implicit `finally` clause that calls
 * [close()][Response.close] for you.
 *
 * ```java
 * Call call = client.newCall(request);
 * try (Response response = call.execute()) {
 * ... // Use the response.
 * }
 * ```
 *
 * You can use a similar block for asynchronous calls:
 *
 * ```java
 * Call call = client.newCall(request);
 * call.enqueue(new Callback() {
 *   public void onResponse(Call call, Response response) throws IOException {
 *     try (ResponseBody responseBody = response.body()) {
 *     ... // Use the response.
 *     }
 *   }
 *
 *   public void onFailure(Call call, IOException e) {
 *   ... // Handle the failure.
 *   }
 * });
 * ```
 *
 * These examples will not work if you're consuming the response body on another thread. In such
 * cases the consuming thread must call [close] when it has finished reading the response
 * body.
 *
 * ### The response body can be consumed only once.
 *
 * This class may be used to stream very large responses. For example, it is possible to use this
 * class to read a response that is larger than the entire memory allocated to the current process.
 * It can even stream a response larger than the total storage on the current device, which is a
 * common requirement for video streaming applications.
 *
 * Because this class does not buffer the full response in memory, the application may not
 * re-read the bytes of the response. Use this one shot to read the entire response into memory with
 * [bytes] or [string]. Or stream the response with either [source], [byteStream], or [charStream].
 */
expect abstract class ResponseBody : Closeable {
    abstract fun contentType(): MediaType?

    /**
     * Returns the number of bytes in that will returned by [bytes], or [byteStream], or -1 if
     * unknown.
     */
    abstract fun contentLength(): Long

    override fun close()

    companion object {
        /** Empty response body with no content-type. Closing this response body does nothing. */
        val EMPTY: ResponseBody
    }
}

expect fun ResponseBody.asyncSource(): AsyncSource

/**
 * Load entire response body into memory, stored in ByteString.
 */
expect suspend fun ResponseBody.byteString(): ByteString

/**
 * Load entire response body into memory and decode it as utf8 string.
 */
expect suspend fun ResponseBody.utf8String(): String
