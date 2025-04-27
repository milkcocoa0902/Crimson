package com.milkcocoa.info.crimson.sample.server

import com.milkcocoa.info.crimson.sample.model.ChatMessage
import com.milkcocoa.info.crimson.sample.model.ChatResponse
import com.milkcocoa.info.crimson.server.Crimson
import com.milkcocoa.info.crimson.server.CrimsonSessionRegistry
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
import kotlin.time.Duration.Companion.seconds

fun Application.test(){
    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 15.seconds
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    install(Crimson){
        crimsonConfig("test"){
            incomingSerializer = ChatMessage.serializer()
            outgoingSerializer = ChatResponse.serializer()
        }
    }

    routing {
        val crimsonSessionRegistry = CrimsonSessionRegistry<ChatMessage, ChatResponse>()
        launch {
            crimsonSessionRegistry.crimsonServerSessionFlow.collect {
                println(it)
            }
        }
        crimson<ChatMessage, ChatResponse>(
            path = "/test",
            crimsonSessionRegistry = crimsonSessionRegistry,
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