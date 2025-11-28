package kmphttp

/**
 * Observes, modifies, and potentially short-circuits requests going out and the corresponding
 * responses coming back in. Inspired by OkHttp's Interceptor.
 */
fun interface AsyncInterceptor {
    /**
     * Intercept the request and return a response.
     */
    suspend fun intercept(chain: Chain): Response

    /**
     * A concrete interceptor chain that carries the request and allows calling the next interceptor.
     */
    interface Chain {
        val request: Request
        val options: RequestOptions

        suspend fun proceed(request: Request): Response
    }
}