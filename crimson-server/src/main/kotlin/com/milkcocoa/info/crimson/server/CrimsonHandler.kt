package com.milkcocoa.info.crimson.server

import com.milkcocoa.info.crimson.core.CrimsonData
import kotlinx.coroutines.flow.SharedFlow

/**
 * Handler for crimson server.
 * @param UPSTREAM upstream data type (received from clients)
 * @param DOWNSTREAM downstream data type (send to clients)
 * @see CrimsonServerCore
 * @see CrimsonData
 */
interface CrimsonHandler<UPSTREAM: CrimsonData, DOWNSTREAM: CrimsonData>{
    /**
     * Called when the connection is established.
     * @param crimson crimson server core
     * @param flow flow of upstream data
     * @see CrimsonServerCore
     */
    suspend fun onConnect(crimson: CrimsonServerCore<UPSTREAM, DOWNSTREAM>, flow: SharedFlow<UPSTREAM>)

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