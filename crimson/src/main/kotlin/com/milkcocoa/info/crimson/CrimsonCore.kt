package com.milkcocoa.info.crimson

import kotlinx.coroutines.flow.SharedFlow

interface CrimsonHandler<SND: CrimsonData, RCV: CrimsonData>{
    suspend fun onConnect(crimson: CrimsonCore<SND, RCV>, flow: SharedFlow<RCV>)
    suspend fun onClosed(code: Int, reason: String)
    suspend fun onError(e: Throwable)
}

interface CrimsonCore<SND: CrimsonData, RCV: CrimsonData>{
    suspend fun send(data: SND)
    suspend fun execute(command: CrimsonCommand)
}

