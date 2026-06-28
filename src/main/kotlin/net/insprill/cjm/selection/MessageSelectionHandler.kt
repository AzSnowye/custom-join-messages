package net.insprill.cjm.selection

import de.leonhard.storage.SimplixBuilder
import de.leonhard.storage.internal.FlatFile
import de.leonhard.storage.internal.settings.ReloadSettings
import net.insprill.cjm.message.MessageAction
import net.insprill.cjm.message.MessageVisibility
import net.insprill.cjm.message.types.MessageType
import org.bukkit.OfflinePlayer
import org.bukkit.plugin.Plugin
import java.nio.file.Paths

class MessageSelectionHandler(plugin: Plugin) {

    private val selectionConfig: FlatFile = SimplixBuilder.fromPath(Paths.get("${plugin.dataFolder}/data/selections.json"))
        .setReloadSettings(ReloadSettings.MANUALLY)
        .createJson()

    fun getSelection(player: OfflinePlayer, type: MessageType, visibility: MessageVisibility, action: MessageAction): Int? {
        val path = getPath(player, type, visibility, action)
        return selectionConfig.getInt(path).takeIf { selectionConfig.contains(path) }
    }

    fun setSelection(player: OfflinePlayer, type: MessageType, visibility: MessageVisibility, action: MessageAction, id: Int) {
        selectionConfig.set(getPath(player, type, visibility, action), id)
    }

    fun clearSelection(player: OfflinePlayer, type: MessageType, visibility: MessageVisibility, action: MessageAction) {
        selectionConfig.remove(getPath(player, type, visibility, action))
    }

    private fun getPath(player: OfflinePlayer, type: MessageType, visibility: MessageVisibility, action: MessageAction): String {
        return "${player.uniqueId}.${type.name.lowercase()}.${visibility.name.lowercase()}.${action.name.lowercase()}"
    }

}

