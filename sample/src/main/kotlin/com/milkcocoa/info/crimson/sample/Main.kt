package com.milkcocoa.info.crimson.sample

import com.milkcocoa.info.crimson.ConnectionInfo
import com.milkcocoa.info.crimson.CrimsonClient
import com.milkcocoa.info.crimson.CrimsonCommand
import com.milkcocoa.info.crimson.CrimsonClientCore
import com.milkcocoa.info.crimson.CrimsonCoroutineDispatchers
import com.milkcocoa.info.crimson.CrimsonHandler
import com.milkcocoa.info.crimson.RetryPolicy
import com.milkcocoa.info.crimson.WebSocketEndpointProvider
import com.milkcocoa.info.crimson.core.CrimsonData
import com.milkcocoa.info.crimson.server.Crimson
import com.milkcocoa.info.crimson.server.broadcast
import com.milkcocoa.info.crimson.server.crimson
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds


@Serializable
data class SamplePayload(val a: String): CrimsonData

class Payload: CrimsonData


fun Application.test(){
    install(Crimson){
        crimsonConfig("test"){

        }
    }

    routing {
        crimson<Payload, Payload>(
            path = "/test",
            config = "test"
        ){ sessionRegistry ->
            launch {
                sessionRegistry.crimsonServerSessionFlow.collect {
                    println(it)
                }
            }

            launch {
                incomingMessageFlow.collect {
                    sessionRegistry.all.broadcast(it)
                }
            }
        }
    }
}



fun main(){
    val crimsonClient = CrimsonClient<SamplePayload, SamplePayload>{
        crimsonHandler = object: CrimsonHandler<SamplePayload, SamplePayload> {
            override suspend fun onConnect(crimson: CrimsonClientCore<SamplePayload, SamplePayload>, flow: SharedFlow<SamplePayload>) {
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
        crimsonClient.execute(CrimsonCommand.Connect)
    }

    CoroutineScope(Dispatchers.Default).launch {
        crimsonClient.connectionStatus.collect { isConnected ->println(isConnected) }
    }
    CoroutineScope(Dispatchers.Default).launch {
        crimsonClient.incomingMessage.collect { incoming -> println(incoming) }
    }

    CoroutineScope(Dispatchers.Default).launch {
        crimsonClient.send(SamplePayload("hello"))
    }


    Thread.sleep(60.minutes.inWholeMilliseconds)
}