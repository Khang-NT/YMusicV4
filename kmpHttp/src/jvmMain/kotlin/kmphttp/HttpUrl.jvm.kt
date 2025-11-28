package kmphttp

import okhttp3.HttpUrl.Companion.toHttpUrl as okHttpToHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull as okHttpToHttpUrlOrNull

actual typealias HttpUrl = okhttp3.HttpUrl
actual typealias HttpUrlBuilder = okhttp3.HttpUrl.Builder

actual suspend fun HttpUrl.getTopPrivateDomain(): String? = this.topPrivateDomain()

/**
 * Parses [this] as an HTTP URL and returns it, throwing [IllegalArgumentException] if invalid.
 *
 * This extension function is provided to match the common API.
 */
actual fun String.toHttpUrl(): HttpUrl = okHttpToHttpUrl()

/**
 * Parses [this] as an HTTP URL and returns it, or null if invalid.
 *
 * This extension function is provided to match the common API.
 */
actual fun String.toHttpUrlOrNull(): HttpUrl? = okHttpToHttpUrlOrNull()