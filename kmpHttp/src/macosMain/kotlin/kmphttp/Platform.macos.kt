package kmphttp

import platform.Foundation.NSProcessInfo

internal actual object Platform {
    actual val name: String
        get() = "macOS"

    actual val version: String
        get() = NSProcessInfo.processInfo.operatingSystemVersionString
}
