package com.milkcocoa.info.crimson.server

import com.milkcocoa.info.crimson.core.CrimsonData
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * Configuration for crimson server.
 * @param UPSTREAM upstream data type (received from clients)
 * @param DOWNSTREAM downstream data type (send to clients)
 * @see CrimsonHandler
 * @see CrimsonData
 * @see Json
 * @see KSerializer
 * @see CoroutineDispatcher
 * @see Crimson
 * @see CrimsonServerSession
 */
class CrimsonServerConfig<UPSTREAM: CrimsonData, DOWNSTREAM: CrimsonData>() {
    var crimsonHandler: CrimsonHandler<UPSTREAM, DOWNSTREAM>? = null
    var dispatcher: CoroutineDispatcher = Dispatchers.IO
    var json: Json = Json.Default
    var incomingSerializer: KSerializer<UPSTREAM>? = null
    var outgoingSerializer: KSerializer<DOWNSTREAM>? = null
}