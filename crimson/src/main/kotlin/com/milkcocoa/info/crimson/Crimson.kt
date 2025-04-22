package com.milkcocoa.info.crimson

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.http.headersOf
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime


class Crimson<SND: CrimsonData, RCV: CrimsonData>(
    config: CrimsonConfig<SND, RCV>
): CrimsonCore<SND, RCV>{
    constructor(config: CrimsonConfig<SND, RCV>.()->Unit): this(CrimsonConfig<SND, RCV>().apply(config))


    private val _connectionStatus = MutableStateFlow(ConnectionState.CLOSED)
    val connectionStatus: StateFlow<ConnectionState> = _connectionStatus

    private var session: DefaultClientWebSocketSession? = null
    private val _incomingFlow = MutableSharedFlow<RCV>()
    val incoming: SharedFlow<RCV> get() = _incomingFlow

    private val mutex = Mutex()


    private val ktorHttpClient: HttpClient = config.ktorHttpClient
    private val crimsonHandler: CrimsonHandler<SND, RCV>? = config.crimsonHandler
    private val retryPolicy: RetryPolicy = config.retryPolicy
    private val scope: CoroutineScope = config.scope
    private val json: Json = config.json
    private val webSocketEndpointProvider: WebSocketEndpointProvider = config.webSocketEndpointProvider
    private val healthCheckInterval = config.healthCheckInterval
    private val incomingSerializer = config.incomingSerializer ?: error("")
    private val outgoingSerializer = config.outgoingSerializer ?: error("")


    override suspend fun execute(command: CrimsonCommand) {
        when(command){
            is CrimsonCommand.Ping -> session?.send(Frame.Ping(command.frame))
            is CrimsonCommand.Pong -> session?.send(Frame.Pong(command.frame))
            is CrimsonCommand.StartHealthCheck -> {
                this.session ?: return
                launchHealthCheck(this@Crimson.session!!)
            }
            is CrimsonCommand.Connect -> {
                mutex.withLock {
                    val connectionInfo = webSocketEndpointProvider.build()

                    _connectionStatus.value = ConnectionState.CONNECTING
                    ktorHttpClient.webSocketSession(urlString = connectionInfo.urlString) {
                        connectionInfo.headers.forEach { key, value -> headersOf(key, value) }
                    }.also {
                        _connectionStatus.value = ConnectionState.CONNECTED
                        this@Crimson.session = it

                        handleIncomingFrame(this@Crimson.session!!)
                        respondToPingFrame(this@Crimson.session!!)
                        closeMonitor(this@Crimson.session!!)

                        crimsonHandler?.onConnect(
                            crimson = this@Crimson,
                            flow = _incomingFlow
                        )
                    }
                }
            }
            is CrimsonCommand.Disconnect -> {
                mutex.withLock {
                    session?.close(reason = CloseReason(code = command.code.toShort(), message = command.reason))
                    session = null
                    scope.cancel()
                    retryingJob?.cancel()
                    _connectionStatus.value = ConnectionState.CLOSED
                }
            }
        }
    }

    override suspend fun send(data: SND) {
        if(_connectionStatus.value != ConnectionState.CONNECTED){
            throw IllegalStateException()
        }
        if(session == null){
            throw IllegalStateException()
        }

        session!!.send(frame = Frame.Text(text = json.encodeToString(outgoingSerializer, data)))
    }

    private var retryingJob: Job? = null

    private fun simpleDelay(delay: Duration) {
        retryingJob?.cancel()
        retryingJob = scope.launch {
            _connectionStatus.value = ConnectionState.RETRYING
            while (isActive && _connectionStatus.value != ConnectionState.CONNECTED) {
                delay(delay)
                runCatching { execute(CrimsonCommand.Connect) }
            }
            _connectionStatus.value = ConnectionState.CONNECTED
        }
    }

    private fun exponentialDelay(delay: Duration) {
        retryingJob?.cancel()
        retryingJob = scope.launch {
            _connectionStatus.value = ConnectionState.RETRYING
            var d = delay
            while (isActive && _connectionStatus.value != ConnectionState.CONNECTED) {
                delay(d)
                d = (d.inWholeMilliseconds * 1.33).milliseconds
                runCatching { execute(CrimsonCommand.Connect) }
            }
            _connectionStatus.value = ConnectionState.CONNECTED
        }
    }


    @OptIn(ExperimentalTime::class)
    private fun launchHealthCheck(session: DefaultClientWebSocketSession) {
        var lastHealthCheckTime = Clock.System.now()

        session.incoming.consumeAsFlow()
            .filterIsInstance<Frame.Pong>()
            .filter { frame -> frame.data.contentEquals(healthCheckFrame) }
            .onEach { frame -> lastHealthCheckTime = Clock.System.now() }
            .launchIn(scope)

        scope.launch {
            while (isActive) {
                execute(CrimsonCommand.Ping(healthCheckFrame))
                delay(healthCheckInterval)
                if(Clock.System.now() > lastHealthCheckTime + (healthCheckInterval * 2)){
                    execute(CrimsonCommand.Disconnect(code = 4000, "health check failed"))
                }
            }
        }
    }


    private fun closeMonitor(session: DefaultClientWebSocketSession){
        scope.launch {
            val closeReason = session.closeReason.await()
            closeReason?.let {
                this@Crimson.crimsonHandler?.onClosed(closeReason.code.toInt(), closeReason.message)
            }
            this@Crimson._connectionStatus.value = ConnectionState.CLOSED
        }
    }

    private fun respondToPingFrame(session: DefaultClientWebSocketSession){
        session.incoming.consumeAsFlow()
            .filterIsInstance<Frame.Ping>()
            .mapNotNull { frame ->
                execute(CrimsonCommand.Pong(frame.readBytes()))
            }.launchIn(scope)
    }

    private fun handleIncomingFrame(session: DefaultClientWebSocketSession){
        session.incoming.consumeAsFlow()
            .filter {
                (it is Frame.Text) || (it is Frame.Binary)
            }
            .mapNotNull { frame ->
                runCatching {
                    return@runCatching when(frame) {
                        is Frame.Text -> json.decodeFromString(incomingSerializer, frame.readText())
                        is Frame.Binary -> json.decodeFromString(incomingSerializer, frame.readBytes().decodeToString())
                        else -> null
                    }
                }.getOrNull()
            }
            .catch { e ->
                _connectionStatus.value = ConnectionState.CLOSED
                this@Crimson.session = null
                when(retryPolicy){
                    is RetryPolicy.Never -> {}
                    is RetryPolicy.SimpleDelay -> simpleDelay(retryPolicy.delay)
                    is RetryPolicy.ExponentialDelay -> exponentialDelay(retryPolicy.initial)
                }
                crimsonHandler?.onError(e)
            }.onEach {
                _incomingFlow.emit(it)
            }.launchIn(scope)
    }


    companion object{
        private val healthCheckFrame = "Crimson Health Check".toByteArray()
    }
}