package kmphttp

/**
 * kmpHttp library version and platform information.
 */
object KmpHttp {
    /** Library version string. */
    const val VERSION: String = "1.0.0"

    /**
     * Default User-Agent header value.
     * Format: "kmpHttp/{version} ({platform} {platformVersion})"
     */
    val userAgent: String by lazy {
        "kmpHttp/$VERSION (${Platform.name} ${Platform.version})"
    }
}

/**
 * Platform information provider.
 */
internal expect object Platform {
    /** Platform name (e.g., "iOS", "macOS"). */
    val name: String

    /** Platform version (e.g., "17.0", "14.0"). */
    val version: String
}
