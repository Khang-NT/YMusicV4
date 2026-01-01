package kmphttp

import okhttp3.MediaType.Companion.toMediaType as toMediaTypeOkHttp
import okhttp3.MediaType.Companion.toMediaTypeOrNull as toMediaTypeOrNullOkHttp

actual typealias MediaType = okhttp3.MediaType

actual object MediaTypeX {
    /**
     * Returns a media type for this string.
     *
     * @throws IllegalArgumentException if this is not a well-formed media type.
     */
    actual fun String.toMediaType(): MediaType = toMediaTypeOkHttp()

    /** Returns a media type for this, or null if this is not a well-formed media type. */
    actual fun String.toMediaTypeOrNull(): MediaType? = toMediaTypeOrNullOkHttp()
}