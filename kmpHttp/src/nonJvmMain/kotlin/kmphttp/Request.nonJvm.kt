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

import kmphttp.internal.HttpMethod
import kmphttp.internal.http.GzipRequestBody
import kmphttp.internal.isSensitiveHeader

actual class Request internal constructor(
    builder: RequestBuilder,
) {
    actual val url: HttpUrl = checkNotNull(builder.url) { "url == null" }

    actual val method: String = builder.method

    actual val headers: Headers = builder.headers.build()

    actual val body: RequestBody? = builder.body

    actual val cacheUrlOverride: HttpUrl? = builder.cacheUrlOverride

    private var lazyCacheControl: CacheControl? = null

    actual val isHttps: Boolean
        get() = url.isHttps

    actual fun header(name: String): String? = headers[name]

    actual fun headers(name: String): List<String> = headers.values(name)

    actual fun newBuilder() = RequestBuilder(this)

    actual val cacheControl: CacheControl
        get() {
            var result = lazyCacheControl
            if (result == null) {
                result = CacheControl.parse(headers)
                lazyCacheControl = result
            }
            return result
        }

    actual override fun toString(): String =
        buildString(32) {
            append("Request{method=")
            append(method)
            append(", url=")
            append(url)
            if (headers.size != 0) {
                append(", headers=[")
                headers.forEachIndexed { index, (name, value) ->
                    if (index > 0) {
                        append(", ")
                    }
                    append(name)
                    append(':')
                    append(if (isSensitiveHeader(name)) "██" else value)
                }
                append(']')
            }
            append('}')
        }
}

actual open class RequestBuilder {
    internal var url: HttpUrl? = null
    internal var method: String
    internal var headers: HeadersBuilder
    internal var body: RequestBody? = null
    internal var cacheUrlOverride: HttpUrl? = null

    actual constructor() {
        this.method = "GET"
        this.headers = HeadersBuilder()
    }

    internal constructor(request: Request) {
        this.url = request.url
        this.method = request.method
        this.body = request.body
        this.headers = request.headers.newBuilder()
        this.cacheUrlOverride = request.cacheUrlOverride
    }

    actual open fun url(url: HttpUrl) =
        apply {
            this.url = url
        }

    actual open fun url(url: String) = url(canonicalUrl(url).toHttpUrl())

    // Silently replace web socket URLs with HTTP URLs.
    private fun canonicalUrl(url: String) =
        when {
            url.startsWith("ws:", ignoreCase = true) -> "http:${url.substring(3)}"
            url.startsWith("wss:", ignoreCase = true) -> "https:${url.substring(4)}"
            else -> url
        }

    actual open fun header(
        name: String,
        value: String,
    ) = apply {
        headers[name] = value
    }

    actual open fun addHeader(
        name: String,
        value: String,
    ) = apply {
        headers.add(name, value)
    }

    actual open fun removeHeader(name: String) =
        apply {
            headers.removeAll(name)
        }

    actual open fun headers(headers: Headers) =
        apply {
            this.headers = headers.newBuilder()
        }

    actual open fun cacheControl(cacheControl: CacheControl) = apply {
        val value = cacheControl.toString()
        return when {
            value.isEmpty() -> removeHeader("Cache-Control")
            else -> header("Cache-Control", value)
        }
    }

    actual open fun get() = method("GET", null)

    actual open fun head() = method("HEAD", null)

    actual open fun post(body: RequestBody) = method("POST", body)

    open fun delete(body: RequestBody?) = method("DELETE", body)

    actual open fun put(body: RequestBody) = method("PUT", body)

    actual open fun patch(body: RequestBody) = method("PATCH", body)

    actual open fun query(body: RequestBody) = method("QUERY", body)

    actual open fun method(
        method: String,
        body: RequestBody?,
    ) =
        apply {
            require(method.isNotEmpty()) {
                "method.isEmpty() == true"
            }
            if (body == null) {
                require(!HttpMethod.requiresRequestBody(method)) {
                    "method $method must have a request body."
                }
            } else {
                require(HttpMethod.permitsRequestBody(method)) {
                    "method $method must not have a request body."
                }
            }
            this.method = method
            this.body = body
        }

    actual fun cacheUrlOverride(cacheUrlOverride: HttpUrl?) =
        apply {
            this.cacheUrlOverride = cacheUrlOverride
        }

    actual fun gzip() =
        apply {
            val identityBody =
                body
                    ?: throw IllegalStateException("cannot gzip a request that has no body")

            val contentEncoding = headers["Content-Encoding"]
            check(contentEncoding == null) {
                "Content-Encoding already set: $contentEncoding"
            }

            headers.add("Content-Encoding", "gzip")
            body = GzipRequestBody(identityBody)
        }

    actual open fun build(): Request = Request(this)
}

/**
 * Workaround default parameter not allowed in expect/actual functions.
 */
actual fun RequestBuilder.deleteWithBody(body: RequestBody) = apply { delete(body) }
actual fun RequestBuilder.deleteWithoutBody() = apply { delete(body = null) }
