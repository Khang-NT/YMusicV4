package kmphttp

import java.util.concurrent.TimeUnit
import kotlin.time.Duration

actual typealias CacheControl = okhttp3.CacheControl
actual typealias CacheControlBuilder = okhttp3.CacheControl.Builder

actual fun CacheControlBuilder.maxAge(maxAge: Duration): CacheControlBuilder {
    return maxAge(maxAge.inWholeMilliseconds.toInt(), TimeUnit.MILLISECONDS)
}

actual fun CacheControlBuilder.maxStale(maxStale: Duration): CacheControlBuilder {
    return maxStale(maxStale.inWholeMilliseconds.toInt(), TimeUnit.MILLISECONDS)
}

actual fun CacheControlBuilder.minFresh(minFresh: Duration): CacheControlBuilder {
    return minFresh(minFresh.inWholeMilliseconds.toInt(), TimeUnit.MILLISECONDS)
}