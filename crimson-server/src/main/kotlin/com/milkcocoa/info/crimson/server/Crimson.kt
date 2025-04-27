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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Configuration holder for [Crimson].
 * @param name config name
 * @param config config
 * @see CrimsonServerConfig
 * @see CrimsonData
 */
data class CrimsonConfigHolder(
    val name: String,
    val config: CrimsonServerConfig<out CrimsonData, out CrimsonData>
)


/**
 * Plugin configuration for [Crimson].
 */
class CrimsonPluginConfig{
    private val configHolderList = mutableListOf<CrimsonConfigHolder>()

    /**
     *
     */
    val configHolder: List<CrimsonConfigHolder> = configHolderList

    /**
     * add crimson config.
     * @param name config name
     * @param block config block. see [CrimsonServerConfig] for details.
     */
    fun<UPSTREAM: CrimsonData, DOWNSTREAM: CrimsonData>
            crimsonConfig(name: String, block: CrimsonServerConfig<UPSTREAM, DOWNSTREAM>.() -> Unit){
        configHolderList.add(CrimsonConfigHolder(name = name, config = CrimsonServerConfig<UPSTREAM, DOWNSTREAM>().apply(block)))
    }
}

/**
 * [Crimson] plugin.
 * @see CrimsonPluginConfig
 */
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

/**
 * attach crimson server session to route.
 * @param path path to attach
 * @param config crimson server config name
 * @param crimsonSessionRegistry crimson session registry
 * @param block block to execute when the connection is established. see [CrimsonServerSession] for details.
 * @see CrimsonServerSession
 * @see CrimsonServerConfig
 * @see CrimsonSessionRegistry
 * @see CrimsonData
 * @see io.ktor.server.websocket.WebSockets
 * @see io.ktor.server.websocket.webSocket
 * @see io.ktor.server.routing.Route
 */
fun<UPSTREAM: CrimsonData, DOWNSTREAM: CrimsonData> Route.crimson(
    path: String,
    config: String,
    crimsonSessionRegistry: CrimsonSessionRegistry<UPSTREAM, DOWNSTREAM> = CrimsonSessionRegistry(),
    block: suspend CrimsonServerSession<UPSTREAM, DOWNSTREAM>.(crimsonSessionRegistry: CrimsonSessionRegistry<UPSTREAM, DOWNSTREAM>) -> Unit = {}
){
    webSocket(path = path){
        runCatching {
            val sessionId = UUID.randomUUID()
            val crimsonServerSessionConfigSet = call.application.pluginRegistry[io.ktor.util.AttributeKey<Set<CrimsonConfigHolder>>(
                Crimson.AttributeKeyString
            )]
            val crimsonServerSessionConfig = (crimsonServerSessionConfigSet.find { it.name == config }?.config as? CrimsonServerConfig<UPSTREAM, DOWNSTREAM>) ?: error("crimson config not found")

            val crimsonServerSession = CrimsonServerSession(
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

            runCatching {
                withContext(crimsonServerSession.scope.coroutineContext){
                    crimsonServerSession.block(crimsonSessionRegistry)
                }
            }.getOrElse {
                crimsonServerSession.close(code = CloseReason.Codes.NORMAL.code, reason = "")
                crimsonSessionRegistry.remove(sessionId)
            }

        }.getOrElse {
            println(it)
            throw it
        }
    }
}

/**
 * broadcast data to all crimson server sessions.
 * @param data data to broadcast
 */
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