/*
 * Copyright (C) 2025 Square, Inc.
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
package kmphttp.internal.http

import kmphttp.AsyncSource
import kmphttp.RequestBody
import okio.Buffer
import okio.GzipSink

internal class GzipRequestBody(
    val delegate: RequestBody,
) : RequestBody() {
    override fun contentType() = delegate.contentType()

    // We don't know the compressed length in advance!
    override fun contentLength() = -1L

    override fun openRead(): AsyncSource {
        return delegate.openRead().gzipCompress()
    }

    override fun isOneShot() = delegate.isOneShot()
}


internal fun AsyncSource.gzipCompress(): AsyncSource {
    val delegate = this
    val tempBuffer = Buffer()
    val compressedBuffer = Buffer()
    val gzipSink = GzipSink(compressedBuffer)
    var ended = false
    return object : AsyncSource {
        override suspend fun read(sink: Buffer, byteCount: Long): Long {
            if (compressedBuffer.size >= byteCount || ended) {
                if (!compressedBuffer.exhausted()) return compressedBuffer.read(sink, byteCount)
                return -1
            } else {
                val wantedBufferSize = maxOf(byteCount, 16 * 1024)
                while (compressedBuffer.size < wantedBufferSize) {
                    val bytesRead = delegate.read(tempBuffer, 8 * 1024)
                    if (bytesRead == -1L) {
                        gzipSink.close()
                        ended = true
                        break
                    } else {
                        gzipSink.write(tempBuffer, tempBuffer.size)
                        check(tempBuffer.size == 0L)
                    }
                }
                return when {
                    !compressedBuffer.exhausted() -> compressedBuffer.read(sink, byteCount)
                    ended -> -1L
                    else -> throw IllegalStateException()
                }
            }
        }

        override fun close() {
            delegate.close()
        }
    }
}