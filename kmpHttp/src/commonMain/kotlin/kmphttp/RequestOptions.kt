package kmphttp

data class RequestOptions(
    val followRedirects: Boolean,
    val followSslRedirects: Boolean
)