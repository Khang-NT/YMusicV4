package kmphttp

import kotlin.time.Duration

data class RequestOptions(
    val followRedirects: Boolean,
    val followSslRedirects: Boolean,
    val readTimeout: Duration,
    val writeTimeout: Duration
)