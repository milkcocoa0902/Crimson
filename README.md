# Crimson

**Crimson** is a Kotlin Multiplatform (KMP) type-safe WebSocket client based on Ktor.  
It supports structured messaging, automatic reconnection, and health checking with Ping/Pong frames.

---

## ✨ Features

- ✅ **Kotlin Multiplatform** support (JVM, Native, JS)
- ✅ **Type-safe messaging** using `kotlinx.serialization`
- ✅ **Reactive incoming messages** via Kotlin `Flow`
- ✅ **Health monitoring** with Ping/Pong heartbeat
- ✅ **Automatic reconnection** with configurable `RetryPolicy`
- ✅ **Customizable endpoint** with dynamic URL, headers, and tokens

---

## 📄 Quick Start

### Define Payload

```kotlin
@Serializable
data class SamplePayload(val a: String) : CrimsonData
```

### Create Crimson Client

```kotlin
val crimson = Crimson<SamplePayload, SamplePayload> {
    ktorHttpClient = HttpClient {
        install(WebSockets)
    }

    crimsonHandler = object : CrimsonHandler<SamplePayload, SamplePayload> {
        override suspend fun onConnect(
            crimson: CrimsonCore<SamplePayload, SamplePayload>,
            flow: SharedFlow<SamplePayload>
        ) {
            // Start health check once connected
            crimson.execute(CrimsonCommand.StartHealthCheck)

            // Collect messages
            CoroutineScope(Dispatchers.Default).launch {
                flow.collect { payload -> println(payload.a) }
            }
        }

        override suspend fun onError(e: Throwable) {
            println("Error: $e")
        }

        override suspend fun onClosed(code: Int, reason: String) {
            println("Closed: $code $reason")
        }
    }

    webSocketEndpointProvider = object : WebSocketEndpointProvider {
        override suspend fun build(): ConnectionInfo {
            return ConnectionInfo("ws://127.0.0.1:54321")
        }
    }

    retryPolicy = RetryPolicy.SimpleDelay(120.seconds)
    scope = CoroutineScope(Dispatchers.Default)
    json = Json
    incomingSerializer = SamplePayload.serializer()
    outgoingSerializer = SamplePayload.serializer()
}
```

### Run Client

```kotlin
fun main() = runBlocking {
    crimson.execute(CrimsonCommand.Connect)

    // Monitor connection status
    CoroutineScope(Dispatchers.Default).launch {
        crimson.connectionStatus.collect { state ->
            println("Connection state: $state")
        }
    }

    // Handle incoming messages
    CoroutineScope(Dispatchers.Default).launch {
        crimson.incoming.collect { incoming ->
            println("Received: $incoming")
        }
    }

    // Send a message
    CoroutineScope(Dispatchers.Default).launch {
        crimson.send(SamplePayload("hello"))
    }

    Thread.sleep(180.seconds.inWholeMilliseconds)
}
```

---

## 🔄 Retry Policies

```kotlin
retryPolicy = RetryPolicy.ExponentialDelay(initial = 20.seconds)
```

Available options:

- `RetryPolicy.Never`: Do not attempt reconnection
- `RetryPolicy.SimpleDelay`: Reconnect at a fixed interval
- `RetryPolicy.ExponentialDelay`: Reconnect with exponential backoff

---

## 🫠 Health Checks

```kotlin
crimson.execute(CrimsonCommand.StartHealthCheck)
```

- Sends periodic Ping frames to check connection liveness
- Expects a matching Pong frame
- Automatically disconnects with `CrimsonCommand.Disconnect(code = 4000, reason = "health check failed", abnormally = true)` if heartbeat fails

---

## 🧪 Local Test Server

### Option 1: Using `wscat`

```bash
npx wscat --listen 54321
```

### Option 2: Node.js WebSocket Echo Server

```js
const WebSocket = require('ws');
const wss = new WebSocket.Server({ port: 54321 });

wss.on('connection', ws => {
  ws.on('message', msg => {
    console.log('received:', msg);
    ws.send(msg); // echo back
  });
});
```

---

## 🧩 License

This project is licensed under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).

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

---

Crimson is designed for reliability, flexibility, and clarity in WebSocket communication across KMP targets.  
Feel free to contribute, open issues, or share your use cases!
