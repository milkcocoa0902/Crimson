package com.milkcocoa.info.crimson

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

actual object CrimsonCoroutineDispatchers{
    actual val main: CoroutineDispatcher = Dispatchers.Main
    actual val io: CoroutineDispatcher = Dispatchers.IO
    actual val default: CoroutineDispatcher = Dispatchers.Default
}