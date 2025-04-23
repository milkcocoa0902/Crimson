package com.milkcocoa.info.crimson

import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.websocket.WebSockets

actual val defaultCrimsonHttpClient: HttpClient = HttpClient(Js){
    install(WebSockets)
}

