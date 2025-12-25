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

import kmphttp.RequestBodyX.toRequestBody
import okio.Buffer
import okio.ByteString
import okio.FileSystem
import okio.Path
import okio.Source
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

actual abstract class RequestBody {
    actual abstract fun contentType(): MediaType?

    actual open fun contentLength(): Long = -1L

    actual open fun isOneShot(): Boolean = false

    abstract fun openRead(): AsyncSource

    actual companion object {
        /** Empty request body with no content-type. */
        actual val EMPTY: RequestBody = ByteString.EMPTY.toRequestBody()
    }
}

@OptIn(ExperimentalAtomicApi::class)
actual object RequestBodyX {
    actual fun String.toRequestBody(contentType: MediaType?)
        = encodeToByteArray().toRequestBody(contentType)

    actual fun ByteString.toRequestBody(contentType: MediaType?) = object : RequestBody() {
        override fun contentType(): MediaType? = contentType
        override fun contentLength(): Long = size.toLong()

        override fun openRead(): AsyncSource {
            var currentOffset = 0
            return object : AsyncSource {
                override suspend fun read(sink: Buffer, byteCount: Long): Long {
                    if (currentOffset == size) return -1
                    val maxRead = minOf(byteCount.toInt(), size - currentOffset)
                    sink.write(this@toRequestBody, currentOffset, maxRead)
                    currentOffset += maxRead
                    return maxRead.toLong()
                }

                override fun close() {
                    currentOffset = size
                }
            }
        }
    }


    actual fun ByteArray.toRequestBody(
        contentType: MediaType?,
        offset: Int,
        byteCount: Int,
    ) = object : RequestBody() {
        override fun contentType(): MediaType? = contentType
        override fun contentLength(): Long = byteCount.toLong()

        override fun openRead(): AsyncSource {
            val endExclusive = offset + byteCount
            check(endExclusive <= size)

            var currentOffset = offset
            return object : AsyncSource {
                override suspend fun read(sink: Buffer, byteCount: Long): Long {
                    if (currentOffset == endExclusive) return -1
                    val maxRead = minOf(byteCount.toInt(), endExclusive - currentOffset)
                    sink.write(this@toRequestBody, currentOffset, maxRead)
                    currentOffset += maxRead
                    return maxRead.toLong()
                }

                override fun close() {
                    currentOffset = endExclusive
                }
            }
        }
    }

    actual fun Path.asRequestBody(
        fileSystem: FileSystem,
        contentType: MediaType?,
    ) = object : RequestBody() {
        override fun contentType(): MediaType? = contentType
        override fun contentLength(): Long = fileSystem.metadata(this@asRequestBody).size ?: -1

        override fun openRead(): AsyncSource {
            val source = fileSystem.source(this@asRequestBody)
            return object : AsyncSource {
                override suspend fun read(sink: Buffer, byteCount: Long): Long {
                    return source.read(sink, byteCount)
                }

                override fun close() {
                    source.close()
                }
            }
        }
    }

    actual fun Source.asOneshotRequestBody(
        contentType: MediaType?,
        contentLength: Long,
    ) = object : AsyncSource {
        override suspend fun read(sink: Buffer, byteCount: Long): Long {
            return this@asOneshotRequestBody.read(sink, byteCount)
        }

        override fun close() {
            this@asOneshotRequestBody.close()
        }
    }.asOneshotRequestBody(contentType, contentLength)

    actual fun AsyncSource.asOneshotRequestBody(
        contentType: MediaType?,
        contentLength: Long,
    ) = object : RequestBody() {
        override fun contentType(): MediaType? = contentType
        override fun contentLength(): Long = contentLength

        private val opened = AtomicBoolean(false)
        override fun openRead(): AsyncSource {
            if (opened.compareAndSet(false, true)) {
                return this@asOneshotRequestBody
            } else {
                throw IllegalStateException("One-short request body was consumed.")
            }
        }

        override fun isOneShot(): Boolean = true
    }
}