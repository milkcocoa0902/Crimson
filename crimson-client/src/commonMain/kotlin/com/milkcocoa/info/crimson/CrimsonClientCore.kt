package com.milkcocoa.info.crimson

import com.milkcocoa.info.crimson.core.CrimsonData
import kotlinx.coroutines.flow.SharedFlow
import kotlin.time.Duration

/**
 * Handler for crimson client.
 * @param UPSTREAM upstream data type (send to server)
 * @param DOWNSTREAM downstream data type (received from server)
 * @see CrimsonClientCore
 * @see CrimsonData
 *
 */
interface CrimsonHandler<UPSTREAM: CrimsonData, DOWNSTREAM: CrimsonData>{
    /**
     * Called when the connection is established.
     * @param crimson crimson client core
     * @param flow flow of downstream data
     * @see CrimsonClientCore
     */
    suspend fun onConnect(crimson: CrimsonClientCore<UPSTREAM, DOWNSTREAM>, flow: SharedFlow<DOWNSTREAM>)

    /**
     * Called when the connection is closed.
     * @param code close code
     * @param reason close reason
     */
    suspend fun onClosed(code: Short, reason: String)

    /**
     * Called when an error occurs.
     * @param e error
     */
    suspend fun onError(e: Throwable)
}


/**
 * Core interface of crimson client.
 * @param UPSTREAM upstream data type (send to server)
 * @param DOWNSTREAM downstream data type (received from server)
 * @see CrimsonHandler
 * @see CrimsonData
 */
interface CrimsonClientCore<UPSTREAM: CrimsonData, DOWNSTREAM: CrimsonData>{
    /**
     * Send data to server.
     * @param data data to send
     * @throws IllegalStateException if the connection is closed.
     */
    suspend fun send(data: UPSTREAM)
    /**
     * Execute command.
     * @param command crimson command
     * @see CrimsonCommand
     */
    suspend fun execute(command: CrimsonCommand)
    /**
     * Receive data from server.
     * @param timeout timeout duration
     * @return received data
     */
    suspend fun receive(timeout: Duration): DOWNSTREAM
}

