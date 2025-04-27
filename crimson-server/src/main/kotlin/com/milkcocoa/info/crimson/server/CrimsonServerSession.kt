package com.milkcocoa.info.crimson.server

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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

class CrimsonServerSession<UPSTREAM: CrimsonData, DOWNSTREAM: CrimsonData>(
    config: CrimsonServerConfig<UPSTREAM, DOWNSTREAM>,
    private val session: WebSocketServerSession
) : CrimsonServerCore<UPSTREAM, DOWNSTREAM>{
    private val crimsonHandler: CrimsonHandler<UPSTREAM, DOWNSTREAM>? = config.crimsonHandler
    private val dispatcher: CoroutineDispatcher = config.dispatcher
    private val json: Json = config.json
    private val incomingSerializer = config.incomingSerializer ?: error("")
    private val outgoingSerializer = config.outgoingSerializer ?: error("")

    @OptIn(ExperimentalTime::class)
    val connectedAt = Clock.System.now()


    private var scope: CoroutineScope = CoroutineScope(dispatcher)

    private val incomingFrameFlow = session.incoming.consumeAsFlow().shareIn(scope, started = SharingStarted.Companion.Eagerly, replay = 0)

    private val _incomingMessageFlow: MutableSharedFlow<UPSTREAM> = MutableSharedFlow()
    val incomingMessageFlow: SharedFlow<UPSTREAM> get() = _incomingMessageFlow

    init {
        incomingFrameFlow.filterIsInstance<Frame.Text>()
            .mapNotNull { frame -> runCatching{ json.decodeFromString(incomingSerializer, frame.readText()) }.getOrNull() }
            .catch { e ->
                crimsonHandler?.onError(e)
                this@CrimsonServerSession.close(code = 4001, "incoming frame error")
            }.onEach {
                _incomingMessageFlow.emit(it)
            }.launchIn(scope)


        incomingFrameFlow.filterIsInstance<Frame.Binary>()
            .mapNotNull { frame -> runCatching { json.decodeFromString(incomingSerializer, frame.readBytes().decodeToString()) }.getOrNull() }
            .catch { e ->
                crimsonHandler?.onError(e)
                this@CrimsonServerSession.close(code = 4001, "incoming frame error")
            }.onEach {
                _incomingMessageFlow.emit(it)
            }.launchIn(scope)

        scope.launch {
            crimsonHandler?.onConnect(
                crimson = this@CrimsonServerSession,
                flow = incomingMessageFlow
            )
        }
    }


    override suspend fun send(data: DOWNSTREAM) {
        session.isActive.takeIf { it } ?: error("")
        session.send(Frame.Text(text = json.encodeToString(outgoingSerializer, data)))
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
            scope.cancel()
        }
    }
}