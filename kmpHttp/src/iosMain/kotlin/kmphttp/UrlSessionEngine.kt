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
package kmphttp

import kmpcommon.LinkedList
import kmpcommon.kilobytes
import kmphttp.MediaTypeX.toMediaTypeOrNull
import kmphttp.internal.AsyncChain
import kmphttp.internal.http.BridgeInterceptor
import kmphttp.internal.http.FollowUpInterceptor
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UnsafeNumber
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okio.Buffer
import okio.IOException
import platform.Foundation.HTTPBodyStream
import platform.Foundation.NSCondition
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSError
import platform.Foundation.NSHTTPCookieAcceptPolicy
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSInputStream
import platform.Foundation.NSLocalizedDescriptionKey
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSRunLoop
import platform.Foundation.NSRunLoopMode
import platform.Foundation.NSStream
import platform.Foundation.NSStreamDelegateProtocol
import platform.Foundation.NSStreamEvent
import platform.Foundation.NSStreamEventEndEncountered
import platform.Foundation.NSStreamEventErrorOccurred
import platform.Foundation.NSStreamEventHasBytesAvailable
import platform.Foundation.NSStreamEventOpenCompleted
import platform.Foundation.NSStreamPropertyKey
import platform.Foundation.NSStreamStatusAtEnd
import platform.Foundation.NSStreamStatusClosed
import platform.Foundation.NSStreamStatusError
import platform.Foundation.NSStreamStatusNotOpen
import platform.Foundation.NSStreamStatusOpen
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.Foundation.NSURLRequestReloadIgnoringLocalCacheData
import platform.Foundation.NSURLResponse
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionDataDelegateProtocol
import platform.Foundation.NSURLSessionDataTask
import platform.Foundation.NSURLSessionResponseAllow
import platform.Foundation.NSURLSessionResponseDisposition
import platform.Foundation.NSURLSessionTask
import platform.Foundation.NSURLSessionTaskDelegateProtocol
import platform.Foundation.NSUnderlyingErrorKey
import platform.Foundation.performInModes
import platform.Foundation.setHTTPBodyStream
import platform.Foundation.setHTTPMethod
import platform.Foundation.setValue
import platform.Foundation.timeIntervalSince1970
import platform.darwin.NSInteger
import platform.darwin.NSObject
import platform.darwin.NSUInteger
import platform.darwin.NSUIntegerVar
import platform.posix.memcpy
import platform.posix.uint8_tVar
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.WeakReference
import kotlin.time.Clock
import kotlin.time.ExperimentalTime


/**
 * iOS HttpEngine implementation using NSURLSession with streaming support.
 *
 * Cookie and redirect handling are disabled - managed by BridgeInterceptor and FollowUpInterceptor.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class UrlSessionEngine : HttpEngine {

    override suspend fun execute(request: Request, options: RequestOptions): Response {
        return AsyncChain(
            interceptors = listOf(FollowUpInterceptor(), BridgeInterceptor(CookieJar.NO_COOKIES)),
            interceptorIndex = 0,
            execute = { request, _ -> executeNetwork(request) },
            request = request, options = options
        ).proceed(request)
    }

    private suspend fun executeNetwork(request: Request): Response {

        val sentRequestAtMillis = currentTimeMillis()

        // Create streaming response handler
        val responseHandler = StreamingResponseHandler()

        // Create session with delegate for streaming response
        val config = NSURLSessionConfiguration.ephemeralSessionConfiguration().apply {
            // Disable automatic cookie handling - managed by BridgeInterceptor
            HTTPCookieAcceptPolicy = NSHTTPCookieAcceptPolicy.NSHTTPCookieAcceptPolicyNever
            HTTPShouldSetCookies = false
        }
        val session = NSURLSession.sessionWithConfiguration(
            configuration = config,
            delegate = responseHandler,
            delegateQueue = null
        )

        val urlRequest = request.toNSURLRequest()
        try {
            // Convert to NSURLRequest with streaming body

            // Create and start the data task
            val task = session.dataTaskWithRequest(urlRequest)
            responseHandler.setTask(task)

            task.resume()

            // Wait for initial response (headers)
            val (httpResponse, receivedResponseAtMillis) = responseHandler.awaitResponse()

            // Build Response with streaming body
            return buildResponse(
                request,
                httpResponse,
                responseHandler,
                sentRequestAtMillis,
                receivedResponseAtMillis
            )
        } catch (e: Throwable) {
            responseHandler.close()
            urlRequest.HTTPBodyStream?.close()
            session.invalidateAndCancel()
            throw e
        }
    }

    private fun Request.toNSURLRequest(): NSMutableURLRequest {
        val url = NSURL(string = this.url.toString())

        val nsRequest = NSMutableURLRequest.requestWithURL(url).apply {
            setHTTPMethod(method)
            setCachePolicy(NSURLRequestReloadIgnoringLocalCacheData)

            // Set headers
            for ((name, value) in headers) {
                setValue(value, forHTTPHeaderField = name)
            }

            // Set streaming body if present
            body?.let { requestBody ->
                if (headers["Content-Type"] == null) {
                    // without content-type set, URLSession sent application/x-www-form-urlencoded as default
                    // should be better with application/octet-stream instead
                    setValue("application/octet-stream", forHTTPHeaderField = "Content-Type")
                }
                val inputStream = AsyncSourceInputStream(requestBody.openRead())
                setHTTPBodyStream(inputStream)
            }
        }

        return nsRequest
    }

    private fun buildResponse(
        request: Request,
        httpResponse: NSHTTPURLResponse,
        responseHandler: StreamingResponseHandler,
        sentRequestAtMillis: Long,
        receivedResponseAtMillis: Long,
    ): Response {
        val code = httpResponse.statusCode.toInt()
        val message = httpStatusMessage(code)
        val protocol = Protocol.HTTP_1_1
        val headers = httpResponse.toHeaders()
        val contentType = headers["Content-Type"]?.toMediaTypeOrNull()
        val contentLength = headers["Content-Length"]?.toLongOrNull() ?: -1L
        val body = StreamingResponseBody(contentType, contentLength, responseHandler)

        return ResponseBuilder()
            .request(request)
            .protocol(protocol)
            .code(code)
            .message(message)
            .headers(headers)
            .body(body)
            .sentRequestAtMillis(sentRequestAtMillis)
            .receivedResponseAtMillis(receivedResponseAtMillis)
            .build()
    }

    private fun NSHTTPURLResponse.toHeaders(): Headers {
        val builder = HeadersBuilder()

        @Suppress("UNCHECKED_CAST")
        val headerFields = allHeaderFields
        for ((key, value) in headerFields) {
            if (key is String && value is String) {
                builder.addUnsafeNonAscii(key, value)
            }
        }
        return builder.build()
    }

    private fun currentTimeMillis(): Long {
        return (NSDate().timeIntervalSince1970 * 1000).toLong()
    }

    private fun httpStatusMessage(code: Int): String = when (code) {
        100 -> "Continue"
        101 -> "Switching Protocols"
        200 -> "OK"
        201 -> "Created"
        202 -> "Accepted"
        204 -> "No Content"
        206 -> "Partial Content"
        301 -> "Moved Permanently"
        302 -> "Found"
        303 -> "See Other"
        304 -> "Not Modified"
        307 -> "Temporary Redirect"
        308 -> "Permanent Redirect"
        400 -> "Bad Request"
        401 -> "Unauthorized"
        403 -> "Forbidden"
        404 -> "Not Found"
        405 -> "Method Not Allowed"
        408 -> "Request Timeout"
        409 -> "Conflict"
        410 -> "Gone"
        413 -> "Payload Too Large"
        414 -> "URI Too Long"
        415 -> "Unsupported Media Type"
        429 -> "Too Many Requests"
        500 -> "Internal Server Error"
        501 -> "Not Implemented"
        502 -> "Bad Gateway"
        503 -> "Service Unavailable"
        504 -> "Gateway Timeout"
        else -> "HTTP $code"
    }
}

/**
 * NSInputStream implementation that reads from AsyncSource.
 * Used to stream request body to NSURLSession.
 */
@OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)
private class AsyncSourceInputStream(
    source: AsyncSource,
) : NSInputStream(NSData()), NSStreamDelegateProtocol {

    private val buffered = Buffer()
    private val bufferedLock = NSCondition()
    private var source: AsyncSource? = source
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val suspendCondition = SuspendCondition()

    private var currentStreamStatus = NSStreamStatusNotOpen
    private var currentStreamError: NSError? = null
    private var closed = false

    private inline fun <R> NSCondition.withLock(block: () -> R): R {
        try {
            lock()
            return block()
        } finally {
            unlock()
        }
    }

    override fun open() {
        currentStreamStatus = NSStreamStatusOpen
        notifyOpened()

        coroutineScope.launch {
            val tempBuffer = Buffer()
            try {
                var sourceEnded = false
                while (true) {
                    check(tempBuffer.size == 0L)
                    val preBufferSize = 16.kilobytes
                    while (buffered.size < preBufferSize && tempBuffer.size < preBufferSize && !sourceEnded) {
                        val bytesRead = source?.read(tempBuffer, preBufferSize) ?: -1L
                        if (bytesRead == -1L) {
                            sourceEnded = true
                            releaseSource()
                        }
                    }
                    val done = bufferedLock.withLock {
                        if (closed) return@launch

                        if (tempBuffer.size > 0) {
                            buffered.write(tempBuffer, tempBuffer.size)
                            check(tempBuffer.size == 0L)
                        }
                        if (buffered.size > 0) {
                            notifyBytesAvailable()
                            suspendCondition.pause()
                            bufferedLock.broadcast()
                            return@withLock false
                        } else {
                            check(sourceEnded)
                            currentStreamStatus = NSStreamStatusAtEnd
                            notifyBytesEnded()
                            bufferedLock.broadcast()
                            return@withLock true
                        }
                    }
                    if (done) {
                        break
                    } else {
                        suspendCondition.await()
                    }
                }
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                bufferedLock.withLock {
                    if (!closed) {
                        currentStreamError = e.toNSError()
                        currentStreamStatus = NSStreamStatusError
                        notifyBytesError()
                    }
                    bufferedLock.broadcast()
                }
            } finally {
                tempBuffer.clear()
                releaseSource()
            }
        }
    }

    private fun notifyOpened() {
        postEvent(NSStreamEventOpenCompleted)
    }

    private fun notifyBytesAvailable() {
        postEvent(NSStreamEventHasBytesAvailable)
    }

    private fun notifyBytesEnded() {
        postEvent(NSStreamEventEndEncountered)
    }

    private fun notifyBytesError() {
        postEvent(NSStreamEventErrorOccurred)
    }

    override fun close() {
        bufferedLock.withLock {
            if (!closed) {
                closed = true
                currentStreamStatus = NSStreamStatusClosed
                coroutineScope.cancel()
                releaseSource()
                suspendCondition.release()
                bufferedLock.broadcast()
            }
        }
    }

    override fun read(buffer: CPointer<uint8_tVar>?, maxLength: NSUInteger): NSInteger {
        if (buffer == null || closed || currentStreamStatus == NSStreamStatusError) {
            return -1
        }

        var bytes: ByteArray? = null
        bufferedLock.withLock {
            while (bytes == null) {
                if (closed || currentStreamStatus == NSStreamStatusError) {
                    return -1
                }
                if (buffered.size > 0L) {
                    val toRead = minOf(buffered.size, maxLength.toLong())
                    bytes = buffered.readByteArray(toRead)
                    suspendCondition.resume()
                } else {
                    if (currentStreamStatus == NSStreamStatusAtEnd) {
                        return 0L // EOF
                    }
                    suspendCondition.resume()
                    bufferedLock.wait() // BLOCKING WAIT
                }
            }
        }

        checkNotNull(bytes)
        bytes.usePinned { pinned ->
            memcpy(buffer, pinned.addressOf(0), bytes.size.toULong())
        }
        return bytes.size.toLong()
    }

    override fun hasBytesAvailable(): Boolean = currentStreamStatus == NSStreamStatusOpen

    override fun streamStatus() = currentStreamStatus

    override fun streamError(): NSError? = currentStreamError

    private fun releaseSource() {
        source?.close()
        source = null
    }

    override fun getBuffer(
        buffer: CPointer<CPointerVar<uint8_tVar>>?,
        length: CPointer<NSUIntegerVar>?
    ): Boolean {
        return false
    }

    override fun propertyForKey(key: NSStreamPropertyKey?): Any? = null
    override fun setProperty(property: Any?, forKey: NSStreamPropertyKey?): Boolean = false

    // WeakReference as delegate should not be retained
    // https://developer.apple.com/documentation/foundation/nsstream/1418423-delegate
    private var _delegate: WeakReference<NSStreamDelegateProtocol>? = null
    private var runLoop: NSRunLoop? = null
    private var runLoopModes = listOf<NSRunLoopMode>()

    private fun postEvent(event: NSStreamEvent) {
        val runLoop = runLoop ?: return
        runLoop.performInModes(runLoopModes) {
            if (runLoop == this.runLoop) {
                delegateOrSelf.stream(this, event)
            }
        }
    }

    override fun delegate() = _delegate?.value

    private val delegateOrSelf get() = delegate ?: this

    override fun setDelegate(delegate: NSStreamDelegateProtocol?) {
        _delegate = delegate?.let { WeakReference(it) }
    }

    override fun stream(aStream: NSStream, handleEvent: NSStreamEvent) {
        // no-op
    }


    override fun scheduleInRunLoop(aRunLoop: NSRunLoop, forMode: NSRunLoopMode) {
        if (runLoop == null) {
            runLoop = aRunLoop
        }
        if (runLoop == aRunLoop) {
            runLoopModes += forMode
        }
        suspendCondition.resume()
    }

    override fun removeFromRunLoop(aRunLoop: NSRunLoop, forMode: NSRunLoopMode) {
        if (aRunLoop == runLoop) {
            runLoopModes -= forMode
            if (runLoopModes.isEmpty()) {
                runLoop = null
            } else {
                suspendCondition.resume()
            }
        }
    }

    override fun description(): String = "$source.inputStream()"
}

@OptIn(UnsafeNumber::class)
internal fun Throwable.toNSError() = NSError(
    domain = "Kotlin",
    code = 0,
    userInfo = mapOf(
        NSLocalizedDescriptionKey to message,
        NSUnderlyingErrorKey to this,
    ),
)

/**
 * Handles streaming response data from NSURLSession.
 */
@OptIn(ExperimentalForeignApi::class)
private class StreamingResponseHandler : NSObject(), NSURLSessionDataDelegateProtocol,
    NSURLSessionTaskDelegateProtocol {

    private val preBufferedSize = 64.kilobytes
    private val hotBuffer = Buffer()
    private val hotBufferGuard = SynchronizedObject()
    private val suspendCondition = SuspendCondition()

    private lateinit var task: NSURLSessionDataTask
    private var httpResponse = CompletableDeferred<Pair<NSHTTPURLResponse, Long>>()
    private var completed = false
    private var completeError: IOException? = null
    private var closed = false

    fun setTask(task: NSURLSessionDataTask) {
        this.task = task
    }

    suspend fun awaitResponse(): Pair<NSHTTPURLResponse, Long> {
        return httpResponse.await()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun read(sink: Buffer, byteCount: Long): Long {
        // Check for error
        if (!httpResponse.isCompleted) throw IllegalStateException("Response not ready")
        httpResponse.getCompletionExceptionOrNull()?.let { throw it }

        while (true) {
            synchronized(hotBufferGuard) {
                completeError?.let { throw it }
                if (closed) return -1L

                // If buffer has data, return it
                if (!hotBuffer.exhausted()) {
                    val toRead = minOf(hotBuffer.size, byteCount)
                    sink.write(hotBuffer, toRead)
                    return toRead
                }
                if (completed) return -1
                task.resume()
                suspendCondition.pause()
            }
            suspendCondition.await()
        }
    }


    fun close() {
        synchronized(hotBufferGuard) {
            task.cancel()
            closed = true
            completed = true
            hotBuffer.clear()
        }
        suspendCondition.release()
    }

    // NSURLSessionDataDelegate - received response headers
    override fun URLSession(
        session: NSURLSession,
        dataTask: NSURLSessionDataTask,
        didReceiveResponse: NSURLResponse,
        completionHandler: (NSURLSessionResponseDisposition) -> Unit
    ) {
        httpResponse.complete((didReceiveResponse as NSHTTPURLResponse) to currentTimeMillis())
        completionHandler(NSURLSessionResponseAllow)
    }

    override fun URLSession(
        session: NSURLSession,
        dataTask: NSURLSessionDataTask,
        didReceiveData: NSData
    ) {
        synchronized(hotBufferGuard) {
            if (closed) return
            val bytes = didReceiveData.toByteArray()
            hotBuffer.write(bytes)
            if (hotBuffer.size >= preBufferedSize) {
                task.suspend()
            }
        }
        suspendCondition.resume()
    }

    override fun URLSession(
        session: NSURLSession,
        task: NSURLSessionTask,
        didCompleteWithError: NSError?
    ) {
        onComplete(didCompleteWithError)
    }

    override fun URLSession(session: NSURLSession, didBecomeInvalidWithError: NSError?) {
        onComplete(didBecomeInvalidWithError)
    }

    private fun onComplete(error: NSError?) {
        synchronized(hotBufferGuard) {
            completed = true
            if (error != null) {
                completeError =
                    IOException("NSURLSession error: ${error.localizedDescription}")

            }
            suspendCondition.release()
        }
        completeError?.let {
            // call completeExceptionally when httpResponse already completed has no effect
            // so no need to check
            httpResponse.completeExceptionally(it)
        }
    }

    override fun URLSession(
        session: NSURLSession,
        task: NSURLSessionTask,
        willPerformHTTPRedirection: NSHTTPURLResponse,
        newRequest: NSURLRequest,
        completionHandler: (NSURLRequest?) -> Unit
    ) {
        completionHandler(null) // don't redirect
    }

    private fun NSData.toByteArray(): ByteArray {
        val length = this.length.toInt()
        if (length == 0) return ByteArray(0)
        val bytes = ByteArray(length)
        bytes.usePinned { pinned ->
            memcpy(pinned.addressOf(0), this.bytes, length.toULong())
        }
        return bytes
    }

    @OptIn(ExperimentalTime::class)
    private fun currentTimeMillis(): Long {
        return Clock.System.now().toEpochMilliseconds()
    }
}

/**
 * Streaming ResponseBody that reads from StreamingResponseHandler.
 */
private class StreamingResponseBody(
    private val contentType: MediaType?,
    private val contentLength: Long,
    private val handler: StreamingResponseHandler,
) : ResponseBody() {

    private val source: AsyncSource = object : AsyncSource {
        override suspend fun read(sink: Buffer, byteCount: Long): Long {
            return handler.read(sink, byteCount)
        }

        override fun close() {
            handler.close()
        }
    }

    override fun contentType(): MediaType? = contentType

    override fun contentLength(): Long = contentLength

    override fun asyncSource(): AsyncSource {
        return source
    }
}

@OptIn(ExperimentalAtomicApi::class)
class SuspendCondition {
    private val waiters = LinkedList<CancellableContinuation<Unit>>()
    private val waitersLock = SynchronizedObject()
    private val isReleased = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)

    suspend fun await() {
        if (isReleased.load() || !isPaused.load()) return

        suspendCancellableCoroutine { continuation ->
            synchronized(waitersLock) {
                if (isReleased.load() || !isPaused.load()) {
                    continuation.resume(Unit) { _, _, _ -> }
                    return@suspendCancellableCoroutine
                }
                waiters.add(continuation)
            }
            continuation.invokeOnCancellation {
                synchronized(waitersLock) {
                    waiters.remove(continuation)
                }
            }
        }
    }

    fun pause() {
        isPaused.exchange(true)
    }

    fun resume() {
        if (isPaused.compareAndSet(true, false)) {
            synchronized(waitersLock) {
                waiters.forEach { it.resume(Unit) { _, _, _ -> } }
                waiters.clear()
            }
        }
    }

    fun release() {
        if (isReleased.compareAndSet(false, true)) {
            synchronized(waitersLock) {
                waiters.forEach { it.resume(Unit) { _, _, _ -> } }
                waiters.clear()
            }
        }
    }

    fun isReleased() = isReleased.load()
}