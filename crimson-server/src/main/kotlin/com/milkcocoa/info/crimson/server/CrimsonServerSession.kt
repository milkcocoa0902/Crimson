package com.milkcocoa.info.crimson.server

import com.milkcocoa.info.crimson.core.ContentConverter
import com.milkcocoa.info.crimson.core.CrimsonData
import io.ktor.server.websocket.WebSocketServerSession
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime

class CrimsonServerSession<UPSTREAM: CrimsonData, DOWNSTREAM: CrimsonData>(
    config: CrimsonServerConfig<UPSTREAM, DOWNSTREAM>,
    private val session: WebSocketServerSession
) : CrimsonServerCore<UPSTREAM, DOWNSTREAM>{
    private val crimsonHandler: CrimsonHandler<UPSTREAM, DOWNSTREAM>? = config.crimsonHandler
    private val dispatcher: CoroutineDispatcher = config.dispatcher
    private val contentConverter = config.contentConverter

    @OptIn(ExperimentalTime::class)
    val connectedAt = Clock.System.now()


    val scope: CoroutineScope = CoroutineScope(dispatcher)

    private val incomingFrameFlow = session.incoming.consumeAsFlow().shareIn(scope, started = SharingStarted.Companion.Eagerly, replay = 0)

    private val _incomingMessageFlow: MutableSharedFlow<UPSTREAM> = MutableSharedFlow()
    val incomingMessageFlow: SharedFlow<UPSTREAM> get() = _incomingMessageFlow.shareIn(scope, started = SharingStarted.Companion.Eagerly, replay = 0)

    init {
        when(contentConverter){
            is ContentConverter.Binary -> {
                incomingFrameFlow
                    .filterIsInstance<Frame.Binary>()
                    .map { frameBinary ->  frameBinary.readBytes() }
                    .mapNotNull { bytes -> runCatching{ (contentConverter as ContentConverter.Binary).decodeUpstream(bytes) }.getOrNull() }
                    .catch { e ->
                        crimsonHandler?.onError(e)
                        this@CrimsonServerSession.close(code = 4001, "incoming frame error")
                    }.onEach {
                        _incomingMessageFlow.emit(it)
                    }.launchIn(scope)
            }
            is ContentConverter.Text -> {
                incomingFrameFlow
                    .filterIsInstance<Frame.Text>()
                    .map { frameText -> frameText.readText() }
                    .mapNotNull { text -> runCatching{ (contentConverter as ContentConverter.Text).decodeUpstream(text) }.getOrNull() }
                    .catch { e ->
                        crimsonHandler?.onError(e)
                        this@CrimsonServerSession.close(code = 4001, "incoming frame error")
                    }.onEach {
                        _incomingMessageFlow.emit(it)
                    }.launchIn(scope)

            }
            is ContentConverter.Nothing -> {
                println("ContentConverter.Nothing is selected. No data will be received. ")
            }
        }
        scope.launch {
            while (session.isActive){
                delay(15_000.milliseconds)
            }
            // 実際にはscope.cancelだけ引き起こす
            close(code = 1001, reason = "session closed by server")
        }


        scope.launch {
            crimsonHandler?.onConnect(
                crimson = this@CrimsonServerSession,
                flow = incomingMessageFlow
            )
        }
    }


    override suspend fun send(data: DOWNSTREAM) {
        if(!session.isActive) return
        when(contentConverter){
            is ContentConverter.Binary -> {
                session.send(
                    frame = Frame.Binary(
                        fin = true,
                        data = contentConverter.encodeDownstream(data)
                    )
                )
            }

            is ContentConverter.Text -> {
                session.send(
                    frame = Frame.Text(
                        text = contentConverter.encodeDownstream(data)
                    )
                )
            }

            is ContentConverter.Nothing -> {
                println("ContentConverter.Nothing is selected. No data will be sent.")
            }
        }
    }


    override suspend fun receive(timeout: Duration): UPSTREAM {
        return withTimeout(timeout) {
            return@withTimeout incomingMessageFlow.first()
        }
    }

    override suspend fun close(code: Short, reason: String) {
        if(session.isActive){
            session.close(CloseReason(code = code, message = reason))
            crimsonHandler?.onClosed(code = code, reason = reason)
        }

        if(scope.isActive){
            scope.cancel()
        }
    }
}