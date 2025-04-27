package com.milkcocoa.info.crimson

import kotlinx.coroutines.CoroutineDispatcher

expect object CrimsonCoroutineDispatchers{
    val main: CoroutineDispatcher
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
}