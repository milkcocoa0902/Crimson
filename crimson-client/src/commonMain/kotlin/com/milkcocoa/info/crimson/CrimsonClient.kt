package com.milkcocoa.info.crimson

import com.milkcocoa.info.crimson.core.CrimsonData
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.utils.io.InternalAPI
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds


/**
 * CrimsonClient is a client for type-safe websocket communication.
 * @param UPSTREAM upstream data type (send to server)
 * @param DOWNSTREAM downstream data type (received from server)
 * @property config crimson client config.
 * @see CrimsonConfig
 * @see CrimsonClientCore
 */
class CrimsonClient<UPSTREAM: CrimsonData, DOWNSTREAM: CrimsonData>(
    config: CrimsonConfig<UPSTREAM, DOWNSTREAM>
): CrimsonClientCore<UPSTREAM, DOWNSTREAM>{
    /**
     * constructor.
     * @param config crimson client config.
     * @see CrimsonConfig
     * @see CrimsonClientCore
     */
    constructor(config: CrimsonConfig<UPSTREAM, DOWNSTREAM>.()->Unit): this(CrimsonConfig<UPSTREAM, DOWNSTREAM>().apply(config))


    private val _connectionStatus = MutableStateFlow(ConnectionState.CLOSED)
    val connectionStatus: StateFlow<ConnectionState> = _connectionStatus

    private var webSocketSession: DefaultClientWebSocketSession? = null
    private var incomingFrameFlow: SharedFlow<Frame>? = null

    private val _incomingMessageFlow = MutableSharedFlow<DOWNSTREAM>()
    val incomingMessage: SharedFlow<DOWNSTREAM> get() = _incomingMessageFlow

    private val mutex = Mutex()


    private val ktorHttpClient: HttpClient = config.ktorHttpClient
    private val crimsonHandler: CrimsonHandler<UPSTREAM, DOWNSTREAM>? = config.crimsonHandler
    private val retryPolicy: RetryPolicy = config.retryPolicy
    private val dispatcher: CoroutineDispatcher = config.dispatcher
    private val json: Json = config.json
    private val webSocketEndpointProvider: WebSocketEndpointProvider = config.webSocketEndpointProvider
    private val incomingSerializer = config.incomingSerializer ?: error("")
    private val outgoingSerializer = config.outgoingSerializer ?: error("")
    private val abnormalCloseCodePredicate = config.abnormalCloseCodePredicate
    private val abnormalCloseReasonPredicate = config.abnormalCloseReasonPredicate


    private var coroutineScope = CoroutineScope(dispatcher + SupervisorJob())

    private fun resetScope(){
        coroutineScope.cancel()
        coroutineScope = CoroutineScope(dispatcher + SupervisorJob())
    }


    /**
     * execute command.
     * @param command crimson command.
     * @see CrimsonCommand
     */
    @OptIn(InternalAPI::class)
    override suspend fun execute(command: CrimsonCommand) {
        when(command){
            is CrimsonCommand.Ping -> webSocketSession?.takeIf { it.isActive }?.send(Frame.Ping(command.frame))
            is CrimsonCommand.Pong -> webSocketSession?.send(Frame.Pong(command.frame))
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
                        this@CrimsonClient.webSocketSession = it

                        this@CrimsonClient.incomingFrameFlow = it.incoming
                            .consumeAsFlow()
                            .shareIn(
                                scope = coroutineScope,
                                started = SharingStarted.Eagerly,
                                replay = 0
                            )

                        handleIncomingFrame(this@CrimsonClient.incomingFrameFlow!!)
                        respondToPingFrame(this@CrimsonClient.incomingFrameFlow!!)
                        closeMonitor(this@CrimsonClient.webSocketSession!!)

                        crimsonHandler?.onConnect(
                            crimson = this@CrimsonClient,
                            flow = _incomingMessageFlow
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
                    crimsonHandler?.onClosed(command.code, command.reason)
                    webSocketSession?.close(reason = CloseReason(code = command.code.toShort(), message = command.reason))
                    webSocketSession = null
                    incomingFrameFlow = null
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

    override suspend fun send(data: UPSTREAM) {
        check(_connectionStatus.value == ConnectionState.CONNECTED) { "WebSocket is not connected" }
        checkNotNull(webSocketSession) { "WebSocketSession is null" }

        webSocketSession!!.send(frame = Frame.Text(text = json.encodeToString(outgoingSerializer, data)))
    }

    override suspend fun receive(timeout: Duration): DOWNSTREAM {
        check(_connectionStatus.value == ConnectionState.CONNECTED) { "WebSocket is not connected" }
        checkNotNull(webSocketSession) { "WebSocketSession is null" }

        return withTimeout(timeout){
            return@withTimeout incomingMessage.first()
        }
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

    private suspend fun exponentialDelay(delay: Duration, max: Duration) {
        mutex.withLock {
            retryingJob?.cancel()
            retryingJob = coroutineScope.launch {
                _connectionStatus.value = ConnectionState.RETRYING
                var d = delay
                while (isActive && _connectionStatus.value != ConnectionState.CONNECTED) {
                    delay(d)
                    d = (d.inWholeMilliseconds * 1.33).milliseconds.coerceAtMost(max)
                    runCatching { execute(CrimsonCommand.Connect) }
                }
            }
        }
    }

    private fun closeMonitor(session: DefaultClientWebSocketSession){
        coroutineScope.launch {
            val closeReason = session.closeReason.await()
            closeReason?.let {
                // 終了コードでabnormallyを判別する
                this@CrimsonClient.webSocketSession = null
                execute(CrimsonCommand.Disconnect(
                    code = it.code,
                    reason = it.message,
                    abnormally = (it.code== CloseReason.Codes.INTERNAL_ERROR.code && it.message == "Ping timeout") ||
                                abnormalCloseCodePredicate(it.code) ||
                                abnormalCloseReasonPredicate(it.message)
                ))
            }
        }
    }

    private fun respondToPingFrame(sessionFlow: SharedFlow<Frame>){
        sessionFlow.filterIsInstance<Frame.Ping>()
            .mapNotNull { frame ->
                execute(CrimsonCommand.Pong(frame.readBytes()))
            }.launchIn(coroutineScope)
    }

    private fun handleIncomingFrame(sessionFlow: SharedFlow<Frame>){
        sessionFlow.filterIsInstance<Frame.Text>()
            .mapNotNull { frame -> runCatching{ json.decodeFromString(incomingSerializer, frame.readText()) }.getOrNull() }
            .catch { e ->
                crimsonHandler?.onError(e)
                execute(CrimsonCommand.Disconnect(code = 4001, "incoming frame error", abnormally = true))
            }.onEach {
                _incomingMessageFlow.emit(it)
            }.launchIn(coroutineScope)


        sessionFlow.filterIsInstance<Frame.Binary>()
            .mapNotNull { frame -> runCatching { json.decodeFromString(incomingSerializer, frame.readBytes().decodeToString()) }.getOrNull() }
            .catch { e ->
                crimsonHandler?.onError(e)
                execute(CrimsonCommand.Disconnect(code = 4001, "incoming frame error", abnormally = true))
            }.onEach {
                _incomingMessageFlow.emit(it)
            }.launchIn(coroutineScope)
    }

    private suspend  fun launchReconnectionLoop(){
        when(retryPolicy){
            is RetryPolicy.Never -> {}
            is RetryPolicy.SimpleDelay -> simpleDelay(retryPolicy.delay)
            is RetryPolicy.ExponentialDelay -> exponentialDelay(retryPolicy.initial, retryPolicy.max)
        }
    }
}