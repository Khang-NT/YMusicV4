/*
 * Copyright (C) 2014 Square, Inc.
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

import kmphttp.internal.closeQuietly
import okio.Buffer
import okio.BufferedSource
import okio.ByteString
import okio.Closeable
import okio.IOException
import okio.use

actual abstract class ResponseBody : Closeable {

  actual abstract fun contentType(): MediaType?

  actual abstract fun contentLength(): Long

  actual abstract fun source(): BufferedSource

  actual fun bytes() = consumeSource(BufferedSource::readByteArray) { it.size }

  actual fun byteString() = consumeSource(BufferedSource::readByteString) { it.size }

  private inline fun <T : Any> ResponseBody.consumeSource(
    consumer: (BufferedSource) -> T,
    sizeMapper: (T) -> Int,
  ): T {
    val contentLength = contentLength()
    if (contentLength > Int.MAX_VALUE) {
      throw IOException("Cannot buffer entire body for content length: $contentLength")
    }

    val bytes = source().use(consumer)
    val size = sizeMapper(bytes)
    if (contentLength != -1L && contentLength != size.toLong()) {
      throw IOException("Content-Length ($contentLength) and stream length ($size) disagree")
    }
    return bytes
  }

  actual override fun close(): Unit = source().closeQuietly()

  actual companion object {
    actual val EMPTY: ResponseBody = ByteString.EMPTY.toResponseBody()

    fun ByteArray.toResponseBody(contentType: MediaType? = null): ResponseBody =
      Buffer()
        .write(this)
        .asResponseBody(contentType, size.toLong())

    fun ByteString.toResponseBody(contentType: MediaType? = null): ResponseBody =
      Buffer()
        .write(this)
        .asResponseBody(contentType, size.toLong())

    fun BufferedSource.asResponseBody(
      contentType: MediaType? = null,
      contentLength: Long = -1L,
    ): ResponseBody =
      object : ResponseBody() {
        override fun contentType(): MediaType? = contentType

        override fun contentLength(): Long = contentLength

        override fun source(): BufferedSource = this@asResponseBody
      }
  }
}
