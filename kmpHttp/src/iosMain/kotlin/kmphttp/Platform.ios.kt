package kmphttp

import platform.UIKit.UIDevice

internal actual object Platform {
    actual val name: String
        get() = UIDevice.currentDevice.systemName

    actual val version: String
        get() = UIDevice.currentDevice.systemVersion
}
