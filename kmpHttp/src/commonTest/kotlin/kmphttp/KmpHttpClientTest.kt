package kmphttp

import kmphttp.RequestBodyX.toRequestBody
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okio.Buffer
import okio.use
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class KmpHttpClientTest {

    private fun createClient(): KmpHttpClient {
        return KmpHttpClientBuilder()
            .httpEngine(createHttpEngineForTest())
            .executeTimeout(30.seconds)
            .build()
    }

    @Test
    fun testSimpleGet(): Unit = runBlocking {
        val client = createClient()
        val request = RequestBuilder()
            .url("https://httpbin.org/get")
            .get()
            .build()

        client.execute(request).use { response ->
            assertEquals(200, response.code)
            assertNotNull(response.body)
        }
    }

    @Test
    fun testGetWithQueryParams(): Unit = runBlocking {
        val client = createClient()
        val request = RequestBuilder()
            .url("https://httpbin.org/get?foo=bar&baz=qux")
            .get()
            .build()

        client.execute(request).use { response ->
            assertEquals(200, response.code)
            val bodyString = response.body.utf8String()
            assertTrue(bodyString.contains("\"foo\""))
            assertTrue(bodyString.contains("\"bar\""))
        }
    }

    @Test
    fun testGetWithHeaders(): Unit = runBlocking {
        val client = createClient()
        val request = RequestBuilder()
            .url("https://httpbin.org/headers")
            .header("X-Custom-Header", "CustomValue")
            .header("X-Another-Header", "AnotherValue")
            .get()
            .build()

        client.execute(request).use { response ->
            assertEquals(200, response.code)
            val bodyString = response.body.utf8String()
            assertTrue(bodyString.contains("X-Custom-Header"))
            assertTrue(bodyString.contains("CustomValue"))
        }
    }

    @Test
    fun testPost(): Unit = runBlocking {
        val client = createClient()
        val request = RequestBuilder()
            .url("https://httpbin.org/post")
            .post("Hello, World!".toRequestBody(null))
            .build()

        client.execute(request).use { response ->
            assertEquals(200, response.code)
            val bodyString = response.body.utf8String()
            assertTrue(bodyString.contains("Hello, World!"))
        }
    }

    @Test
    fun testPut(): Unit = runBlocking {
        val client = createClient()
        val request = RequestBuilder()
            .url("https://httpbin.org/put")
            .put("""{"key":"value"}""".toRequestBody(null))
            .build()

        client.execute(request).use { response ->
            assertEquals(200, response.code)
            val bodyString = response.body.utf8String()
            assertTrue(bodyString.contains("key"))
            assertTrue(bodyString.contains("value"))
        }
    }

    @Test
    fun testDelete(): Unit = runBlocking {
        val client = createClient()
        val request = RequestBuilder()
            .url("https://httpbin.org/delete")
            .deleteWithoutBody()
            .build()

        client.execute(request).use { response ->
            assertEquals(200, response.code)
        }
    }

    @Test
    fun testPatch(): Unit = runBlocking {
        val client = createClient()
        val request = RequestBuilder()
            .url("https://httpbin.org/patch")
            .patch("""{"patched":true}""".toRequestBody(null))
            .build()

        client.execute(request).use { response ->
            assertEquals(200, response.code)
            val bodyString = response.body.utf8String()
            assertTrue(bodyString.contains("patched"))
        }
    }

    @Test
    fun testStatusCodes(): Unit = runBlocking {
        val client = createClient()

        // Test 404
        val request404 = RequestBuilder()
            .url("https://httpbin.org/status/404")
            .get()
            .build()
        client.execute(request404).use { response ->
            assertEquals(404, response.code)
        }

        // Test 500
        val request500 = RequestBuilder()
            .url("https://httpbin.org/status/500")
            .get()
            .build()
        client.execute(request500).use { response ->
            assertEquals(500, response.code)
        }
    }

    @Test
    fun testResponseHeaders(): Unit = runBlocking {
        val client = createClient()
        val request = RequestBuilder()
            .url("https://httpbin.org/response-headers?X-Test-Header=TestValue")
            .get()
            .build()

        client.execute(request).use { response ->
            assertEquals(200, response.code)
            assertEquals("TestValue", response.header("X-Test-Header"))
        }
    }

    @Test
    fun testInterceptor(): Unit = runBlocking {
        var interceptorCalled = false
        val client = KmpHttpClientBuilder()
            .httpEngine(createHttpEngineForTest())
            .executeTimeout(30.seconds)
            .addInterceptor { chain ->
                interceptorCalled = true
                val modifiedRequest = chain.request.newBuilder()
                    .addHeader("X-Interceptor", "Added")
                    .build()
                chain.proceed(modifiedRequest)
            }
            .build()

        val request = RequestBuilder()
            .url("https://httpbin.org/headers")
            .get()
            .build()

        client.execute(request).use { response ->
            assertEquals(200, response.code)
            assertTrue(interceptorCalled)
            val bodyString = response.body.utf8String()
            assertTrue(bodyString.contains("X-Interceptor"))
        }
    }

    @Test
    fun testMultipleInterceptors(): Unit = runBlocking {
        val callOrder = mutableListOf<Int>()

        val client = KmpHttpClientBuilder()
            .httpEngine(createHttpEngineForTest())
            .executeTimeout(30.seconds)
            .addInterceptor { chain ->
                callOrder.add(1)
                val response = chain.proceed(chain.request)
                callOrder.add(4)
                response
            }
            .addInterceptor { chain ->
                callOrder.add(2)
                val response = chain.proceed(chain.request)
                callOrder.add(3)
                response
            }
            .build()

        val request = RequestBuilder()
            .url("https://httpbin.org/get")
            .get()
            .build()

        client.execute(request).use { response ->
            assertEquals(200, response.code)
            assertEquals(listOf(1, 2, 3, 4), callOrder)
        }
    }

    @Test
    fun testClientNewBuilder(): Unit = runBlocking {
        val originalClient = createClient()

        var newInterceptorCalled = false
        val newClient = originalClient.newBuilder()
            .addInterceptor { chain ->
                newInterceptorCalled = true
                chain.proceed(chain.request)
            }
            .build()

        val request = RequestBuilder()
            .url("https://httpbin.org/get")
            .get()
            .build()

        newClient.execute(request).use { response ->
            assertEquals(200, response.code)
            assertTrue(newInterceptorCalled)
        }
    }

    @Test
    fun testUserAgent(): Unit = runBlocking {
        val client = createClient()
        val request = RequestBuilder()
            .url("https://httpbin.org/user-agent")
            .header("User-Agent", "KmpHttpTest/1.0")
            .get()
            .build()

        client.execute(request).use { response ->
            assertEquals(200, response.code)
            val bodyString = response.body.utf8String()
            assertTrue(bodyString.contains("KmpHttpTest/1.0"))
        }
    }

    // Architecture note: Redirect behavior is controlled via RequestOptions passed to execute(),
    // not via builder settings. The HttpEngine receives RequestOptions and configures accordingly.
    @Test
    fun testFollowRedirects(): Unit = runBlocking {
        val client = createClient()
        val request = RequestBuilder()
            .url("https://httpbin.org/redirect/3")
            .get()
            .build()
        val options = RequestOptions(followRedirects = true, followSslRedirects = true)

        client.execute(request, options).use { response ->
            assertEquals(200, response.code)
            // Final URL should be /get after 3 redirects
            assertTrue(response.request.url.toString().endsWith("/get"))
        }
    }

    @Test
    fun testDisableFollowRedirects(): Unit = runBlocking {
        val client = createClient()
        val request = RequestBuilder()
            .url("https://httpbin.org/redirect/1")
            .get()
            .build()
        val options = RequestOptions(followRedirects = false, followSslRedirects = false)

        client.execute(request, options).use { response ->
            assertEquals(302, response.code)
            assertNotNull(response.header("Location"))
        }
    }

    @Test
    fun testRedirectToAbsoluteUrl(): Unit = runBlocking {
        val client = createClient()
        val request = RequestBuilder()
            .url("https://httpbin.org/absolute-redirect/2")
            .get()
            .build()

        client.execute(request).use { response ->
            assertEquals(200, response.code)
            assertTrue(response.request.url.toString().endsWith("/get"))
        }
    }

    @Test
    fun testPriorResponseChain(): Unit = runBlocking {
        val client = createClient()
        val request = RequestBuilder()
            .url("https://httpbin.org/redirect/2")
            .get()
            .build()

        client.execute(request).use { response ->
            assertEquals(200, response.code)

            // Check prior response chain (2 redirects = 2 prior responses)
            val prior1 = response.priorResponse
            assertNotNull(prior1)
            assertEquals(302, prior1.code)

            val prior2 = prior1.priorResponse
            assertNotNull(prior2)
            assertEquals(302, prior2.code)

            // No more prior responses
            assertEquals(null, prior2.priorResponse)
        }
    }

    @Test
    fun testExecuteTimeout(): Unit = runBlocking {
        val client = KmpHttpClientBuilder()
            .httpEngine(createHttpEngineForTest())
            .executeTimeout(500.milliseconds)
            .build()

        val request = RequestBuilder()
            .url("https://httpbin.org/delay/3") // 3 second delay
            .get()
            .build()

        assertFailsWith<TimeoutCancellationException> {
            client.execute(request)
        }
    }

    @Test
    fun testExecuteWithinTimeout(): Unit = runBlocking {
        val client = KmpHttpClientBuilder()
            .httpEngine(createHttpEngineForTest())
            .executeTimeout(10.seconds)
            .build()

        val request = RequestBuilder()
            .url("https://httpbin.org/delay/1") // 1 second delay
            .get()
            .build()

        client.execute(request).use { response ->
            assertEquals(200, response.code)
        }
    }

    @Test
    fun testTimeoutWhileReadingResponseBody(): Unit = runBlocking {
        val client = createClient()
        val request = RequestBuilder()
            // drip: streams bytes slowly - 10 bytes over 5 seconds
            .url("https://httpbin.org/drip?duration=5&numbytes=10&code=200&delay=0")
            .get()
            .build()

        // Execute with long timeout so request succeeds
        client.execute(request, timeout = 30.seconds).use { response ->
            assertEquals(200, response.code)

            // Timeout while reading body via asyncSource
            assertFailsWith<TimeoutCancellationException> {
                withTimeout(500.milliseconds) {
                    val source = response.body.asyncSource()
                    val buffer = Buffer()
                    while (source.read(buffer, 8192) != -1L) {
                        // Keep reading until timeout
                    }
                }
            }
        }
    }
}
