package com.milkcocoa.info.crimson

import io.ktor.websocket.CloseReason

sealed interface CrimsonCommand {
    data object Connect: CrimsonCommand
    data class Disconnect(
        val code: Short = CloseReason.Codes.NORMAL.code,
        val reason: String = "Close normally",
        val abnormally: Boolean = false
    ): CrimsonCommand
    data class Ping(val frame: ByteArray): CrimsonCommand
    data class Pong(val frame: ByteArray): CrimsonCommand
}