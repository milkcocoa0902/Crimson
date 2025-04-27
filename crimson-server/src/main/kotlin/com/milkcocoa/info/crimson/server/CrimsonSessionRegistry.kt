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


/**
 * Registry for crimson server sessions.
 * @param UPSTREAM upstream data type (received from clients)
 * @param DOWNSTREAM downstream data type (send to clients)
 */
@OptIn(ExperimentalTime::class)
class CrimsonSessionRegistry<UPSTREAM: CrimsonData, DOWNSTREAM: CrimsonData>(
    /**
     * max connection count.
     */
    private val maxConnection: Int = 10000,
    /**
     * max lifetime of session.
     */
    private val maxLifetime: Duration = 300.seconds,
    /**
     * watch dog interval.
     */
    private val watchDogInterval: Duration = 120.seconds,
    /**
     * watch dog dispatcher.
     */
    private val watchDogDispatcher: CoroutineDispatcher = Dispatchers.Default,
){
    /**
     * Connection limit exception.
     */
    object ConnectionLimit: Throwable("")

    val semaphore = Semaphore(100)
    val mutex = Mutex()
    private val _crimsonServerSessionMap = mutableMapOf<UUID, CrimsonServerSession<UPSTREAM, DOWNSTREAM>>()
    private val _crimsonServerSessionFlow = MutableSharedFlow<List<CrimsonServerSession<UPSTREAM, DOWNSTREAM>>>()
    val crimsonServerSessionFlow: SharedFlow<List<CrimsonServerSession<UPSTREAM, DOWNSTREAM>>> get() = _crimsonServerSessionFlow

    /**
     * store crimson server session.
     * @param sessionId session id
     * @param crimsonServerSession crimson server session
     * @throws ConnectionLimit if the connection limit is exceeded.
     * @see ConnectionLimit
     */
    @OptIn(ExperimentalTime::class)
    suspend fun add(sessionId: UUID, crimsonServerSession: CrimsonServerSession<UPSTREAM, DOWNSTREAM>){
        mutex.withLock {
            if(_crimsonServerSessionMap.size >= maxConnection) throw ConnectionLimit

            _crimsonServerSessionMap.put(sessionId, crimsonServerSession)
            _crimsonServerSessionFlow.emit(_crimsonServerSessionMap.values.toList())
        }
    }

    /**
     * remove crimson server session.
     * @param sessionId session id
     */
    suspend fun remove(sessionId: UUID){
        mutex.withLock {
            runCatching {
                _crimsonServerSessionMap.remove(sessionId)?.close()
            }
            _crimsonServerSessionFlow.emit(_crimsonServerSessionMap.values.toList())
        }
    }

    suspend fun removeRange(vararg sessionIds: UUID){
        mutex.withLock {
            runCatching {
                sessionIds.forEach {
                    _crimsonServerSessionMap.remove(it)?.close()
                }
            }
            _crimsonServerSessionFlow.emit(_crimsonServerSessionMap.values.toList())
        }
    }

    /**
     * get crimson server session.
     * @param sessionId session id
     * @return crimson server session. if not found, returns null.
     * @see CrimsonServerSession
     */
    suspend fun get(sessionId: UUID): CrimsonServerSession<UPSTREAM, DOWNSTREAM>?{
        return _crimsonServerSessionMap[sessionId]
    }

    /**
     * get all crimson server sessions.
     * @return all crimson server sessions.
     * @see CrimsonServerSession
     */
    val all get(): List<CrimsonServerSession<UPSTREAM, DOWNSTREAM>>{
        return _crimsonServerSessionMap.values.toList()
    }

    /**
     * enforce timeout of sessions.
     *
     */
    @OptIn(ExperimentalTime::class)
    suspend fun enforceTimeout() {
        mutex.withLock {
            val now = Clock.System.now()
            _crimsonServerSessionMap
                .filterValues { it.connectedAt.plus(maxLifetime) < now }
                .let { removeRange(*it.keys.toTypedArray()) }
        }
    }

    /**
     * watch dog for timeout of sessions.
     */
    init {
        CoroutineScope(watchDogDispatcher).launch {
            while (true) {
                delay(watchDogInterval)
                val now = Clock.System.now()
                val sample = _crimsonServerSessionMap.keys
                    .shuffled()
                    .take(maxConnection.div(10).coerceAtLeast(100))

                _crimsonServerSessionMap.filterKeys { sample.contains(it) }
                    .filterValues { it.connectedAt.plus(maxLifetime) < now }
                    .let { removeRange(*it.keys.toTypedArray()) }
            }
        }
    }
}