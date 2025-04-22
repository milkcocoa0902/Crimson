package com.milkcocoa.info.crimson

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds


internal val defaultCrimsonHttpClient = HttpClient(CIO){
    install(WebSockets)
}

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
    var scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    var json: Json = Json.Default
    var healthCheckInterval: Duration = 60.seconds
    var incomingSerializer: KSerializer<RCV>? = null
    var outgoingSerializer: KSerializer<SND>? = null
}