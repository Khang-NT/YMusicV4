package kmpcommon

val Int.kilobytes get() = this * 1024L
val Long.kilobytes get() = this * 1024L
val Int.megabytes get() = this * 1024 * 1024L
val Long.megabytes get() = this * 1024 * 1024L
val Int.gigabytes get() = this * 1024 * 1024 * 1024L
val Long.gigabytes get() = this * 1024 * 1024 * 1024L