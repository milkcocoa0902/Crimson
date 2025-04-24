package com.milkcocoa.info.crimson.sample

import com.milkcocoa.info.crimson.ConnectionInfo
import com.milkcocoa.info.crimson.CrimsonData
import com.milkcocoa.info.crimson.Crimson
import com.milkcocoa.info.crimson.CrimsonCommand
import com.milkcocoa.info.crimson.CrimsonCore
import com.milkcocoa.info.crimson.CrimsonCoroutineDispatchers
import com.milkcocoa.info.crimson.CrimsonHandler
import com.milkcocoa.info.crimson.RetryPolicy
import com.milkcocoa.info.crimson.WebSocketEndpointProvider
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds


@Serializable
data class SamplePayload(val a: String): CrimsonData


fun main(){
    val crimson = Crimson<SamplePayload, SamplePayload>{
        crimsonHandler = object: CrimsonHandler<SamplePayload, SamplePayload> {
            override suspend fun onConnect(crimson: CrimsonCore<SamplePayload, SamplePayload>, flow: SharedFlow<SamplePayload>) {
                crimson.execute(CrimsonCommand.StartHealthCheck)
                CoroutineScope(Dispatchers.Default).launch {
                    flow.collect { payload -> println(payload.a) }
                }
            }

            override suspend fun onError(e: Throwable) {
                println(e)
            }

            override suspend fun onClosed(code: Int, reason: String) {
                println("$code $reason")
            }
        }

        webSocketEndpointProvider = object: WebSocketEndpointProvider {
            override suspend fun build(): ConnectionInfo {
                return ConnectionInfo("ws://127.0.0.1:54321")
            }
        }

        retryPolicy = RetryPolicy.SimpleDelay(30.seconds)
        dispatcher = CrimsonCoroutineDispatchers.io
        json = Json
        incomingSerializer = SamplePayload.serializer()
        outgoingSerializer = SamplePayload.serializer()
    }

    runBlocking {
        crimson.execute(CrimsonCommand.Connect)
    }

    CoroutineScope(Dispatchers.Default).launch {
        crimson.connectionStatus.collect { isConnected ->println(isConnected) }
    }
    CoroutineScope(Dispatchers.Default).launch {
        crimson.incoming.collect { incoming -> println(incoming) }
    }

    CoroutineScope(Dispatchers.Default).launch {
        crimson.send(SamplePayload("hello"))
    }


    Thread.sleep(60.minutes.inWholeMilliseconds)
}