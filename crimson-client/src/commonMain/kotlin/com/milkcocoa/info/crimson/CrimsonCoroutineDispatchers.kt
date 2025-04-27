package com.milkcocoa.info.crimson

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Coroutine dispatcher for crimson client.
 */
expect object CrimsonCoroutineDispatchers{
    /**
     * Main dispatcher.
     */
    val main: CoroutineDispatcher
    /**
     * IO dispatcher.
     */
    val io: CoroutineDispatcher
    /**
     * Default dispatcher.
     */
    val default: CoroutineDispatcher
}