package com.milkcocoa.info.crimson

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Retry policy for crimson client.
 */
sealed class RetryPolicy{
    /**
     * Never retry.
     */
    data object Never : RetryPolicy()

    /**
     * Retry with fixed delay.
     * @param delay retry delay.
     */
    data class SimpleDelay(val delay: Duration): RetryPolicy(){
        init {
            if(delay < 15.seconds){ error("The delay of $delay seconds is less than 15 seconds!") }
        }
    }

    /**
     * Retry with exponential delay.
     * @param initial initial delay.
     * @param max max delay.
     */
    data class ExponentialDelay(val initial: Duration, val max: Duration = Duration.INFINITE): RetryPolicy(){
        init {
            if(initial < 15.seconds){ error("The delay of $initial seconds is less than 15 seconds!") }
        }
    }
}