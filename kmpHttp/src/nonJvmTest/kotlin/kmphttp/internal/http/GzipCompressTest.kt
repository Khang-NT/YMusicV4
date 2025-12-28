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
import kotlinx.coroutines.runBlocking
import okio.Buffer
import okio.ByteString.Companion.encodeUtf8
import okio.GzipSink
import okio.GzipSource
import okio.buffer
import okio.use
import kotlin.test.Test
import kotlin.test.assertEquals

class GzipCompressTest {

    @Test
    fun compressEmptySource() = runBlocking {
        verifyCompression("")
    }

    @Test
    fun compressSmallData() = runBlocking {
        verifyCompression("Hello, World!")
    }

    @Test
    fun compressLargeData() = runBlocking {
        // Create data larger than internal buffer size (16KB)
        verifyCompression("x".repeat(32 * 1024))
    }

    @Test
    fun compressMultipleReads() = runBlocking {
        val data = "Hello, World! This is a test of gzip compression."
        val source = bufferAsyncSource(Buffer().write(data.encodeUtf8()))
        val compressed = source.gzipCompress()

        // Read in small chunks
        val result = Buffer()
        while (true) {
            val read = compressed.read(result, 5)
            if (read == -1L) break
        }

        val decompressed = decompress(result)
        assertEquals(data, decompressed.readUtf8())
    }

    @Test
    fun compressReturnsMinusOneAfterExhausted() = runBlocking {
        val data = "test"
        val source = bufferAsyncSource(Buffer().write(data.encodeUtf8()))
        val compressed = source.gzipCompress()

        readAll(compressed)

        // Subsequent reads should return -1
        val sink = Buffer()
        assertEquals(-1L, compressed.read(sink, 100))
        assertEquals(-1L, compressed.read(sink, 100))
    }

    @Test
    fun matchesOkioGzipSink() = runBlocking {
        val testCases = listOf(
            "",
            "Hello",
            "Hello, World! ".repeat(100),
            "x".repeat(32 * 1024),
            "Mixed content 123 !@# with special chars: ñ, 日本語",
        )

        for (data in testCases) {
            val asyncResult = compressWithAsyncSource(data)
            val okioResult = compressWithOkioGzipSink(data)
            assertEquals(okioResult.readByteString(), asyncResult.readByteString(), "Compressed output differs for: ${data.take(20)}...")
        }
    }

    private suspend fun verifyCompression(data: String) {
        val asyncResult = compressWithAsyncSource(data)
        val okioResult = compressWithOkioGzipSink(data)

        // Verify decompression works
        val decompressed = decompress(asyncResult)
        assertEquals(data, decompressed.readUtf8())

        // Verify compressed bytes match okio's GzipSink
        val asyncResultAgain = compressWithAsyncSource(data)
        assertEquals(okioResult.readByteString(), asyncResultAgain.readByteString())
    }

    private suspend fun compressWithAsyncSource(data: String): Buffer {
        val source = bufferAsyncSource(Buffer().write(data.encodeUtf8()))
        val compressed = source.gzipCompress()
        return readAll(compressed)
    }

    private fun compressWithOkioGzipSink(data: String): Buffer {
        val input = Buffer().write(data.encodeUtf8())
        val output = Buffer()
        GzipSink(output).buffer().use { it.writeAll(input) }
        return output
    }

    private fun bufferAsyncSource(buffer: Buffer): AsyncSource {
        return object : AsyncSource {
            override suspend fun read(sink: Buffer, byteCount: Long): Long {
                if (buffer.exhausted()) return -1
                return buffer.read(sink, byteCount)
            }

            override fun close() {
                buffer.clear()
            }
        }
    }

    private suspend fun readAll(source: AsyncSource): Buffer {
        val result = Buffer()
        while (true) {
            val read = source.read(result, 8192)
            if (read == -1L) break
        }
        return result
    }

    private fun decompress(compressed: Buffer): Buffer {
        val result = Buffer()
        GzipSource(compressed).use { gzip ->
            result.writeAll(gzip)
        }
        return result
    }
}
