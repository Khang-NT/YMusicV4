package kmphttp

import kmphttp.internal.AsyncChain
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okio.Sink
import okio.Source
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Actual http engine that perform network request.
 */
interface HttpEngine {
    suspend fun execute(request: Request, options: RequestOptions): Response
}

/**
 */
open class KmpHttpClient internal constructor(
    val interceptors: List<AsyncInterceptor>,
    val defaultRequestOptions: RequestOptions,
    val defaultExecuteTimeout: Duration?,
    val defaultDispatcher: CoroutineDispatcher,
    internal val engine: HttpEngine
) {
    open suspend fun execute(
        request: Request,
        options: RequestOptions = defaultRequestOptions,
        timeout: Duration? = defaultExecuteTimeout
    ): Response = withContext(defaultDispatcher) {
        val asyncChain = AsyncChain(interceptors, 0, engine::execute, request, options)
        if (timeout == null) {
            asyncChain.proceed(request)
        } else {
            withTimeout(timeout) {
                asyncChain.proceed(request)
            }
        }
    }

    open fun newBuilder() = KmpHttpClientBuilder(this)
}

class KmpHttpClientBuilder internal constructor(
    private val interceptors: MutableList<AsyncInterceptor>,
    private var followRedirects: Boolean,
    private var followSslRedirects: Boolean,
    private var executeTimeout: Duration?,
    private var defaultDispatcher: CoroutineDispatcher,
    private var httpEngine: HttpEngine?
) {

    constructor() : this(
        interceptors = mutableListOf(),
        followRedirects = true,
        followSslRedirects = true,
        executeTimeout = 10.seconds,
        defaultDispatcher = Dispatchers.IO,
        httpEngine = null
    )

    constructor(instance: KmpHttpClient) : this(
        interceptors = instance.interceptors.toMutableList(),
        followRedirects = instance.defaultRequestOptions.followRedirects,
        followSslRedirects = instance.defaultRequestOptions.followSslRedirects,
        executeTimeout = instance.defaultExecuteTimeout,
        defaultDispatcher = instance.defaultDispatcher,
        httpEngine = instance.engine
    )

    /**
     * Returns a modifiable list of interceptors that observe the full span of each call: from
     * before the connection is established (if any) until after the response source is selected
     * (either the origin server, cache, or both).
     */
    fun interceptors(): MutableList<AsyncInterceptor> = interceptors
    fun addInterceptor(interceptor: AsyncInterceptor) = apply {
        interceptors.add(interceptor)
    }

    /** Configure this client to follow redirects. If unset, redirects will be followed. */
    fun followRedirects(followRedirects: Boolean) = apply {
        this.followRedirects = followRedirects
    }

    /**
     * Configure this client to allow protocol redirects from HTTPS to HTTP and from HTTP to HTTPS.
     * Redirects are still first restricted by [followRedirects].  Defaults to true.
     *
     * @param followProtocolRedirects whether to follow redirects between HTTPS and HTTP.
     */
    fun followSslRedirects(followProtocolRedirects: Boolean) = apply {
        this.followSslRedirects = followProtocolRedirects
    }

    fun executeTimeout(duration: Duration?) = apply {
        this.executeTimeout = duration
    }


    fun httpEngine(engine: HttpEngine) = apply {
        this.httpEngine = engine
    }

    fun build(): KmpHttpClient {
        return KmpHttpClient(
            interceptors = interceptors.toList(),
            defaultRequestOptions = RequestOptions(
                followRedirects,
                followSslRedirects
            ),
            defaultExecuteTimeout = executeTimeout,
            defaultDispatcher = defaultDispatcher,
            engine = checkNotNull(httpEngine) { "Http engine is required" }
        )
    }
}