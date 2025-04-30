package com.milkcocoa.info.crimson

import com.milkcocoa.info.crimson.core.ContentConverter
import com.milkcocoa.info.crimson.core.CrimsonData
import io.ktor.serialization.WebsocketContentConverter
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.receiveDeserialized
import io.ktor.server.websocket.webSocket
import io.ktor.util.reflect.TypeInfo
import io.ktor.utils.io.charsets.Charset
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

fun Application.module() {
    install(WebSockets){
        maxFrameSize = 8192
    }

    routing {
        webSocket("/") {
            val message = receiveDeserialized<UpstreamMessage>()
            println("Client connected!")

            for (frame in incoming) {
                when (frame) {
                    is Frame.Text -> {
                        val f = frame.readText()
                        println("Received text: $f")
                        send(Frame.Text(f))
                    }
                    is Frame.Ping -> {
                        println("Ping received: ${frame.data.decodeToString()}")
                        send(Frame.Pong(frame.data))
                    }
                    is Frame.Pong -> {
                        println("Pong received (unexpected): ${frame.data.decodeToString()}")
                    }
                    else -> {
                        println("Other frame: ${frame.frameType.name}")
                    }
                }
            }

            println("Client disconnected!")
        }
        webSocket("/no_ping_pong") {
            println("Client connected!")

            for (frame in incoming) {
                when (frame) {
                    is Frame.Text -> {
                        val f = frame.readText()
                        println("Received: ${f}")
                        send(Frame.Text(f))
                    }
                    else -> Unit
                }
            }

            println("Client disconnected!")
        }
    }
}

fun startTestServer(
    port: Int = 54321
)= embeddedServer(CIO, port = port) {
    module() // ← 上で定義した
}.start(wait = false)


@Serializable
data class SamplePayload(val a: String): CrimsonData {
    override fun equals(other: Any?): Boolean {
        return (other is SamplePayload) && (this.a == other.a)
    }
}

@Serializable
data class UpstreamMessage(val a: String): CrimsonData

@Serializable
data class DownstreamMessage(val a: String): CrimsonData

class CrimsonTest {
    var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null

//    @BeforeTest
//    fun before(){
//        server = startTestServer()
//    }
//
//    @AfterTest
//    fun after(){
//        server?.stop()
//        server = null
//    }

    @Test
    fun testConnectionStateIntoConnected() = runTest{
        val crimsonClient = CrimsonClient{
            crimsonHandler = object: CrimsonHandler<SamplePayload, SamplePayload> {
                override suspend fun onConnect(crimson: CrimsonClientCore<SamplePayload, SamplePayload>, flow: SharedFlow<SamplePayload>) { }

                override suspend fun onError(e: Throwable) { }

                override suspend fun onClosed(code: Short, reason: String) {}
            }

            webSocketEndpointProvider = object: WebSocketEndpointProvider {
                override suspend fun build(): ConnectionInfo {
                    return ConnectionInfo("ws://127.0.0.1:54321")
                }
            }

            retryPolicy = RetryPolicy.SimpleDelay(30.seconds)
            dispatcher = CrimsonCoroutineDispatchers.io
            contentConverter = ContentConverter.Text.Json(
                json = Json,
                upstreamSerializer = SamplePayload.serializer(),
                downstreamSerializer = SamplePayload.serializer()
            )
        }

        val actual = async {
            crimsonClient.connectionStatus.first{ it == ConnectionState.CONNECTED }
        }
        crimsonClient.execute(CrimsonCommand.Connect)
        assertEquals(ConnectionState.CONNECTED, actual.await())
    }

    @Test
    fun testConnectionStateIntoDisconnected() = runTest{
        val crimsonClient = CrimsonClient{
            crimsonHandler = object: CrimsonHandler<SamplePayload, SamplePayload> {
                override suspend fun onConnect(crimson: CrimsonClientCore<SamplePayload, SamplePayload>, flow: SharedFlow<SamplePayload>) { }

                override suspend fun onError(e: Throwable) { }

                override suspend fun onClosed(code: Short, reason: String) {}
            }

            webSocketEndpointProvider = object: WebSocketEndpointProvider {
                override suspend fun build(): ConnectionInfo {
                    return ConnectionInfo("ws://127.0.0.1:54321")
                }
            }

            retryPolicy = RetryPolicy.SimpleDelay(30.seconds)
            dispatcher = CrimsonCoroutineDispatchers.io
            contentConverter = ContentConverter.Text.Json(
                json = Json,
                upstreamSerializer = SamplePayload.serializer(),
                downstreamSerializer = SamplePayload.serializer()
            )
        }

        val actual = async {
            crimsonClient.connectionStatus
                .dropWhile{ it != ConnectionState.CONNECTED }
                .first { it == ConnectionState.CLOSED }
        }
        crimsonClient.execute(CrimsonCommand.Connect)
        delay(500.milliseconds)
        crimsonClient.execute(CrimsonCommand.Disconnect(1000, "test"))
        assertEquals(ConnectionState.CLOSED, actual.await())
    }


    @Test
    fun testHealthCheck() = runTest{
        val crimsonClient = CrimsonClient{
            crimsonHandler = object: CrimsonHandler<SamplePayload, SamplePayload> {
                override suspend fun onConnect(crimson: CrimsonClientCore<SamplePayload, SamplePayload>, flow: SharedFlow<SamplePayload>) {
                }

                override suspend fun onError(e: Throwable) { }

                override suspend fun onClosed(code: Short, reason: String) {
                    println("onClosed: $code $reason")
                }
            }

            webSocketEndpointProvider = object: WebSocketEndpointProvider {
                override suspend fun build(): ConnectionInfo {
                    return ConnectionInfo("ws://127.0.0.1:54321")
                }
            }

            retryPolicy = RetryPolicy.SimpleDelay(15.seconds)
            dispatcher = CrimsonCoroutineDispatchers.io
            contentConverter = ContentConverter.Text.Json(
                json = Json,
                upstreamSerializer = SamplePayload.serializer(),
                downstreamSerializer = SamplePayload.serializer()
            )
        }

        launch {
            crimsonClient.connectionStatus.first { it == ConnectionState.RETRYING }
            error("")
        }

        launch {
            crimsonClient.connectionStatus.collect{ println(it) }
        }

        crimsonClient.execute(CrimsonCommand.Connect)
        assertEquals(ConnectionState.CONNECTED, crimsonClient.connectionStatus.value)
    }

    @Test
    fun testHealthCheckFailed() = runTest{
        val crimsonClient = CrimsonClient{
            crimsonHandler = object: CrimsonHandler<SamplePayload, SamplePayload> {
                override suspend fun onConnect(crimson: CrimsonClientCore<SamplePayload, SamplePayload>, flow: SharedFlow<SamplePayload>) {
                }

                override suspend fun onError(e: Throwable) { }

                override suspend fun onClosed(code: Short, reason: String) {
                    println("onClosed: $code $reason")
                }
            }

            webSocketEndpointProvider = object: WebSocketEndpointProvider {
                override suspend fun build(): ConnectionInfo {
                    return ConnectionInfo("ws://127.0.0.1:54321/no_ping_pong")
                }
            }

            retryPolicy = RetryPolicy.SimpleDelay(15.seconds)
            dispatcher = CrimsonCoroutineDispatchers.io
            contentConverter = ContentConverter.Text.Json(
                json = Json,
                upstreamSerializer = SamplePayload.serializer(),
                downstreamSerializer = SamplePayload.serializer()
            )
        }

        val actual = async {
            crimsonClient.connectionStatus
                .dropWhile { it != ConnectionState.RETRYING }
                .first { it == ConnectionState.CONNECTED }
        }

        crimsonClient.execute(CrimsonCommand.Connect)
        assertEquals(ConnectionState.CONNECTED, actual.await())
    }





    @Test
    fun crimsonTest() = runTest{
        val expected = SamplePayload("hello")
        val crimsonClient = CrimsonClient{
            crimsonHandler = object: CrimsonHandler<SamplePayload, SamplePayload> {
                override suspend fun onConnect(crimson: CrimsonClientCore<SamplePayload, SamplePayload>, flow: SharedFlow<SamplePayload>) {
                    crimson.send(expected)
                }

                override suspend fun onError(e: Throwable) { }

                override suspend fun onClosed(code: Short, reason: String) {
                    println("onClosed: $code $reason")
                }
            }

            webSocketEndpointProvider = object: WebSocketEndpointProvider {
                override suspend fun build(): ConnectionInfo {
                    return ConnectionInfo("ws://127.0.0.1:54321")
                }
            }

            retryPolicy = RetryPolicy.SimpleDelay(30.seconds)
            dispatcher = CrimsonCoroutineDispatchers.io
            contentConverter = ContentConverter.Text.Json(
                json = Json,
                upstreamSerializer = SamplePayload.serializer(),
                downstreamSerializer = SamplePayload.serializer()
            )
        }

        crimsonClient.execute(CrimsonCommand.Connect)
        assertEquals(
            expected,
            crimsonClient.incomingMessage.first()
        )
    }
}