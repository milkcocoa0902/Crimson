# 📚 Crimson - Typed WebSocket Framework for Ktor

## Overview

**Crimson** is a lightweight, type-safe WebSocket communication framework for [Ktor](https://ktor.io/).  
It provides a production-ready foundation for both **server** and **client** WebSocket communication by enhancing session management, reconnection control, and seamless typed data exchange.

✅ Type-safe send/receive  
✅ Session management and timeout handling  
✅ Automatic reconnection  
✅ Built on top of Ktor WebSocket APIs  
✅ Coroutine-friendly and highly extensible

---

## Modules

| Module | Description |
|:-------|:------------|
| `crimson-server` | Manage WebSocket sessions with connection limits, session timeouts, and safe broadcasting on Ktor servers. |
| `crimson-client` | Build type-safe WebSocket clients on Ktor clients. |

---

## Features

### Crimson Server
- Typed WebSocket session management
- Maximum connection limit enforcement
- Session lifetime timeout with watchdog monitoring
- Safe broadcasting to connected clients
- Coroutine-based non-blocking design

### Crimson Client
- Type-safe WebSocket communication
- Auto-reconnection support with customizable retry policies
- Heartbeat (ping-pong) monitoring and disconnection detection
- Coroutine-based incoming message streaming
- JSON serialization/deserialization based on Kotlinx Serialization

---

## Installation

**TBD — Will be available soon.**  
(We are preparing distribution settings. Stay tuned!)

---

## Basic Usage

### Server Side (Ktor)

```kotlin
install(Crimson) {
    crimsonConfig("chat") {
        // Optional: Configure server behaviors (handler, incoming serializer, outgoing serializer, etc.)
    }
}

routing {
    crimson<ChatMessage, ChatResponse>(
        path = "/chat",
        config = "chat"
    ) { sessionRegistry ->
        incomingMessageFlow.collect { message ->
            // Handle incoming chat message
        }
    }
}
```

---

### Client Side (Ktor)

```kotlin
val crimsonClient = CrimsonClient<ChatMessage, ChatResponse>{
    crimsonHandler = object: CrimsonHandler<ChatMessage, ChatResponse> {
        override suspend fun onConnect(crimson: CrimsonClientCore<ChatMessage, ChatResponse>, flow: SharedFlow<ChatResponse>) {
        }

        override suspend fun onError(e: Throwable) {
            println(e)
        }

        override suspend fun onClosed(code: Int, reason: String) {
            println("$code $reason")
        }
    }

    webSocketEndpointProvider = object: WebSocketEndpointProvider {
        override suspend fun build(): ConnectionInfo {
            return ConnectionInfo("ws://127.0.0.1:54321")
        }
    }

    retryPolicy = RetryPolicy.SimpleDelay(30.seconds)
    incomingSerializer = ChatResponse.serializer()
    outgoingSerializer = ChatMessage.serializer()
}

launch {
    crimsonClient.execute(CrimsonCommand.Connect)
}

launch {
    crimsonClient.incomingMessage.collect { incoming -> println(incoming) }
}

crimson.send(ChatMessage(message = "Hello!"))
```

---

## Why Crimson?

While Ktor provides basic WebSocket support, it focuses only on low-level I/O.  
**Crimson** bridges the gap by providing:

- Strong typing for safer communication
- Built-in session lifecycle management
- Automated connection health monitoring
- Reconnection strategy management (client-side)
- Production-grade robustness and scalability

---

## License

This project is licensed under the [Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0).

```text
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
```

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

