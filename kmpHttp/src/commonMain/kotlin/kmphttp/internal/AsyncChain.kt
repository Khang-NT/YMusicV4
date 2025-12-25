package kmphttp.internal

import kmphttp.AsyncInterceptor
import kmphttp.Request
import kmphttp.RequestOptions
import kmphttp.Response

internal class AsyncChain(
    val interceptors: List<AsyncInterceptor>,
    val interceptorIndex: Int,
    val execute: suspend (Request, RequestOptions) -> Response,
    override val request: Request,
    override val options: RequestOptions
) : AsyncInterceptor.Chain {
    override suspend fun proceed(request: Request): Response {
        if (interceptorIndex == interceptors.size) {
            return execute(request, options)
        } else {
            val interceptor = interceptors[interceptorIndex]
            return interceptor.intercept(AsyncChain(interceptors, interceptorIndex + 1, execute, request, options))
        }
    }
}