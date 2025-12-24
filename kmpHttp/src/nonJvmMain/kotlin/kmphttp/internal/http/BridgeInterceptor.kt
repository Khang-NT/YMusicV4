/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package kmphttp.internal.http

import kmphttp.AsyncInterceptor
import kmphttp.Cookie
import kmphttp.CookieJar
import kmphttp.KmpHttp
import kmphttp.Response
import kmphttp.internal.toHostHeader
import kmphttp.parseAllCookies

/**
 * Bridges from application code to network code. First it builds a network request from a user
 * request. Then it proceeds to call the network. Finally it builds a user response from the network
 * response.
 */
class BridgeInterceptor(
    private val cookieJar: CookieJar,
) : AsyncInterceptor {

    override suspend fun intercept(chain: AsyncInterceptor.Chain): Response {
        val userRequest = chain.request
        val requestBuilder = userRequest.newBuilder()

        val body = userRequest.body
        if (body != null) {
            val contentType = body.contentType()
            if (contentType != null) {
                requestBuilder.header("Content-Type", contentType.toString())
            }

            val contentLength = body.contentLength()
            if (contentLength != -1L) {
                requestBuilder.header("Content-Length", contentLength.toString())
            } else {
                requestBuilder.removeHeader("Content-Length")
            }
        }

        if (userRequest.header("Host") == null) {
            requestBuilder.header("Host", userRequest.url.toHostHeader())
        }

        // Note: Accept-Encoding is NOT set here - platform HttpEngine handles compression

        val cookies = cookieJar.loadForRequest(userRequest.url)
        if (cookies.isNotEmpty()) {
            requestBuilder.header("Cookie", cookieHeader(cookies))
        }

        if (userRequest.header("User-Agent") == null) {
            requestBuilder.header("User-Agent", KmpHttp.userAgent)
        }

        val networkRequest = requestBuilder.build()
        val networkResponse = chain.proceed(networkRequest)

        // Save cookies from response
        val responseCookies = parseAllCookies(networkRequest.url, networkResponse.headers)
        if (responseCookies.isNotEmpty()) {
            cookieJar.saveFromResponse(networkRequest.url, responseCookies)
        }

        return networkResponse
    }

    /** Returns a 'Cookie' HTTP request header with all cookies, like `a=b; c=d`. */
    private fun cookieHeader(cookies: List<Cookie>): String =
        buildString {
            cookies.forEachIndexed { index, cookie ->
                if (index > 0) append("; ")
                append(cookie.name).append('=').append(cookie.value)
            }
        }
}
