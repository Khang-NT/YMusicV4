package kmphttp

/**
 * An [RFC 2045][rfc_2045] Media Type, appropriate to describe the content type of an HTTP request
 * or response body.
 *
 * [rfc_2045]: http://tools.ietf.org/html/rfc2045
 */
expect class MediaType {
    /**
     * Returns the high-level media type, such as "text", "image", "audio", "video", or "application".
     */
    val type: String

    /**
     * Returns a specific media subtype, such as "plain" or "png", "mpeg", "mp4" or "xml".
     */
    val subtype: String
//    /**
//     * Returns the charset of this media type, or [defaultValue] if either this media type doesn't
//     * specify a charset, or if its charset is unsupported by the current runtime.
//     */
//    @JvmOverloads
//    fun charset(defaultValue: Charset? = null): Charset? {
//        val charset = parameter("charset") ?: return defaultValue
//        return try {
//            Charset.forName(charset)
//        } catch (_: IllegalArgumentException) {
//            defaultValue // This charset is invalid or unsupported. Give up.
//        }
//    }

    /**
     * Returns the parameter [name] of this media type, or null if this media type does not define
     * such a parameter.
     */
    fun parameter(name: String): String?


    /**
     * Returns the encoded media type, like "text/plain; charset=utf-8", appropriate for use in a
     * Content-Type header.
     */
    override fun toString(): String

    override fun equals(other: Any?): Boolean

    override fun hashCode(): Int

    companion object {
        /**
         * Returns a media type for this string.
         *
         * @throws IllegalArgumentException if this is not a well-formed media type.
         */
        fun String.toMediaType(): MediaType

        /** Returns a media type for this, or null if this is not a well-formed media type. */
        fun String.toMediaTypeOrNull(): MediaType?
    }
}

