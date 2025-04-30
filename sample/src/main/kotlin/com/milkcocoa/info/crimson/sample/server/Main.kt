package com.milkcocoa.info.crimson.sample.server

import com.milkcocoa.info.crimson.core.ContentConverter
import com.milkcocoa.info.crimson.sample.model.ChatMessage
import com.milkcocoa.info.crimson.sample.model.ChatResponse
import com.milkcocoa.info.crimson.server.Crimson
import com.milkcocoa.info.crimson.server.cluster.CrimsonLocalSessionCluster
import com.milkcocoa.info.crimson.server.broadcast
import com.milkcocoa.info.crimson.server.crimson
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalSerializationApi::class)
fun Application.test(){
    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 15.seconds
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    install(Crimson){
        crimsonConfig("test"){
            contentConverter = ContentConverter.Binary.Protobuf(
                protobuf = ProtoBuf.Default,
                upstreamSerializer = ChatMessage.serializer(),
                downstreamSerializer = ChatResponse.serializer()
            )
        }
    }

    routing {
        val crimsonSessionCluster = CrimsonLocalSessionCluster<ChatMessage, ChatResponse>()
        launch {
            crimsonSessionCluster.crimsonServerSessionFlow.collect {
                println(it)
            }
        }
        crimson<ChatMessage, ChatResponse>(
            path = "/test",
            crimsonSessionCluster = crimsonSessionCluster,
            config = "test"
        ){ sessionRegistry ->
            incomingMessageFlow.collect {
                println(it)
                sessionRegistry.all.broadcast(ChatResponse(it.text))
            }
        }
    }
}


fun main(){
    embeddedServer(CIO, port = 54321){
        test()
    }.start(wait = true)
}