package kmphttp

import kotlinx.coroutines.runBlocking
import okio.BufferedSink
import okio.ByteString
import okio.FileSystem
import okio.Path
import okio.Source
import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.RequestBody.Companion.asRequestBody as okHttpAsRequestBody
import okhttp3.RequestBody.Companion.toRequestBody as okHttpToRequestBody

actual typealias RequestBody = okhttp3.RequestBody

actual object RequestBodyX {
    actual fun String.toRequestBody(contentType: MediaType?) = this.okHttpToRequestBody(contentType)
    actual fun ByteString.toRequestBody(contentType: MediaType?) = this.okHttpToRequestBody(contentType)


    actual fun ByteArray.toRequestBody(
        contentType: MediaType?,
        offset: Int,
        byteCount: Int,
    ) = this.okHttpToRequestBody(contentType, offset, byteCount)

    actual fun Path.asRequestBody(
        fileSystem: FileSystem,
        contentType: MediaType?,
    ) = this.okHttpAsRequestBody(fileSystem, contentType)

    actual fun Source.asOneshotRequestBody(
        contentType: MediaType?,
        contentLength: Long,
    ) = object : RequestBody() {
        override fun contentType(): okhttp3.MediaType? = contentType
        override fun contentLength(): Long = contentLength

        private val opened = AtomicBoolean(false)
        override fun writeTo(sink: BufferedSink) {
            if (opened.compareAndSet(false, true)) {
                sink.writeAll(this@asOneshotRequestBody)
            } else {
                throw IllegalStateException("One-shot request body was consumed")
            }
        }

        override fun isOneShot(): Boolean = true
    }

    actual fun AsyncSource.asOneshotRequestBody(
        contentType: MediaType?,
        contentLength: Long,
    ) = object : RequestBody() {
        override fun contentType(): okhttp3.MediaType? = contentType
        override fun contentLength(): Long = contentLength

        private val opened = AtomicBoolean(false)
        override fun writeTo(sink: BufferedSink) {
            if (opened.compareAndSet(false, true)) {
                runBlocking {
                    sink.writeAll(this@asOneshotRequestBody)
                }
            } else {
                throw IllegalStateException("One-shot request body was consumed")
            }
        }

        override fun isOneShot(): Boolean = true
    }
}
