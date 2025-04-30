package com.milkcocoa.info.crimson.sample.client

import com.milkcocoa.info.crimson.ConnectionInfo
import com.milkcocoa.info.crimson.CrimsonClient
import com.milkcocoa.info.crimson.CrimsonCommand
import com.milkcocoa.info.crimson.CrimsonClientCore
import com.milkcocoa.info.crimson.CrimsonCoroutineDispatchers
import com.milkcocoa.info.crimson.CrimsonHandler
import com.milkcocoa.info.crimson.RetryPolicy
import com.milkcocoa.info.crimson.WebSocketEndpointProvider
import com.milkcocoa.info.crimson.core.ContentConverter
import com.milkcocoa.info.crimson.sample.model.ChatMessage
import com.milkcocoa.info.crimson.sample.model.ChatResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds



@OptIn(ExperimentalSerializationApi::class)
fun main(){
    val crimsonClient = CrimsonClient{
        crimsonHandler = object: CrimsonHandler<ChatMessage, ChatResponse> {
            override suspend fun onConnect(crimson: CrimsonClientCore<ChatMessage, ChatResponse>, flow: SharedFlow<ChatResponse>) {
                crimson.send(ChatMessage("hello"))
            }

            override suspend fun onError(e: Throwable) {
                println(e)
            }

            override suspend fun onClosed(code: Short, reason: String) {
                println("$code $reason")
            }
        }

        webSocketEndpointProvider = object: WebSocketEndpointProvider {
            override suspend fun build(): ConnectionInfo {
                return ConnectionInfo("ws://127.0.0.1:54321/test")
            }
        }

        retryPolicy = RetryPolicy.SimpleDelay(30.seconds)
        dispatcher = CrimsonCoroutineDispatchers.io
        contentConverter = ContentConverter.Binary.Protobuf(
            protobuf = ProtoBuf.Default,
            upstreamSerializer = ChatMessage.serializer(),
            downstreamSerializer = ChatResponse.serializer()
        )
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
    }


    Thread.sleep(60.minutes.inWholeMilliseconds)
}