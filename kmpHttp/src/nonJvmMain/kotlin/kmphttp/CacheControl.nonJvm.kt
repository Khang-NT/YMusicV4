package kmphttp

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

actual class CacheControl(
    actual val noCache: Boolean,
    actual val noStore: Boolean,
    actual val maxAgeSeconds: Int,
    actual val sMaxAgeSeconds: Int,
    actual val isPrivate: Boolean,
    actual val isPublic: Boolean,
    actual val mustRevalidate: Boolean,
    actual val maxStaleSeconds: Int,
    actual val minFreshSeconds: Int,
    actual val onlyIfCached: Boolean,
    actual val noTransform: Boolean,
    actual val immutable: Boolean,
    internal var headerValue: String?,
) {


    override fun toString(): String {
        var result = headerValue
        if (result == null) {
            result =
                buildString {
                    if (noCache) append("no-cache, ")
                    if (noStore) append("no-store, ")
                    if (maxAgeSeconds != -1) append("max-age=").append(maxAgeSeconds).append(", ")
                    if (sMaxAgeSeconds != -1) append("s-maxage=").append(sMaxAgeSeconds)
                        .append(", ")
                    if (isPrivate) append("private, ")
                    if (isPublic) append("public, ")
                    if (mustRevalidate) append("must-revalidate, ")
                    if (maxStaleSeconds != -1) append("max-stale=").append(maxStaleSeconds)
                        .append(", ")
                    if (minFreshSeconds != -1) append("min-fresh=").append(minFreshSeconds)
                        .append(", ")
                    if (onlyIfCached) append("only-if-cached, ")
                    if (noTransform) append("no-transform, ")
                    if (immutable) append("immutable, ")
                    if (isEmpty()) return ""
                    deleteRange(length - 2, length)
                }
            headerValue = result
        }
        return result
    }


  actual companion object {
      actual val FORCE_NETWORK = CacheControlBuilder()
          .noCache()
          .build()

      actual val FORCE_CACHE = CacheControlBuilder()
          .onlyIfCached()
          .maxStale(Int.MAX_VALUE.seconds)
          .build()

      actual fun parse(headers: Headers): CacheControl {
          var noCache = false
          var noStore = false
          var maxAgeSeconds = -1
          var sMaxAgeSeconds = -1
          var isPrivate = false
          var isPublic = false
          var mustRevalidate = false
          var maxStaleSeconds = -1
          var minFreshSeconds = -1
          var onlyIfCached = false
          var noTransform = false
          var immutable = false

          var canUseHeaderValue = true
          var headerValue: String? = null

          loop@ for (i in 0 until headers.size) {
              val name = headers.name(i)
              val value = headers.value(i)

              when {
                  name.equals("Cache-Control", ignoreCase = true) -> {
                      if (headerValue != null) {
                          // Multiple cache-control headers means we can't use the raw value.
                          canUseHeaderValue = false
                      } else {
                          headerValue = value
                      }
                  }
                  name.equals("Pragma", ignoreCase = true) -> {
                      // Pragma: no-cache is treated as Cache-Control: no-cache for HTTP/1.0 compatibility
                      canUseHeaderValue = false
                      if (value.equals("no-cache", ignoreCase = true)) {
                          noCache = true
                      }
                      continue@loop
                  }
                  else -> {
                      continue@loop
                  }
              }

              var pos = 0
              while (pos < value.length) {
                  val tokenStart = pos
                  pos = value.indexOfElement("=,;", pos)
                  val directive = value.substring(tokenStart, pos).trim()
                  val parameter: String?

                  if (pos == value.length || value[pos] == ',' || value[pos] == ';') {
                      pos++ // Consume ',' or ';' (if necessary).
                      parameter = null
                  } else {
                      pos++ // Consume '='.
                      pos = value.indexOfNonWhitespace(pos)

                      if (pos < value.length && value[pos] == '\"') {
                          // Quoted string.
                          pos++ // Consume '"' open quote.
                          val parameterStart = pos
                          pos = value.indexOf('"', pos)
                          parameter = value.substring(parameterStart, pos)
                          pos++ // Consume '"' close quote (if necessary).
                      } else {
                          // Unquoted string.
                          val parameterStart = pos
                          pos = value.indexOfElement(",;", pos)
                          parameter = value.substring(parameterStart, pos).trim()
                      }
                  }

                  when {
                      "no-cache".equals(directive, ignoreCase = true) -> {
                          noCache = true
                      }
                      "no-store".equals(directive, ignoreCase = true) -> {
                          noStore = true
                      }
                      "max-age".equals(directive, ignoreCase = true) -> {
                          maxAgeSeconds = parameter.toNonNegativeInt(-1)
                      }
                      "s-maxage".equals(directive, ignoreCase = true) -> {
                          sMaxAgeSeconds = parameter.toNonNegativeInt(-1)
                      }
                      "private".equals(directive, ignoreCase = true) -> {
                          isPrivate = true
                      }
                      "public".equals(directive, ignoreCase = true) -> {
                          isPublic = true
                      }
                      "must-revalidate".equals(directive, ignoreCase = true) -> {
                          mustRevalidate = true
                      }
                      "max-stale".equals(directive, ignoreCase = true) -> {
                          maxStaleSeconds = parameter.toNonNegativeInt(Int.MAX_VALUE)
                      }
                      "min-fresh".equals(directive, ignoreCase = true) -> {
                          minFreshSeconds = parameter.toNonNegativeInt(-1)
                      }
                      "only-if-cached".equals(directive, ignoreCase = true) -> {
                          onlyIfCached = true
                      }
                      "no-transform".equals(directive, ignoreCase = true) -> {
                          noTransform = true
                      }
                      "immutable".equals(directive, ignoreCase = true) -> {
                          immutable = true
                      }
                  }
              }
          }

          if (!canUseHeaderValue) {
              headerValue = null
          }

          return CacheControl(
              noCache = noCache,
              noStore = noStore,
              maxAgeSeconds = maxAgeSeconds,
              sMaxAgeSeconds = sMaxAgeSeconds,
              isPrivate = isPrivate,
              isPublic = isPublic,
              mustRevalidate = mustRevalidate,
              maxStaleSeconds = maxStaleSeconds,
              minFreshSeconds = minFreshSeconds,
              onlyIfCached = onlyIfCached,
              noTransform = noTransform,
              immutable = immutable,
              headerValue = headerValue,
          )
      }
  }
}

actual class CacheControlBuilder {
    internal var noCache: Boolean = false
    internal var noStore: Boolean = false
    internal var maxAgeSeconds = -1
    internal var maxStaleSeconds = -1
    internal var minFreshSeconds = -1
    internal var onlyIfCached: Boolean = false
    internal var noTransform: Boolean = false
    internal var immutable: Boolean = false

    actual fun noCache() = apply {
        this.noCache = true
    }

    actual fun noStore() = apply {
        this.noStore = true
    }

    actual fun onlyIfCached() = apply {
        this.onlyIfCached = true
    }

    actual fun noTransform() = apply {
        this.noTransform = true
    }

    actual fun immutable() = apply {
        this.immutable = true
    }

    internal fun maxAgeNonJvm(maxAge: Duration) =
        apply {
            val maxAgeSeconds = maxAge.inWholeSeconds
            require(maxAgeSeconds >= 0) { "maxAge < 0: $maxAgeSeconds" }
            this.maxAgeSeconds = maxAgeSeconds.commonClampToInt()
        }

    internal fun maxStaleNonJvm(maxStale: Duration) =
        apply {
            val maxStaleSeconds = maxStale.inWholeSeconds
            require(maxStaleSeconds >= 0) { "maxStale < 0: $maxStaleSeconds" }
            this.maxStaleSeconds = maxStaleSeconds.commonClampToInt()
        }

    internal fun minFreshNonJvm(minFresh: Duration) =
        apply {
            val minFreshSeconds = minFresh.inWholeSeconds
            require(minFreshSeconds >= 0) { "minFresh < 0: $minFreshSeconds" }
            this.minFreshSeconds = minFreshSeconds.commonClampToInt()
        }

    actual fun build(): CacheControl = CacheControl(
        noCache = noCache,
        noStore = noStore,
        maxAgeSeconds = maxAgeSeconds,
        sMaxAgeSeconds = -1,
        isPrivate = false,
        isPublic = false,
        mustRevalidate = false,
        maxStaleSeconds = maxStaleSeconds,
        minFreshSeconds = minFreshSeconds,
        onlyIfCached = onlyIfCached,
        noTransform = noTransform,
        immutable = immutable,
        headerValue = null,
    )
}

actual fun CacheControlBuilder.maxAge(maxAge: Duration) = apply { maxAgeNonJvm(maxAge) }

actual fun CacheControlBuilder.maxStale(maxStale: Duration) = apply { maxStaleNonJvm(maxStale) }

actual fun CacheControlBuilder.minFresh(minFresh: Duration) = apply { minFreshNonJvm(minFresh) }

internal fun Long.commonClampToInt(): Int =
  when {
    this > Int.MAX_VALUE -> Int.MAX_VALUE
    else -> toInt()
  }

/**
 * Returns the next index in this at or after [startIndex] that is a character from
 * [characters]. Returns the input length if none of the requested characters can be found.
 */
private fun String.indexOfElement(
  characters: String,
  startIndex: Int = 0,
): Int {
  for (i in startIndex until length) {
    if (this[i] in characters) {
      return i
    }
  }
  return length
}

private fun String.indexOfNonWhitespace(startIndex: Int): Int {
  for (i in startIndex until length) {
    if (!this[i].isWhitespace()) return i
  }
  return -1
}

/**
 * Returns this as a non-negative integer, or 0 if it is negative, or [Int.MAX_VALUE] if it is too
 * large, or [defaultValue] if it cannot be parsed.
 */
internal fun String?.toNonNegativeInt(defaultValue: Int): Int {
  try {
    val value = this?.toLong() ?: return defaultValue
    return when {
      value > Int.MAX_VALUE -> Int.MAX_VALUE
      value < 0 -> 0
      else -> value.toInt()
    }
  } catch (_: NumberFormatException) {
    return defaultValue
  }
}