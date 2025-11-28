package kmphttp

import kotlin.jvm.JvmInline

@JvmInline
value class LowercaseString internal constructor(val string: String)

fun String.toLowercaseString(): LowercaseString = LowercaseString(this.lowercase())