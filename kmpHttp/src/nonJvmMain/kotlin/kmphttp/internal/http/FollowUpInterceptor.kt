/*
 * Copyright (C) 2016 Square, Inc.
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

import kmphttp.AsyncInterceptor
import kmphttp.HttpStatus.HTTP_MOVED_PERM
import kmphttp.HttpStatus.HTTP_MOVED_TEMP
import kmphttp.HttpStatus.HTTP_MULT_CHOICE
import kmphttp.HttpStatus.HTTP_PERM_REDIRECT
import kmphttp.HttpStatus.HTTP_SEE_OTHER
import kmphttp.HttpStatus.HTTP_TEMP_REDIRECT
import kmphttp.Request
import kmphttp.Response
import kmphttp.header
import kmphttp.internal.HttpMethod
import kmphttp.internal.closeQuietly
import kmphttp.internal.stripBody
import okio.IOException

/**
 * This interceptor follows redirects as necessary.
 *
 * Simplified version for non-JVM platforms:
 * - Handles redirects (301, 302, 303, 307, 308)
 * - Does NOT handle: retry logic, complex connection management, HTTP/2 specifics
 */
class FollowUpInterceptor : AsyncInterceptor {

    override suspend fun intercept(chain: AsyncInterceptor.Chain): Response {
        var request = chain.request
        val options = chain.options
        var followUpCount = 0
        var priorResponse: Response? = null

        while (true) {
            val response = chain.proceed(request)

            // Clear out downstream interceptor's additional request headers, cookies, etc.
            val responseWithPrior =
                response
                    .newBuilder()
                    .request(request)
                    .priorResponse(priorResponse?.stripBody())
                    .build()

            val followUp = followUpRequest(responseWithPrior, options.followRedirects, options.followSslRedirects)

            if (followUp == null) {
                return responseWithPrior
            }

            val followUpBody = followUp.body
            if (followUpBody != null && followUpBody.isOneShot()) {
                return responseWithPrior
            }

            responseWithPrior.body.closeQuietly()

            if (++followUpCount > MAX_FOLLOW_UPS) {
                throw IOException("Too many follow-up requests: $followUpCount")
            }

            request = followUp
            priorResponse = responseWithPrior
        }
    }

    /**
     * Figures out the HTTP request to make in response to receiving [userResponse]. This will
     * follow redirects if applicable. If a follow-up is either unnecessary or not applicable,
     * this returns null.
     */
    private fun followUpRequest(
        userResponse: Response,
        followRedirects: Boolean,
        followSslRedirects: Boolean,
    ): Request? {
        val responseCode = userResponse.code
        val method = userResponse.request.method

        return when (responseCode) {
            HTTP_PERM_REDIRECT, HTTP_TEMP_REDIRECT, HTTP_MULT_CHOICE, HTTP_MOVED_PERM, HTTP_MOVED_TEMP, HTTP_SEE_OTHER -> {
                buildRedirectRequest(userResponse, method, followRedirects, followSslRedirects)
            }
            else -> null
        }
    }

    private fun buildRedirectRequest(
        userResponse: Response,
        method: String,
        followRedirects: Boolean,
        followSslRedirects: Boolean,
    ): Request? {
        // Does the client allow redirects?
        if (!followRedirects) return null

        val location = userResponse.header("Location") ?: return null
        // Don't follow redirects to unsupported protocols.
        val url = userResponse.request.url.resolve(location) ?: return null

        // If configured, don't follow redirects between SSL and non-SSL.
        val sameScheme = url.scheme == userResponse.request.url.scheme
        if (!sameScheme && !followSslRedirects) return null

        // Most redirects don't include a request body.
        val requestBuilder = userResponse.request.newBuilder()
        if (HttpMethod.permitsRequestBody(method)) {
            val responseCode = userResponse.code
            val maintainBody =
                HttpMethod.redirectsWithBody(method) ||
                    responseCode == HTTP_PERM_REDIRECT ||
                    responseCode == HTTP_TEMP_REDIRECT
            if (HttpMethod.redirectsToGet(method) && responseCode != HTTP_PERM_REDIRECT && responseCode != HTTP_TEMP_REDIRECT) {
                requestBuilder.method("GET", null)
            } else {
                val requestBody = if (maintainBody) userResponse.request.body else null
                requestBuilder.method(method, requestBody)
            }
            if (!maintainBody) {
                requestBuilder.removeHeader("Transfer-Encoding")
                requestBuilder.removeHeader("Content-Length")
                requestBuilder.removeHeader("Content-Type")
            }
        }

        // When redirecting across hosts, drop all authentication headers.
        if (!canReuseConnectionFor(userResponse.request.url, url)) {
            requestBuilder.removeHeader("Authorization")
        }

        return requestBuilder.url(url).build()
    }

    /**
     * Returns true if requests to [a] can reuse a connection to [b].
     * Both URLs must share the same scheme, host, and port.
     */
    private fun canReuseConnectionFor(a: kmphttp.HttpUrl, b: kmphttp.HttpUrl): Boolean {
        return a.host == b.host &&
            a.port == b.port &&
            a.scheme == b.scheme
    }

    companion object {
        /**
         * How many redirects and auth challenges should we attempt? Chrome follows 21 redirects;
         * Firefox, curl, and wget follow 20; Safari follows 16; and HTTP/1.0 recommends 5.
         */
        private const val MAX_FOLLOW_UPS = 20
    }
}
