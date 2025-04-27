package com.milkcocoa.info.crimson.server

import com.milkcocoa.info.crimson.core.CrimsonData
import kotlinx.coroutines.flow.SharedFlow

interface CrimsonHandler<UPSTREAM: CrimsonData, DOWNSTREAM: CrimsonData>{
    suspend fun onConnect(crimson: CrimsonServerCore<UPSTREAM, DOWNSTREAM>, flow: SharedFlow<UPSTREAM>)
    suspend fun onClosed(code: Short, reason: String)
    suspend fun onError(e: Throwable)
}