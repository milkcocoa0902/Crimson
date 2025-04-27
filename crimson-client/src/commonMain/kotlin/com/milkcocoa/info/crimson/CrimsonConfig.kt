package com.milkcocoa.info.crimson

import com.milkcocoa.info.crimson.core.CrimsonData
import io.ktor.client.HttpClient
import io.ktor.websocket.CloseReason
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

internal expect val defaultCrimsonHttpClient: HttpClient

data class ConnectionInfo(
    val urlString: String,
    val headers: Map<String, String> = emptyMap()
)



interface WebSocketEndpointProvider{
    suspend fun build(): ConnectionInfo
}

internal val defaultWebSocketEndpointProvider = object: WebSocketEndpointProvider {
    override suspend fun build(): ConnectionInfo {
        TODO("Not implemented yet.")
    }
}

class CrimsonConfig<SND: CrimsonData,  RCV: CrimsonData>() {
    var ktorHttpClient: HttpClient = defaultCrimsonHttpClient
    var webSocketEndpointProvider: WebSocketEndpointProvider = defaultWebSocketEndpointProvider
    var crimsonHandler: CrimsonHandler<SND, RCV>? = null
    var retryPolicy: RetryPolicy = RetryPolicy.Never
    var dispatcher: CoroutineDispatcher = CrimsonCoroutineDispatchers.io
    var json: Json = Json.Default
    var incomingSerializer: KSerializer<RCV>? = null
    var outgoingSerializer: KSerializer<SND>? = null
    var abnormalCloseCodePredicate: (code: Short)->Boolean = { it != CloseReason.Codes.NORMAL.code }
    var abnormalCloseReasonPredicate: (String) -> Boolean = { false }
}