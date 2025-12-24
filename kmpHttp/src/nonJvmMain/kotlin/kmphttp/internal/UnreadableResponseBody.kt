package kmphttp.internal

import kmphttp.AsyncSource
import kmphttp.MediaType
import kmphttp.Response
import kmphttp.ResponseBody
import okio.Buffer
import okio.Source
import okio.Timeout


internal class UnreadableResponseBody(
    val contentType: MediaType?,
    val contentLength: Long,
) : ResponseBody(), Source {

    override fun contentLength(): Long = contentLength
    override fun contentType(): MediaType? = contentType

    override fun asyncSource(): AsyncSource = AsyncSource.EMPTY

    override fun read(
        sink: Buffer,
        byteCount: Long,
    ): Long =
        throw IllegalStateException(
            """
      |Unreadable ResponseBody! These Response objects have bodies that are stripped:
      | * Response.cacheResponse
      | * Response.networkResponse
      | * Response.priorResponse
      | * EventSourceListener
      | * WebSocketListener
      |(It is safe to call contentType() and contentLength() on these response bodies.)
      """.trimMargin(),
        )

    override fun timeout() = Timeout.NONE

    override fun close() {
    }
}

fun Response.stripBody(): Response =
    newBuilder()
        .body(UnreadableResponseBody(body.contentType(), body.contentLength()))
        .build()