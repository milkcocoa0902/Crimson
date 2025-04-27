package com.milkcocoa.info.crimson

import io.ktor.websocket.CloseReason

/**
 * Command for crimson client.
 */
sealed interface CrimsonCommand {
    /**
     * Connect command.
     */
    data object Connect: CrimsonCommand

    /**
     * Disconnect command.
     * @param code close code
     * @param reason close reason
     * @param abnormally abnormally close flag
     *
     */
    data class Disconnect(
        val code: Short = CloseReason.Codes.NORMAL.code,
        val reason: String = "Close normally",
        val abnormally: Boolean = false
    ): CrimsonCommand

    /**
     * Ping command.
     * @param frame ping frame
     */
    data class Ping(val frame: ByteArray): CrimsonCommand

    /**
     * Pong command.
     * @param frame pong frame
     */
    data class Pong(val frame: ByteArray): CrimsonCommand
}