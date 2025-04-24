package com.milkcocoa.info.crimson

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets
import okhttp3.OkHttpClient
import java.time.Duration
import kotlin.time.Duration.Companion.seconds

actual val defaultCrimsonHttpClient: HttpClient = HttpClient(CIO){
    install(WebSockets)
    engine {
    }
}

