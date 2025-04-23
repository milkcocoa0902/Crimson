package com.milkcocoa.info.crimson

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets
import okhttp3.OkHttpClient
import java.time.Duration
import kotlin.time.Duration.Companion.seconds

actual val defaultCrimsonHttpClient: HttpClient = HttpClient(OkHttp){
    install(WebSockets)
    engine {
        preconfigured = OkHttpClient.Builder()
            .pingInterval(Duration.ofSeconds(60))
            .build()
    }
}

