package kmphttp

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okio.IOException
import kotlin.coroutines.resumeWithException

internal class OkHttpEngine(
    baseOkHttp: OkHttpClient
) : HttpEngine {
    companion object {
        private fun redirectOptionsToIndex(followRedirects: Boolean, followSslRedirects: Boolean): Int {
            return when {
                !followRedirects -> 0
                !followSslRedirects -> 1
                else -> 2
            }
        }
    }
    private val clients = Array<OkHttpClient?>(3) { null }

    init {
        clients[
            redirectOptionsToIndex(baseOkHttp.followRedirects, baseOkHttp.followSslRedirects)
        ] = baseOkHttp
    }

    private fun getOkHttpClient(options: RequestOptions): OkHttpClient {
        val i = redirectOptionsToIndex(options.followRedirects, options.followSslRedirects)
        clients[i]?.let { return@let it }
        synchronized(clients) {
            clients[i]?.let { return@let it }
            val baseOkHttp = checkNotNull(clients.find { it != null })
            val newClient = baseOkHttp.newBuilder()
                .followRedirects(options.followRedirects)
                .followSslRedirects(options.followSslRedirects)
                .build()
            clients[i] = newClient
            return newClient
        }
    }

    override suspend fun execute(
        request: Request,
        options: RequestOptions
    ): Response {
        val client = getOkHttpClient(options)
        return suspendCancellableCoroutine { cancellableContinuation ->
            val call = client.newCall(request.newBuilder()
                .tag(RequestOptions::class, options)
                .build())

            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    cancellableContinuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: okhttp3.Response) {
                    cancellableContinuation.resume(response) { error, res, context ->
                        res.close()
                    }
                }

            })
            cancellableContinuation.invokeOnCancellation { call.cancel() }
        }
    }
}

/**
 * Create [HttpEngine] from an [OkHttpClient] instance.
 * Note that, [KmpHttpClient] timeout mechanism depends on Coroutine's [kotlinx.coroutines.time.withTimeout],
 * so the provided [OkHttpClient] instance's timeouts (connectTimeout, readTimeout, writeTimeout)
 * should long enough to cover the multiplatform code usage.
 */
fun OkHttpClient.toHttpEngine(): HttpEngine {
    return OkHttpEngine(this)
}