package kmphttp

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

actual fun createHttpEngineForTest(): HttpEngine {
    val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    return okHttpClient.toHttpEngine()
}
