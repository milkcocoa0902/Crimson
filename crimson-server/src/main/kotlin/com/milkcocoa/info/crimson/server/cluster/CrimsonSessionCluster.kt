package com.milkcocoa.info.crimson.server.cluster

import com.milkcocoa.info.crimson.core.CrimsonData
import com.milkcocoa.info.crimson.server.CrimsonServerSession
import java.util.UUID
import kotlin.collections.List

interface CrimsonSessionCluster<UPSTREAM: CrimsonData, DOWNSTREAM: CrimsonData> {
    /**
     * Connection limit exception.
     */
    object ConnectionLimit: Throwable("")


    /**
     * get all crimson server sessions.
     * @return all crimson server sessions.
     * @see CrimsonServerSession
     */
    val all: List<CrimsonServerSession<UPSTREAM, DOWNSTREAM>>
    /**
     * store crimson server session.
     * @param sessionId session id
     * @param crimsonServerSession crimson server session
     * @throws ConnectionLimit if the connection limit is exceeded.
     * @see ConnectionLimit
     */
    suspend fun add(sessionId: UUID, crimsonServerSession: CrimsonServerSession<UPSTREAM, DOWNSTREAM>)
    /**
     * remove crimson server session.
     * @param sessionId session id
     */
    suspend fun remove(sessionId: UUID)
    suspend fun removeRange(vararg sessionIds: UUID)
    /**
     * get crimson server session.
     * @param sessionId session id
     * @return crimson server session. if not found, returns null.
     * @see CrimsonServerSession
     */
    suspend fun get(sessionId: UUID): CrimsonServerSession<UPSTREAM, DOWNSTREAM>?
    /**
     * enforce timeout of sessions.
     *
     */
    suspend fun enforceTimeout()

    suspend fun broadcast(data: DOWNSTREAM, filter: (CrimsonServerSession<UPSTREAM, DOWNSTREAM>) -> Boolean)
}