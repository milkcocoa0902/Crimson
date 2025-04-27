package com.milkcocoa.info.crimson.server

import com.milkcocoa.info.crimson.core.CrimsonData
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class CrimsonSessionRegistry<UPSTREAM: CrimsonData, DOWNSTREAM: CrimsonData>(
    private val maxConnection: Int = 10000,
    private val maxLifetime: Duration = 3600.seconds,
    private val watchDogInterval: Duration = 120.seconds,
    private val watchDogDispatcher: CoroutineDispatcher = Dispatchers.Default,
){
    object ConnectionLimit: Throwable("")

    val semaphore = Semaphore(100)
    val mutex = Mutex()
    private val _crimsonServerSessionMap = mutableMapOf<UUID, CrimsonServerSession<UPSTREAM, DOWNSTREAM>>()
    private val _crimsonServerSessionFlow = MutableSharedFlow<List<CrimsonServerSession<UPSTREAM, DOWNSTREAM>>>()
    val crimsonServerSessionFlow: SharedFlow<List<CrimsonServerSession<UPSTREAM, DOWNSTREAM>>> get() = _crimsonServerSessionFlow

    @OptIn(ExperimentalTime::class)
    suspend fun add(sessionId: UUID, crimsonServerSession: CrimsonServerSession<UPSTREAM, DOWNSTREAM>){
        mutex.withLock {
            if(_crimsonServerSessionMap.size >= maxConnection) throw ConnectionLimit

            _crimsonServerSessionMap.put(sessionId, crimsonServerSession)
            _crimsonServerSessionFlow.emit(_crimsonServerSessionMap.values.toList())
        }
    }

    suspend fun remove(sessionId: UUID){
        mutex.withLock {
            runCatching {
                _crimsonServerSessionMap.remove(sessionId)?.close()
            }
            _crimsonServerSessionFlow.emit(_crimsonServerSessionMap.values.toList())
        }
    }

    suspend fun get(sessionId: UUID): CrimsonServerSession<UPSTREAM, DOWNSTREAM>?{
        return _crimsonServerSessionMap[sessionId]
    }

    suspend fun all(): List<CrimsonServerSession<UPSTREAM, DOWNSTREAM>>{
        return _crimsonServerSessionMap.values.toList()
    }

    @OptIn(ExperimentalTime::class)
    suspend fun enforceTimeout() {
        mutex.withLock {
            withContext(Dispatchers.Default) {
                val now = Clock.System.now()
                _crimsonServerSessionMap
                    .filterValues { it.connectedAt.plus(maxLifetime) < now }
                    .map {
                        async {
                            runCatching {
                                semaphore.withPermit {
                                    remove(it.key)
                                }
                            }
                        }
                    }
            }
        }
    }

    init {
        CoroutineScope(watchDogDispatcher).launch {
            while (true) {
                delay(watchDogInterval)
                val now = Clock.System.now()
                mutex.withLock {
                    val sample = _crimsonServerSessionMap.keys
                        .shuffled()
                        .take(maxConnection.div(10).coerceAtLeast(100))

                    _crimsonServerSessionMap.filterKeys { sample.contains(it) }
                        .filterValues { it.connectedAt.plus(maxLifetime) < now }
                        .map { async { runCatching{ semaphore.withPermit { remove(it.key) } } } }
                        .awaitAll()
                }
            }
        }
    }
}