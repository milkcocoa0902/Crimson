package com.milkcocoa.info.crimson.server


import com.milkcocoa.info.crimson.core.CrimsonData
import io.ktor.websocket.CloseReason
import kotlin.time.Duration

/**
 * Core interface of crimson server.
 * @param UPSTREAM upstream data type (received from clients)
 * @param DOWNSTREAM downstream data type (send to clients)
 */
interface CrimsonServerCore<UPSTREAM: CrimsonData, DOWNSTREAM: CrimsonData> {
    /**
     * Send data to clients.
     * @param data data to send
     * @throws IllegalStateException if the connection is closed.
     */
    suspend fun send(data: DOWNSTREAM)

    /**
     * Receive data from clients.
     * @param timeout timeout duration
     * @return received data
     */
    suspend fun receive(timeout: Duration): UPSTREAM

    /**
     * Close the connection.
     * @param code close code
     * @param reason close reason
     * @throws IllegalStateException if the connection is already closed.
     */
    suspend fun close(code: Short = CloseReason.Codes.NORMAL.code, reason: String = "")
}

