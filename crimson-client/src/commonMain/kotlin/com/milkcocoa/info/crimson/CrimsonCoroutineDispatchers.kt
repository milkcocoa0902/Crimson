package com.milkcocoa.info.crimson

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Coroutine dispatcher for crimson client.
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
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