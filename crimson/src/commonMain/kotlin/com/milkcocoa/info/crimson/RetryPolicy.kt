package com.milkcocoa.info.crimson

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

sealed class RetryPolicy{
    data object Never : RetryPolicy()
    data class SimpleDelay(val delay: Duration): RetryPolicy(){
        init {
            if(delay < 15.seconds){ error("The delay of $delay seconds is less than 15 seconds!") }
        }
    }
    data class ExponentialDelay(val initial: Duration, val max: Duration = Duration.INFINITE): RetryPolicy(){
        init {
            if(initial < 15.seconds){ error("The delay of $initial seconds is less than 15 seconds!") }
        }
    }
}