package kmphttp

import kmphttp.internal.toHttpDateOrNull
import kmphttp.internal.toHttpDateString
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * The header fields of a single HTTP message. Values are uninterpreted strings; use `Request` and
 * `Response` for interpreted headers. This class maintains the order of the header fields within
 * the HTTP message.
 *
 * This class tracks header values line-by-line. A field with multiple comma- separated values on
 * the same line will be treated as a field with a single value by this class. It is the caller's
 * responsibility to detect and split on commas if their field permits multiple values. This
 * simplifies use of single-valued fields whose values routinely contain commas, such as cookies or
 * dates.
 *
 * This class trims whitespace from values. It never returns values with leading or trailing
 * whitespace.
 *
 * Instances of this class are immutable. Use [Builder] to create instances.
 */
@Suppress("NAME_SHADOWING")
expect class Headers : Iterable<Pair<String, String>> {
    /** Returns the last value corresponding to the specified field, or null. */
    operator fun get(name: String): String?

    /** Returns the number of field values. */
    val size: Int

    /** Returns the field at `position`. */
    fun name(index: Int): String

    /** Returns the value at `index`. */
    fun value(index: Int): String

    /** Returns an immutable list of the header values for `name`. */
    fun values(name: String): List<String>


    fun newBuilder(): HeadersBuilder

    /**
     * Returns true if `other` is a `Headers` object with the same headers, with the same casing, in
     * the same order. Note that two headers instances may be *semantically* equal but not equal
     * according to this method. In particular, none of the following sets of headers are equal
     * according to this method:
     *
     * 1. Original
     * ```
     * Content-Type: text/html
     * Content-Length: 50
     * ```
     *
     * 2. Different order
     *
     * ```
     * Content-Length: 50
     * Content-Type: text/html
     * ```
     *
     * 3. Different case
     *
     * ```
     * content-type: text/html
     * content-length: 50
     * ```
     *
     * 4. Different values
     *
     * ```
     * Content-Type: text/html
     * Content-Length: 050
     * ```
     *
     * Applications that require semantically equal headers should convert them into a canonical form
     * before comparing them for equality.
     */
    override fun equals(other: Any?): Boolean

    override fun hashCode(): Int

    /**
     * Returns header names and values. The names and values are separated by `: ` and each pair is
     * followed by a newline character `\n`.
     *
     * Since OkHttp 5 this redacts these sensitive headers:
     *
     *  * `Authorization`
     *  * `Cookie`
     *  * `Proxy-Authorization`
     *  * `Set-Cookie`
     */
    override fun toString(): String

    override fun iterator(): Iterator<Pair<String, String>>


    companion object {
        /** Empty headers. */
        val EMPTY: Headers
    }
}

@OptIn(ExperimentalTime::class)
fun Headers.getInstant(name: String): Instant? = this[name]?.toHttpDateOrNull()
fun Headers.toMap(): Map<LowercaseString, List<String>> {
    return HashMap<LowercaseString, MutableList<String>>(size).apply {
        for ((name, value) in this@toMap) {
            getOrPut(name.toLowercaseString()) { mutableListOf() }.add(value)
        }
    }
}

expect class HeadersBuilder {
    constructor()
    /** Add an header line containing a field name, a literal colon, and a value. */
    fun add(line: String): HeadersBuilder

    /**
     * Add a header with the specified name and value. Does validation of header names and values.
     */
    fun add(
        name: String,
        value: String,
    ): HeadersBuilder

    /**
     * Add a header with the specified name and value. Does validation of header names, allowing
     * non-ASCII values.
     */
    fun addUnsafeNonAscii(
        name: String,
        value: String,
    ): HeadersBuilder

    /**
     * Adds all headers from an existing collection.
     */
    fun addAll(headers: Headers): HeadersBuilder

    fun removeAll(name: String): HeadersBuilder

    /**
     * Set a field with the specified value. If the field is not found, it is added. If the field is
     * found, the existing values are replaced.
     */
    operator fun set(
        name: String,
        value: String,
    ): HeadersBuilder

    /** Equivalent to `build().get(name)`, but potentially faster. */
    operator fun get(name: String): String?

    fun build(): Headers
}

/**
 * Set a field with the specified instant. If the field is not found, it is added. If the field
 * is found, the existing values are replaced.
 */
@OptIn(ExperimentalTime::class)
operator fun HeadersBuilder.set(name: String, value: Instant) = apply {
    set(name, value.toHttpDateString())
}