package kmpcommon

import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
fun Clock.nowMs(): Long = now().toEpochMilliseconds()