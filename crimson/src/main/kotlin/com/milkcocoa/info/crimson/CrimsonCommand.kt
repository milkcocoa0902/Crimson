package com.milkcocoa.info.crimson

import kotlin.time.Duration

sealed interface CrimsonCommand {
    data object Connect: CrimsonCommand
    data class Disconnect(val code: Int, val reason: String): CrimsonCommand
    data class Ping(val frame: ByteArray): CrimsonCommand
    data class Pong(val frame: ByteArray): CrimsonCommand
    data object StartHealthCheck: CrimsonCommand
}