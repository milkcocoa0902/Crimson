package com.milkcocoa.info.crimson

import com.milkcocoa.info.crimson.core.CrimsonData
import io.ktor.client.HttpClient
import io.ktor.websocket.CloseReason
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json


internal expect val defaultCrimsonHttpClient: HttpClient

/**
 * Connection information for crimson client.
 * @param urlString url string
 * @param headers headers
 */
data class ConnectionInfo(
    val urlString: String,
    val headers: Map<String, String> = emptyMap()
)


/**
 * Provider for WebSocket endpoint.
 */
interface WebSocketEndpointProvider{
    /**
     * Build WebSocket endpoint.
     * @return WebSocket endpoint
     */
    suspend fun build(): ConnectionInfo
}


internal val defaultWebSocketEndpointProvider = object: WebSocketEndpointProvider {
    override suspend fun build(): ConnectionInfo {
        TODO("Not implemented yet.")
    }
}

/**
 * Configuration for crimson client.
 * @param UPSTREAM upstream data type (received from server)
 * @param DOWNSTREAM downstream data type (send to server)
 * @see CrimsonHandler
 * @see CrimsonData
 * @see Json
 * @see WebSocketEndpointProvider
 * @see defaultWebSocketEndpointProvider
 * @see defaultCrimsonHttpClient
 * @see CrimsonCoroutineDispatchers
 */
class CrimsonConfig<UPSTREAM: CrimsonData,  DOWNSTREAM: CrimsonData>() {
    var ktorHttpClient: HttpClient = defaultCrimsonHttpClient
    var webSocketEndpointProvider: WebSocketEndpointProvider = defaultWebSocketEndpointProvider
    var crimsonHandler: CrimsonHandler<UPSTREAM, DOWNSTREAM>? = null
    var retryPolicy: RetryPolicy = RetryPolicy.Never
    var dispatcher: CoroutineDispatcher = CrimsonCoroutineDispatchers.io
    var json: Json = Json.Default
    var incomingSerializer: KSerializer<DOWNSTREAM>? = null
    var outgoingSerializer: KSerializer<UPSTREAM>? = null
    var abnormalCloseCodePredicate: (code: Short)->Boolean = { it != CloseReason.Codes.NORMAL.code }
    var abnormalCloseReasonPredicate: (String) -> Boolean = { false }
}