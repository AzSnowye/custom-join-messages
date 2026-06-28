package net.insprill.cjm.message

import net.insprill.cjm.compatibility.BedrockDetector
import net.insprill.cjm.message.types.MessageType
import org.bukkit.Bukkit
import org.bukkit.entity.Player

enum class MessageCondition(private val condition: (MessageType, String, Player?) -> Boolean) {
    MAX_PLAYERS({ msg, path, _ ->
        val maxPlayers = msg.config.getOrDefault("$path.Max-Players", -1)
        maxPlayers < 1 || Bukkit.getOnlinePlayers().size < maxPlayers
    }),
    MIN_PLAYERS({ msg, path, _ ->
        val minPlayers = msg.config.getOrDefault("$path.Min-Players", -1)
        minPlayers < 1 || Bukkit.getOnlinePlayers().size > minPlayers
    }),
    PLAYER_TYPE({ msg, path, player ->
        when (msg.config.getOrDefault("$path.Player-Type", "ANY").uppercase()) {
            "ANY" -> true
            "JAVA" -> player != null && !BedrockDetector.isBedrockPlayer(player)
            "BEDROCK" -> player != null && BedrockDetector.isBedrockPlayer(player)
            else -> true
        }
    }),
    ;

    fun checkCondition(msg: MessageType, messagePath: String, player: Player? = null): Boolean {
        return condition.invoke(msg, messagePath, player)
    }

    companion object {
        fun checkAllConditions(msg: MessageType, messagePath: String, player: Player? = null): Boolean {
            return entries.all { it.checkCondition(msg, messagePath, player) }
        }
    }

}
