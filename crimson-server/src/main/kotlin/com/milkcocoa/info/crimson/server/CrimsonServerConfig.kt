package com.milkcocoa.info.crimson.server

import com.milkcocoa.info.crimson.core.CrimsonData
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

class CrimsonServerConfig<UPSTREAM: CrimsonData, DOWNSTREAM: CrimsonData>() {
    var crimsonHandler: CrimsonHandler<UPSTREAM, DOWNSTREAM>? = null
    var dispatcher: CoroutineDispatcher = Dispatchers.IO
    var json: Json = Json.Default
    var incomingSerializer: KSerializer<UPSTREAM>? = null
    var outgoingSerializer: KSerializer<DOWNSTREAM>? = null
}