/*
 * Copyright (C) 2013 Square, Inc.
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

import kmphttp.HttpStatus.HTTP_MOVED_PERM
import kmphttp.HttpStatus.HTTP_MOVED_TEMP
import kmphttp.HttpStatus.HTTP_MULT_CHOICE
import kmphttp.HttpStatus.HTTP_PERM_REDIRECT
import kmphttp.HttpStatus.HTTP_SEE_OTHER
import kmphttp.HttpStatus.HTTP_TEMP_REDIRECT
import okio.Closeable

actual class Response internal constructor(
    actual val request: Request,
    actual val protocol: Protocol,
    actual val message: String,
    actual val code: Int,
    actual val headers: Headers,
    actual val body: ResponseBody,
    actual val networkResponse: Response?,
    actual val cacheResponse: Response?,
    actual val priorResponse: Response?,
    actual val sentRequestAtMillis: Long,
    actual val receivedResponseAtMillis: Long,
) : Closeable {

    actual val isSuccessful: Boolean = code in 200..299

    actual val isRedirect: Boolean =
        when (code) {
            HTTP_PERM_REDIRECT, HTTP_TEMP_REDIRECT, HTTP_MULT_CHOICE, HTTP_MOVED_PERM, HTTP_MOVED_TEMP, HTTP_SEE_OTHER -> true
            else -> false
        }

    private var lazyCacheControl: CacheControl? = null

    actual val cacheControl: CacheControl
        get() {
            var result = lazyCacheControl
            if (result == null) {
                result = CacheControl.parse(headers)
                lazyCacheControl = result
            }
            return result
        }

    actual override fun close() {
        body.close()
    }

    actual fun newBuilder() = ResponseBuilder(this)

    actual override fun toString(): String =
        "Response{protocol=$protocol, code=$code, message=$message, url=${request.url}}"

}

actual open class ResponseBuilder {
    internal var request: Request? = null
    internal var protocol: Protocol? = null
    internal var code = -1
    internal var message: String? = null
    internal var headers: HeadersBuilder
    internal var body: ResponseBody = ResponseBody.EMPTY
    internal var networkResponse: Response? = null
    internal var cacheResponse: Response? = null
    internal var priorResponse: Response? = null
    internal var sentRequestAtMillis: Long = 0
    internal var receivedResponseAtMillis: Long = 0

    actual constructor() {
        headers = HeadersBuilder()
    }

    internal constructor(response: Response) {
        this.request = response.request
        this.protocol = response.protocol
        this.code = response.code
        this.message = response.message
        this.headers = response.headers.newBuilder()
        this.body = response.body
        this.networkResponse = response.networkResponse
        this.cacheResponse = response.cacheResponse
        this.priorResponse = response.priorResponse
        this.sentRequestAtMillis = response.sentRequestAtMillis
        this.receivedResponseAtMillis = response.receivedResponseAtMillis
    }

    actual open fun request(request: Request) =
        apply {
            this.request = request
        }

    actual open fun protocol(protocol: Protocol) =
        apply {
            this.protocol = protocol
        }

    actual open fun code(code: Int) =
        apply {
            this.code = code
        }

    actual open fun message(message: String) =
        apply {
            this.message = message
        }

    /**
     * Sets the header named [name] to [value]. If this request already has any headers
     * with that name, they are all replaced.
     */
    actual open fun header(
        name: String,
        value: String,
    ) = apply {
        headers[name] = value
    }

    /**
     * Adds a header with [name] to [value]. Prefer this method for multiply-valued
     * headers like "Set-Cookie".
     */
    actual open fun addHeader(
        name: String,
        value: String,
    ) = apply {
        headers.add(name, value)
    }

    /** Removes all headers named [name] on this builder. */
    actual open fun removeHeader(name: String) =
        apply {
            headers.removeAll(name)
        }

    /** Removes all headers on this builder and adds [headers]. */
    actual open fun headers(headers: Headers) =
        apply {
            this.headers = headers.newBuilder()
        }

    actual open fun body(body: ResponseBody) =
        apply {
            this.body = body
        }

    actual open fun networkResponse(networkResponse: Response?) =
        apply {
            checkSupportResponse("networkResponse", networkResponse)
            this.networkResponse = networkResponse
        }

    actual open fun cacheResponse(cacheResponse: Response?) =
        apply {
            checkSupportResponse("cacheResponse", cacheResponse)
            this.cacheResponse = cacheResponse
        }

    private fun checkSupportResponse(
        name: String,
        response: Response?,
    ) {
        response?.apply {
            require(networkResponse == null) { "$name.networkResponse != null" }
            require(cacheResponse == null) { "$name.cacheResponse != null" }
            require(priorResponse == null) { "$name.priorResponse != null" }
        }
    }

    actual open fun priorResponse(priorResponse: Response?) =
        apply {
            this.priorResponse = priorResponse
        }

    actual open fun sentRequestAtMillis(sentRequestAtMillis: Long) =
        apply {
            this.sentRequestAtMillis = sentRequestAtMillis
        }

    actual open fun receivedResponseAtMillis(receivedResponseAtMillis: Long) =
        apply {
            this.receivedResponseAtMillis = receivedResponseAtMillis
        }

    actual open fun build(): Response {
        check(code >= 0) { "code < 0: $code" }
        return Response(
            checkNotNull(request) { "request == null" },
            checkNotNull(protocol) { "protocol == null" },
            checkNotNull(message) { "message == null" },
            code,
            headers.build(),
            body,
            networkResponse,
            cacheResponse,
            priorResponse,
            sentRequestAtMillis,
            receivedResponseAtMillis,
        )
    }
}