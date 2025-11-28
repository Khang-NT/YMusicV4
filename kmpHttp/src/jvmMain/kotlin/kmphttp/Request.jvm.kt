package kmphttp

actual typealias Request = okhttp3.Request
actual typealias RequestBuilder = okhttp3.Request.Builder

/**
 * Workaround default parameter not allowed in expect/actual functions.
 */
actual fun RequestBuilder.deleteWithBody(body: RequestBody) = apply { delete(body) }
actual fun RequestBuilder.deleteWithoutBody() = apply { delete(body = null) }
