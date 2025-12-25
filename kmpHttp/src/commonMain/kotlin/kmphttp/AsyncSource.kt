package kmphttp

import okio.Buffer
import okio.BufferedSink
import okio.Closeable

interface AsyncSource : Closeable {
    /**
     * This function may perform either blocking or non-blocking read, so the caller should not be
     * main thread.
     *
     * @return the number of bytes read, or -1 if this source is ended.
     */
    suspend fun read(sink: Buffer, byteCount: Long): Long

    companion object {
        val EMPTY = object : AsyncSource {
            override suspend fun read(sink: Buffer, byteCount: Long): Long = -1
            override fun close() = Unit
        }
    }
}

suspend fun BufferedSink.writeAll(source: AsyncSource): Long {
    var totalRead = 0L
    while (true) {
        // Okio segment SIZE
        // const val SIZE = 8192
        val readCount = source.read(this.buffer, 8192)
        if (readCount == -1L) break
        totalRead += readCount
    }
    return totalRead
}
