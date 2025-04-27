package com.milkcocoa.info.crimson.server

import com.milkcocoa.info.crimson.core.CrimsonData
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.Plugin
import io.ktor.server.application.pluginRegistry
import io.ktor.server.routing.Route
import io.ktor.server.websocket.webSocket
import io.ktor.util.AttributeKey
import io.ktor.websocket.CloseReason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.util.UUID

data class CrimsonConfigHolder(
    val name: String,
    val config: CrimsonServerConfig<CrimsonData, CrimsonData>
)


class CrimsonPluginConfig{
    private val configHolderList = mutableListOf<CrimsonConfigHolder>()
    val configHolder: List<CrimsonConfigHolder> = configHolderList

    fun crimsonConfig(name: String, block: CrimsonServerConfig<CrimsonData, CrimsonData>.() -> Unit){
        configHolderList.add(CrimsonConfigHolder(name = name, config = CrimsonServerConfig<CrimsonData, CrimsonData>().apply(block)))
    }
}

object Crimson: Plugin<ApplicationCallPipeline, CrimsonPluginConfig, Set<CrimsonConfigHolder>> {
    override val key: AttributeKey<Set<CrimsonConfigHolder>>
        get() = io.ktor.util.AttributeKey(AttributeKeyString)
    override fun install(pipeline: ApplicationCallPipeline, configure: CrimsonPluginConfig.() -> Unit): Set<CrimsonConfigHolder> {
        return CrimsonPluginConfig().apply(configure)
            .configHolder
            .groupingBy { it.name }
            .reduce { key, accumulator, element -> element }
            .values.toSet()
    }

    const val AttributeKeyString = "CrimsonPluginConfigKey"
}

fun<UPSTREAM: CrimsonData, DOWNSTREAM: CrimsonData> Route.crimson(
    path: String,
    config: String,
    crimsonSessionRegistry: CrimsonSessionRegistry<UPSTREAM, DOWNSTREAM> = CrimsonSessionRegistry(),
    block: suspend CrimsonServerSession<UPSTREAM, DOWNSTREAM>.(crimsonSessionRegistry: CrimsonSessionRegistry<UPSTREAM, DOWNSTREAM>) -> Unit = {}
){
    webSocket(path = path){
        val sessionId = UUID.randomUUID()
        val crimsonServerSessionConfigSet = call.application.pluginRegistry[io.ktor.util.AttributeKey<Set<CrimsonConfigHolder>>(
            Crimson.AttributeKeyString
        )]
        val crimsonServerSessionConfig = (crimsonServerSessionConfigSet.find { it.name == config }?.config as? CrimsonServerConfig<UPSTREAM, DOWNSTREAM>) ?: error("crimson config not found")

        val crimsonServerSession = CrimsonServerSession<UPSTREAM, DOWNSTREAM>(
            config = crimsonServerSessionConfig,
            session = this
        )

        runCatching {
            crimsonSessionRegistry.add(sessionId, crimsonServerSession)
        }.getOrElse {
            when(it){
                is CrimsonSessionRegistry.ConnectionLimit -> {
                    crimsonServerSession.close(code = CloseReason.Codes.VIOLATED_POLICY.code, reason = "connection limit exceeded")
                }
            }
            return@webSocket
        }

        crimsonServerSession.block(crimsonSessionRegistry)

        crimsonServerSession.close(code = CloseReason.Codes.NORMAL.code, reason = "")
        crimsonSessionRegistry.remove(sessionId)
    }
}


suspend fun<DOWNSTREAM: CrimsonData> List<CrimsonServerSession<*, DOWNSTREAM>>.broadcast(data: DOWNSTREAM){
    withContext(Dispatchers.IO) {
        this@broadcast.map {
            async {
                runCatching {
                    it.send(data = data)
                }.onFailure {

                }
            }
        }.awaitAll()
    }
}