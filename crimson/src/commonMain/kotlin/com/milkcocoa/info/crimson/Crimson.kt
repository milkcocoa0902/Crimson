package com.milkcocoa.info.crimson

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.http.headersOf
import io.ktor.utils.io.core.toByteArray
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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
    private val dispatcher: CoroutineDispatcher = config.dispatcher
    private val json: Json = config.json
    private val webSocketEndpointProvider: WebSocketEndpointProvider = config.webSocketEndpointProvider
    private val healthCheckInterval = config.healthCheckInterval
    private val incomingSerializer = config.incomingSerializer ?: error("")
    private val outgoingSerializer = config.outgoingSerializer ?: error("")


    private var coroutineScope = CoroutineScope(dispatcher + SupervisorJob())

    private fun resetScope(){
        coroutineScope.cancel()
        coroutineScope = CoroutineScope(dispatcher + SupervisorJob())
    }


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
                    runCatching {
                        val connectionInfo = webSocketEndpointProvider.build()
                        _connectionStatus.value = ConnectionState.CONNECTING

                        ktorHttpClient.webSocketSession(urlString = connectionInfo.urlString) {
                            connectionInfo.headers.forEach { (key, value) ->
                                headers.append(key, value)
                            }
                        }
                    }.onSuccess {
                        _connectionStatus.value = ConnectionState.CONNECTED
                        this@Crimson.session = it

                        handleIncomingFrame(this@Crimson.session!!)
                        respondToPingFrame(this@Crimson.session!!)
                        closeMonitor(this@Crimson.session!!)

                        crimsonHandler?.onConnect(
                            crimson = this@Crimson,
                            flow = _incomingFlow
                        )
                    }.onFailure {
                        _connectionStatus.value = ConnectionState.CLOSED
                        crimsonHandler?.onError(it)
                    }
                }
            }
            is CrimsonCommand.Disconnect -> {
                mutex.withLock {
                    _connectionStatus.value = ConnectionState.CLOSED
                    session?.close(reason = CloseReason(code = command.code.toShort(), message = command.reason))
                    session = null
                    if(command.abnormally){
                        resetScope()
                        coroutineScope.launch {
                            launchReconnectionLoop()
                        }
                    }
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

    private suspend fun simpleDelay(delay: Duration) {
        mutex.withLock {
            retryingJob?.cancel()
            retryingJob = coroutineScope.launch {
                _connectionStatus.value = ConnectionState.RETRYING
                while (isActive && _connectionStatus.value != ConnectionState.CONNECTED) {
                    delay(delay)
                    runCatching { execute(CrimsonCommand.Connect) }
                }
            }
        }
    }

    private suspend fun exponentialDelay(delay: Duration) {
        mutex.withLock {
            retryingJob?.cancel()
            retryingJob = coroutineScope.launch {
                _connectionStatus.value = ConnectionState.RETRYING
                var d = delay
                while (isActive && _connectionStatus.value != ConnectionState.CONNECTED) {
                    delay(d)
                    d = (d.inWholeMilliseconds * 1.33).milliseconds
                    runCatching { execute(CrimsonCommand.Connect) }
                }
            }
        }
    }


    @OptIn(ExperimentalTime::class)
    private fun launchHealthCheck(session: DefaultClientWebSocketSession) {
        var lastHealthCheckTime = Clock.System.now()

        session.incoming.consumeAsFlow()
            .filterIsInstance<Frame.Pong>()
            .filter { frame -> frame.data.contentEquals(healthCheckFrame) }
            .onEach { frame -> lastHealthCheckTime = Clock.System.now() }
            .launchIn(coroutineScope)

        coroutineScope.launch {
            while (isActive) {
                execute(CrimsonCommand.Ping(healthCheckFrame))
                delay(healthCheckInterval)
                if(Clock.System.now() > lastHealthCheckTime + (healthCheckInterval * 2)){
                    execute(CrimsonCommand.Disconnect(code = 4000, "health check failed", abnormally = true))
                }
            }
        }
    }


    private fun closeMonitor(session: DefaultClientWebSocketSession){
        coroutineScope.launch {
            val closeReason = session.closeReason.await()
            closeReason?.let {
                // 終了コードでabnormallyを判別する
                this@Crimson.crimsonHandler?.onClosed(closeReason.code.toInt(), closeReason.message)
            }
        }
    }

    private fun respondToPingFrame(session: DefaultClientWebSocketSession){
        session.incoming.consumeAsFlow()
            .filterIsInstance<Frame.Ping>()
            .mapNotNull { frame ->
                execute(CrimsonCommand.Pong(frame.readBytes()))
            }.launchIn(coroutineScope)
    }

    private fun handleIncomingFrame(session: DefaultClientWebSocketSession){
        session.incoming.consumeAsFlow()
            .filter {
                (it is Frame.Text) || (it is Frame.Binary)
            }
            .mapNotNull { frame ->
                when(frame) {
                    is Frame.Text -> runCatching{ json.decodeFromString(incomingSerializer, frame.readText()) }.getOrNull()
                    is Frame.Binary -> runCatching { json.decodeFromString(incomingSerializer, frame.readBytes().decodeToString()) }.getOrNull()
                    else -> null
                }
            }
            .catch { e ->
                crimsonHandler?.onError(e)
                execute(CrimsonCommand.Disconnect(code = 4001, "incoming frame error", abnormally = true))
            }.onEach {
                _incomingFlow.emit(it)
            }.launchIn(coroutineScope)
    }

    private suspend  fun launchReconnectionLoop(){
        when(retryPolicy){
            is RetryPolicy.Never -> {}
            is RetryPolicy.SimpleDelay -> simpleDelay(retryPolicy.delay)
            is RetryPolicy.ExponentialDelay -> exponentialDelay(retryPolicy.initial)
        }
    }


    companion object{
        private val healthCheckFrame = "Crimson Health Check".toByteArray()
    }
}