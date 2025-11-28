package kmphttp

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
        val asyncChain = AsyncChain(0, request, options)
        if (timeout == null) {
            asyncChain.proceed(request)
        } else {
            withTimeout(timeout) {
                asyncChain.proceed(request)
            }
        }
    }

    private inner class AsyncChain(
        val interceptorIndex: Int,
        override val request: Request,
        override val options: RequestOptions
    ) : AsyncInterceptor.Chain {
        override suspend fun proceed(request: Request): Response {
            if (interceptorIndex == interceptors.size) {
                return engine.execute(request, options)
            } else {
                val interceptor = interceptors[interceptorIndex]
                return interceptor.intercept(AsyncChain(interceptorIndex + 1, request, options))
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
    private var readTimeout: Duration,
    private var writeTimeout: Duration,
    private var defaultDispatcher: CoroutineDispatcher,
    private var httpEngine: HttpEngine?
) {

    constructor() : this(
        interceptors = mutableListOf(),
        followRedirects = true,
        followSslRedirects = true,
        executeTimeout = 10.seconds,
        readTimeout = 10.seconds,
        writeTimeout = 10.seconds,
        defaultDispatcher = Dispatchers.IO,
        httpEngine = null
    )

    constructor(instance: KmpHttpClient) : this(
        interceptors = instance.interceptors.toMutableList(),
        followRedirects = instance.defaultRequestOptions.followRedirects,
        followSslRedirects = instance.defaultRequestOptions.followSslRedirects,
        executeTimeout = instance.defaultExecuteTimeout,
        readTimeout = instance.defaultRequestOptions.readTimeout,
        writeTimeout = instance.defaultRequestOptions.writeTimeout,
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

    /**
     * Sets the default read timeout for new connections. A value of 0 means no timeout, otherwise
     * values must be between 1 and [Integer.MAX_VALUE] when converted to milliseconds.
     *
     * The read timeout is applied to both the TCP socket and for individual read IO operations
     * including on [Source] of the [Response]. The default value is 10 seconds.
     *
     * @see Socket.setSoTimeout
     * @see Source.timeout
     */
    fun readTimeout(duration: Duration) = apply {
        this.readTimeout = duration
    }

    /**
     * Sets the default write timeout for new connections. A value of 0 means no timeout, otherwise
     * values must be between 1 and [Integer.MAX_VALUE] when converted to milliseconds.
     *
     * The write timeout is applied for individual write IO operations. The default value is 10
     * seconds.
     *
     * @see Sink.timeout
     */
    fun writeTimeout(duration: Duration) = apply {
        this.writeTimeout = duration
    }

    fun httpEngine(engine: HttpEngine) = apply {
        this.httpEngine = engine
    }

    fun build(): KmpHttpClient {
        return KmpHttpClient(
            interceptors = interceptors.toList(),
            defaultRequestOptions = RequestOptions(
                followRedirects,
                followSslRedirects,
                readTimeout,
                writeTimeout
            ),
            defaultExecuteTimeout = executeTimeout,
            defaultDispatcher = defaultDispatcher,
            engine = checkNotNull(httpEngine) { "Http engine is required" }
        )
    }
}