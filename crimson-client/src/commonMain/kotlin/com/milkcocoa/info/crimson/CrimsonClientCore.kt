package com.milkcocoa.info.crimson

import com.milkcocoa.info.crimson.core.CrimsonData
import kotlinx.coroutines.flow.SharedFlow
import kotlin.time.Duration

interface CrimsonHandler<SND: CrimsonData, RCV: CrimsonData>{
    suspend fun onConnect(crimson: CrimsonClientCore<SND, RCV>, flow: SharedFlow<RCV>)
    suspend fun onClosed(code: Int, reason: String)
    suspend fun onError(e: Throwable)
}

interface CrimsonClientCore<SND: CrimsonData, RCV: CrimsonData>{
    suspend fun send(data: SND)
    suspend fun execute(command: CrimsonCommand)
    suspend fun receive(timeout: Duration): RCV?
}

