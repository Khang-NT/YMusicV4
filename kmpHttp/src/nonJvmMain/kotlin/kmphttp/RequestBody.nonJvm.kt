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

import kmphttp.internal.checkOffsetAndCount
import okio.BufferedSink
import okio.ByteString
import okio.FileSystem
import okio.HashingSink
import okio.Path
import okio.blackholeSink
import okio.buffer
import okio.use

actual abstract class RequestBody {
  actual abstract fun contentType(): MediaType?

  actual open fun contentLength(): Long = -1L

  actual abstract fun writeTo(sink: BufferedSink)

  actual open fun isDuplex(): Boolean = false

  actual open fun isOneShot(): Boolean = false

  actual fun sha256(): ByteString {
    val hashingSink = HashingSink.sha256(blackholeSink())
    hashingSink.buffer().use {
      this.writeTo(it)
    }
    return hashingSink.hash
  }

  actual companion object {
    /** Empty request body with no content-type. */
    actual val EMPTY: RequestBody = ByteString.EMPTY.toRequestBody()

    fun ByteString.toRequestBody(contentType: MediaType? = null): RequestBody =
      object : RequestBody() {
        override fun contentType() = contentType

        override fun contentLength() = size.toLong()

        override fun writeTo(sink: BufferedSink) {
          sink.write(this@toRequestBody)
        }
      }

    /** Returns a new request body that transmits this. */
    fun ByteArray.toRequestBody(
      contentType: MediaType? = null,
      offset: Int = 0,
      byteCount: Int = size,
    ): RequestBody {
      checkOffsetAndCount(size.toLong(), offset.toLong(), byteCount.toLong())
      return object : RequestBody() {
        override fun contentType() = contentType

        override fun contentLength() = byteCount.toLong()

        override fun writeTo(sink: BufferedSink) {
          sink.write(this@toRequestBody, offset, byteCount)
        }
      }
    }

    fun Path.asRequestBody(
      fileSystem: FileSystem,
      contentType: MediaType? = null,
    ): RequestBody =
      object : RequestBody() {
        override fun contentType() = contentType

        override fun contentLength() = fileSystem.metadata(this@asRequestBody).size ?: -1

        override fun writeTo(sink: BufferedSink) {
          fileSystem.source(this@asRequestBody).use { source -> sink.writeAll(source) }
        }
      }
  }
}
