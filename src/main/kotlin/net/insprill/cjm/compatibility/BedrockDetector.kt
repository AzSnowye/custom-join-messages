package net.insprill.cjm.compatibility

import java.util.UUID
import org.bukkit.Bukkit
import org.bukkit.entity.Player

object BedrockDetector {

    private val floodgateApiClass by lazy {
        runCatching { Class.forName("org.geysermc.floodgate.api.FloodgateApi") }.getOrNull()
    }

    private val geyserApiClass by lazy {
        runCatching { Class.forName("org.geysermc.geyser.api.GeyserApi") }.getOrNull()
    }

    fun isBedrockPlayer(player: Player): Boolean {
        return isFloodgatePlayer(player) || isGeyserPlayer(player.uniqueId)
    }

    private fun isFloodgatePlayer(player: Player): Boolean {
        if (!Bukkit.getPluginManager().isPluginEnabled("floodgate")) {
            return false
        }
        val apiClass = floodgateApiClass ?: return false
        return runCatching {
            val api = apiClass.getMethod("getInstance").invoke(null)
            val uuidMethod = api.javaClass.methods.firstOrNull {
                it.name == "isFloodgatePlayer" && it.parameterTypes.contentEquals(arrayOf(UUID::class.java))
            }
            if (uuidMethod != null) {
                return@runCatching uuidMethod.invoke(api, player.uniqueId) as Boolean
            }
            val playerMethod = api.javaClass.methods.firstOrNull {
                it.name == "isFloodgatePlayer" && it.parameterTypes.contentEquals(arrayOf(Player::class.java))
            }
            if (playerMethod != null) {
                return@runCatching playerMethod.invoke(api, player) as Boolean
            }
            false
        }.getOrDefault(false)
    }

    private fun isGeyserPlayer(uuid: UUID): Boolean {
        if (!Bukkit.getPluginManager().isPluginEnabled("Geyser-Spigot") && !Bukkit.getPluginManager().isPluginEnabled("Geyser")) {
            return false
        }
        val apiClass = geyserApiClass ?: return false
        return runCatching {
            val api = apiClass.getMethod("api").invoke(null)
            val method = api.javaClass.methods.firstOrNull {
                it.name == "connectionByUuid" && it.parameterTypes.contentEquals(arrayOf(UUID::class.java))
            } ?: return@runCatching false
            method.invoke(api, uuid) != null
        }.getOrDefault(false)
    }

}

