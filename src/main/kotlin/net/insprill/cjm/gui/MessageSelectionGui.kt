package net.insprill.cjm.gui

import net.insprill.cjm.CustomJoinMessages
import net.insprill.cjm.extension.replacePlaceholders
import net.insprill.cjm.extension.sendInfo
import net.insprill.cjm.message.MessageAction
import net.insprill.cjm.message.MessageVisibility
import net.insprill.cjm.message.types.MessageType
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack

class MessageSelectionGui(private val plugin: CustomJoinMessages) : Listener {

    private val itemFactory = GuiItemFactory(plugin)
    private val selectableActions = listOf(MessageAction.JOIN, MessageAction.QUIT)

    private enum class View {
        ACTION,
        VISIBILITY,
        TYPE,
        ID,
    }

    private data class GuiContext(
        val action: MessageAction? = null,
        val visibility: MessageVisibility? = null,
        val type: MessageType? = null,
    )

    private class GuiHolder(val view: View, val context: GuiContext = GuiContext()) : InventoryHolder {
        lateinit var backingInventory: Inventory

        override fun getInventory(): Inventory = backingInventory
    }

    fun openRoot(player: Player) {
        openActionMenu(player)
    }

    private fun openActionMenu(player: Player) {
        val title = plugin.guiConfig.getOrDefault("Selection-Menu.Action-Title", "&aSelect Message Action")
        val inventory = createInventory(View.ACTION, 27, title)

        selectableActions.forEach { action ->
            val actionNode = actionNode(action)
            val path = "Selection-Menu.Action-Menu.$actionNode"
            val slot = plugin.guiConfig.getOrDefault("$path.Slot", if (action == MessageAction.JOIN) 11 else 15)
            if (slot !in 0 until inventory.size) {
                return@forEach
            }

            val count = MessageVisibility.entries.sumOf { visibility ->
                accessibleTypes(action, visibility, player).size
            }
            val fallbackMaterial = if (action == MessageAction.JOIN) Material.PAPER else Material.BARRIER
            val materialSpec = plugin.guiConfig.getString("$path.Material")
            val materialItem = itemFactory.createItem(materialSpec, fallbackMaterial)
            val name = plugin.guiConfig.getOrDefault("$path.Name", "&e${pretty(action)}")
            val defaultLore = listOf("&7%count% type(s) available", "&8Click to continue")
            val lore = plugin.guiConfig.getStringList("$path.Lore").takeIf { it.isNotEmpty() }?.map { it ?: "" } ?: defaultLore
            val replacedLore = lore.map { it.replace("%count%", count.toString()) }
            val built = buildItem(materialItem, name, replacedLore, selected = false)

            inventory.setItem(slot, built)
        }

        inventory.setItem(26, closeItem())
        player.openInventory(inventory)
    }

    private fun openVisibilityMenu(player: Player, action: MessageAction) {
        val title = plugin.guiConfig.getOrDefault("Selection-Menu.Visibility-Title", "&aChoose Visibility")
        val inventory = createInventory(View.VISIBILITY, 27, title, GuiContext(action = action))

        val visibilitySlots = mapOf(
            MessageVisibility.PUBLIC to 11,
            MessageVisibility.PRIVATE to 15,
        )

        MessageVisibility.entries.forEach { visibility ->
            val count = accessibleTypes(action, visibility, player).size
            val material = if (count > 0) Material.LIME_DYE else Material.GRAY_DYE
            val name = "&e${pretty(visibility)}"
            val lore = if (count > 0) {
                listOf("&7$count type(s) available", "&8Click to continue")
            } else {
                listOf("&8No available options")
            }
            inventory.setItem(visibilitySlots.getValue(visibility), buildItem(material, name, lore))
        }

        inventory.setItem(18, backItem())
        inventory.setItem(26, closeItem())
        player.openInventory(inventory)
    }

    private fun openTypeMenu(player: Player, action: MessageAction, visibility: MessageVisibility) {
        val title = plugin.guiConfig.getOrDefault("Selection-Menu.Type-Title", "&aChoose Message Type")
        val inventory = createInventory(View.TYPE, 54, title, GuiContext(action = action, visibility = visibility))
        val types = accessibleTypes(action, visibility, player)

        if (types.isEmpty()) {
            inventory.setItem(22, buildItem(Material.GRAY_DYE, "&7No available message types", listOf("&8No options for this action.")))
        } else {
            types.take(45).forEachIndexed { index, type ->
                val ids = plugin.messageSender.getAvailableMessageIds(type, path(action, visibility), player)
                val icon = iconForType(type)
                val item = buildItem(
                    icon,
                    "&e${type.name.replaceFirstChar { it.titlecase() }}",
                    listOf("&7${ids.size} ID(s) available", "&8Click to choose ID"),
                )
                inventory.setItem(index, item)
            }
        }

        inventory.setItem(45, backItem())
        inventory.setItem(53, closeItem())
        player.openInventory(inventory)
    }

    private fun openIdMenu(player: Player, action: MessageAction, visibility: MessageVisibility, type: MessageType) {
        val title = plugin.guiConfig.getOrDefault("Selection-Menu.Id-Title", "&aChoose Message ID")
        val inventory = createInventory(View.ID, 54, title, GuiContext(action = action, visibility = visibility, type = type))
        val ids = plugin.messageSender.getAvailableMessageIds(type, path(action, visibility), player)
        val selected = plugin.selectionHandler.getSelection(player, type, visibility, action)

        if (ids.isEmpty()) {
            inventory.setItem(22, buildItem(Material.GRAY_DYE, "&7No available IDs", listOf("&8You do not have permission for this type.")))
        } else {
            ids.take(45).forEachIndexed { index, id ->
                val item = buildMessageSelectionItem(player, type, action, visibility, id, selected == id)
                inventory.setItem(index, item)
            }
        }

        inventory.setItem(45, backItem())
        inventory.setItem(49, resetItem())
        inventory.setItem(53, closeItem())
        player.openInventory(inventory)
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val holder = event.view.topInventory.holder as? GuiHolder ?: return
        event.isCancelled = true

        val player = event.whoClicked as? Player ?: return
        if (event.clickedInventory != event.view.topInventory) {
            return
        }

        when (holder.view) {
            View.ACTION -> handleActionClick(player, event.slot)
            View.VISIBILITY -> handleVisibilityClick(player, holder.context.action, event.slot)
            View.TYPE -> handleTypeClick(player, holder.context.action, holder.context.visibility, event.slot)
            View.ID -> handleIdClick(player, holder.context.action, holder.context.visibility, holder.context.type, event.slot)
        }
    }

    @EventHandler
    fun onInventoryDrag(event: InventoryDragEvent) {
        if (event.view.topInventory.holder !is GuiHolder) {
            return
        }
        event.isCancelled = true
    }

    private fun handleActionClick(player: Player, slot: Int) {
        if (slot == 26) {
            player.closeInventory()
            return
        }

        val joinSlot = plugin.guiConfig.getOrDefault("Selection-Menu.Action-Menu.Join.Slot", 11)
        val leaveSlot = plugin.guiConfig.getOrDefault("Selection-Menu.Action-Menu.Leave.Slot", 15)
        val action = when (slot) {
            joinSlot -> MessageAction.JOIN
            leaveSlot -> MessageAction.QUIT
            else -> return
        }

        val hasPublic = accessibleTypes(action, MessageVisibility.PUBLIC, player).isNotEmpty()
        val hasPrivate = accessibleTypes(action, MessageVisibility.PRIVATE, player).isNotEmpty()
        if (!hasPublic && !hasPrivate) {
            player.sendMessage(color("&cYou do not have permission for any messages for ${pretty(action)}."))
            return
        }

        openVisibilityMenu(player, action)
    }

    private fun handleVisibilityClick(player: Player, action: MessageAction?, slot: Int) {
        if (action == null) {
            return
        }

        when (slot) {
            18 -> {
                openActionMenu(player)
                return
            }
            26 -> {
                player.closeInventory()
                return
            }
        }

        val visibility = when (slot) {
            11 -> MessageVisibility.PUBLIC
            15 -> MessageVisibility.PRIVATE
            else -> return
        }

        if (accessibleTypes(action, visibility, player).isEmpty()) {
            player.sendMessage(color("&cYou do not have permission for any messages in ${pretty(visibility)} ${pretty(action)}."))
            return
        }

        openTypeMenu(player, action, visibility)
    }

    private fun handleTypeClick(player: Player, action: MessageAction?, visibility: MessageVisibility?, slot: Int) {
        if (action == null || visibility == null) {
            return
        }

        when (slot) {
            45 -> {
                openVisibilityMenu(player, action)
                return
            }
            53 -> {
                player.closeInventory()
                return
            }
        }

        val type = accessibleTypes(action, visibility, player).getOrNull(slot) ?: return
        openIdMenu(player, action, visibility, type)
    }

    private fun handleIdClick(player: Player, action: MessageAction?, visibility: MessageVisibility?, type: MessageType?, slot: Int) {
        if (action == null || visibility == null || type == null) {
            return
        }

        when (slot) {
            45 -> {
                openTypeMenu(player, action, visibility)
                return
            }
            49 -> {
                plugin.selectionHandler.clearSelection(player, type, visibility, action)
                plugin.commandManager.sendInfo(
                    player,
                    "cjm.gui.selection.cleared",
                    "%type%", type.name,
                    "%visibility%", pretty(visibility),
                    "%action%", pretty(action),
                )
                openIdMenu(player, action, visibility, type)
                return
            }
            53 -> {
                player.closeInventory()
                return
            }
        }

        val ids = plugin.messageSender.getAvailableMessageIds(type, path(action, visibility), player).take(45)
        val id = ids.getOrNull(slot) ?: return
        plugin.selectionHandler.setSelection(player, type, visibility, action, id)
        plugin.commandManager.sendInfo(
            player,
            "cjm.gui.selection.set",
            "%type%", type.name,
            "%visibility%", pretty(visibility),
            "%action%", pretty(action),
            "%id%", id.toString(),
        )
        openIdMenu(player, action, visibility, type)
    }

    private fun accessibleTypes(action: MessageAction, visibility: MessageVisibility, player: Player): List<MessageType> {
        val messagePath = path(action, visibility)
        return plugin.messageSender.typeMap.values
            .filter { it.isEnabled }
            .sortedBy { it.name }
            .filter { plugin.messageSender.getAvailableMessageIds(it, messagePath, player).isNotEmpty() }
    }

    private fun path(action: MessageAction, visibility: MessageVisibility): String {
        return "${visibility.configSection}.${action.configSection}"
    }

    private fun createInventory(view: View, size: Int, title: String): Inventory {
        return createInventory(view, size, title, GuiContext())
    }

    private fun createInventory(view: View, size: Int, title: String, context: GuiContext): Inventory {
        val holder = GuiHolder(view, context)
        val inventory = Bukkit.createInventory(holder, size, color(title))
        holder.backingInventory = inventory
        return inventory
    }

    private fun buildMessageSelectionItem(
        player: Player,
        type: MessageType,
        action: MessageAction,
        visibility: MessageVisibility,
        id: Int,
        selected: Boolean,
    ): ItemStack {
        val idPath = "${path(action, visibility)}.$id"
        val itemPath = guiItemPath(type, visibility, action, id)

        val permission = plugin.guiConfig.getOrDefault("$itemPath.Permission", type.config.getOrDefault("$idPath.Permission", "cjm.default"))
        val preview = truncatePreview(plugin.guiConfig.getOrDefault("$itemPath.Preview", getPreview(type, idPath, player)))
        val displayId = plugin.guiConfig.getOrDefault("$itemPath.Display-Id", id.toString())

        val defaultMaterial = if (selected) {
            plugin.guiConfig.getOrDefault("Selection-Menu.Defaults.Selected-Material", "LIME_DYE")
        } else {
            plugin.guiConfig.getOrDefault("Selection-Menu.Defaults.Material", "PAPER")
        }
        val materialSpec = plugin.guiConfig.getOrDefault("$itemPath.Material", type.config.getOrDefault("$idPath.Display.Material", defaultMaterial))
        val item = itemFactory.createItem(materialSpec, iconForType(type))

        val defaultName = plugin.guiConfig.getOrDefault("Selection-Menu.Defaults.Name", "&eID %display_id%")
        val rawName = plugin.guiConfig.getOrDefault("$itemPath.Name", type.config.getOrDefault("$idPath.Display.Name", defaultName))

        val rawLore = when {
            plugin.guiConfig.contains("$itemPath.Lore") -> plugin.guiConfig.getStringList("$itemPath.Lore")
            type.config.contains("$idPath.Display.Lore") -> type.config.getStringList("$idPath.Display.Lore")
            else -> plugin.guiConfig.getStringList("Selection-Menu.Defaults.Lore")
        }.map { it ?: "" }

        val replacements = mapOf(
            "%id%" to id.toString(),
            "%display_id%" to displayId,
            "%type%" to type.name,
            "%action%" to pretty(action),
            "%visibility%" to pretty(visibility),
            "%permission%" to permission,
            "%preview%" to preview,
        )

        val name = applyReplacements(rawName, replacements)
        val lore = rawLore.map { applyReplacements(it, replacements) }
        return buildItem(item, name, lore, selected)
    }

    private fun getPreview(type: MessageType, idPath: String, player: Player): String {
        val basePath = "$idPath.${type.key}"
        val section = type.config.getSection(basePath) ?: return "-"
        val key = section.singleLayerKeySet()
            .sortedWith(compareBy<String> { it.toIntOrNull() ?: Int.MAX_VALUE }.thenBy { it })
            .firstOrNull()
            ?: return "-"
        val chosenPath = "$basePath.$key"

        val rawPreview = when (type.name.lowercase()) {
            "chat" -> type.config.getStringList(chosenPath).mapNotNull { it }.firstOrNull().orEmpty()
            "actionbar", "bossbar" -> type.config.getString("$chosenPath.Message") ?: ""
            "title" -> {
                val title = type.config.getString("$chosenPath.Title") ?: ""
                val subTitle = type.config.getString("$chosenPath.SubTitle") ?: ""
                if (subTitle.isBlank()) title else "$title / $subTitle"
            }
            "sound" -> type.config.getString("$chosenPath.Sound") ?: ""
            else -> type.config.getString(chosenPath) ?: ""
        }

        if (rawPreview.isBlank()) {
            return "-"
        }

        val withPlaceholders = rawPreview.replacePlaceholders(player)
        val translated = ChatColor.translateAlternateColorCodes('&', withPlaceholders)
        val stripped = ChatColor.stripColor(translated) ?: translated
        return stripped.replace(Regex("<[^>]+>"), "")
    }

    private fun truncatePreview(preview: String): String {
        if (preview.length <= 60) {
            return preview
        }
        return preview.substring(0, 57) + "..."
    }

    private fun applyReplacements(input: String, replacements: Map<String, String>): String {
        var out = input
        replacements.forEach { (key, value) ->
            out = out.replace(key, value)
        }
        return out
    }

    private fun actionNode(action: MessageAction): String {
        return if (action == MessageAction.QUIT) "Leave" else "Join"
    }

    private fun guiItemPath(type: MessageType, visibility: MessageVisibility, action: MessageAction, id: Int): String {
        val base = "Selection-Menu.Items.${type.name.lowercase()}.${visibility.name.lowercase()}"
        val actionName = when (action) {
            MessageAction.JOIN -> "join"
            MessageAction.QUIT -> {
                val leavePath = "$base.leave.$id"
                val quitPath = "$base.quit.$id"
                if (plugin.guiConfig.contains(leavePath) || !plugin.guiConfig.contains(quitPath)) "leave" else "quit"
            }
            MessageAction.FIRST_JOIN -> "join"
        }
        return "$base.$actionName.$id"
    }

    private fun iconForType(type: MessageType): Material {
        return when (type.name.lowercase()) {
            "chat" -> Material.BOOK
            "sound" -> Material.NOTE_BLOCK
            "title" -> Material.OAK_SIGN
            "bossbar" -> Material.WITHER_ROSE
            "actionbar" -> Material.PAPER
            else -> Material.PAPER
        }
    }

    private fun closeItem(): ItemStack {
        return buildItem(Material.BARRIER, "&cClose", listOf("&8Click to close menu"))
    }

    private fun backItem(): ItemStack {
        return buildItem(Material.ARROW, "&eBack", listOf("&8Go to previous menu"))
    }

    private fun resetItem(): ItemStack {
        return buildItem(Material.BUCKET, "&eReset Selection", listOf("&8Use automatic priority selection"))
    }

    private fun buildItem(material: Material, name: String, lore: List<String>, selected: Boolean = false): ItemStack {
        return buildItem(ItemStack(material), name, lore, selected)
    }

    private fun buildItem(item: ItemStack, name: String, lore: List<String>, selected: Boolean = false): ItemStack {
        val meta = item.itemMeta ?: return item
        meta.setDisplayName(color(name))
        meta.lore = lore.map { color(it) }
        if (selected) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true)
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS)
        }
        item.itemMeta = meta
        return item
    }

    private fun pretty(value: Any): String {
        if (value is MessageAction && value == MessageAction.QUIT) {
            return "Leave"
        }
        return value.toString().lowercase().replace('_', ' ').replaceFirstChar { it.titlecase() }
    }

    private val hexPattern = java.util.regex.Pattern.compile("#[a-fA-F\\d]{6}")
    private val formattedHexPattern = java.util.regex.Pattern.compile("[?:{<&]?#[a-fA-F\\d]{6}[}>]?")

    private fun color(text: String): String {
        var workingStr = net.insprill.cjm.formatting.LegacyGradientUtils.parseGradients(text)
        
        if (net.insprill.spigotutils.MinecraftVersion.isAtLeast(net.insprill.spigotutils.MinecraftVersion.v1_16_0)) {
            val formattedMatcher = formattedHexPattern.matcher(workingStr)
            while (formattedMatcher.find()) {
                val hex = formattedMatcher.group()
                val hexMatcher = hexPattern.matcher(hex)
                if (hexMatcher.find()) {
                    workingStr = workingStr.replace(formattedMatcher.group(), net.md_5.bungee.api.ChatColor.of(hexMatcher.group()).toString())
                    formattedMatcher.reset(workingStr)
                }
            }
        }
        
        return ChatColor.translateAlternateColorCodes('&', workingStr)
    }

}
