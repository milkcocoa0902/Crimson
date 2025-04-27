package com.milkcocoa.info.crimson.server


import com.milkcocoa.info.crimson.core.CrimsonData
import io.ktor.websocket.CloseReason
import kotlin.time.Duration

interface CrimsonServerCore<UPSTREAM: CrimsonData, DOWNSTREAM: CrimsonData> {
    suspend fun send(data: DOWNSTREAM)
    suspend fun receive(timeout: Duration): UPSTREAM
    suspend fun close(code: Short = CloseReason.Codes.NORMAL.code, reason: String = "")
}

