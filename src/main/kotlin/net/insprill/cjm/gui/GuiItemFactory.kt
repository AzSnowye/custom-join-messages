package net.insprill.cjm.gui

import net.insprill.cjm.CustomJoinMessages
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

class GuiItemFactory(private val plugin: CustomJoinMessages) {

    fun createItem(materialSpec: String?, fallback: Material): ItemStack {
        if (materialSpec.isNullOrBlank()) {
            return ItemStack(fallback)
        }

        val normalized = materialSpec.trim()
        val hdbPrefix = normalized.substringBefore(':').uppercase()
        if (hdbPrefix == "HDB" || hdbPrefix == "HEADDATABASE") {
            val id = normalized.substringAfter(':', "").trim()
            getHeadDatabaseItem(id)?.let { return it }
            return ItemStack(fallback)
        }

        val material = runCatching { Material.valueOf(normalized.uppercase()) }.getOrNull() ?: fallback
        return ItemStack(material)
    }

    private fun getHeadDatabaseItem(id: String): ItemStack? {
        if (id.isBlank()) {
            return null
        }
        if (!plugin.server.pluginManager.isPluginEnabled("HeadDatabase")) {
            return null
        }

        return runCatching {
            val apiClass = Class.forName("me.arcaniax.hdb.api.HeadDatabaseAPI")
            val api = apiClass.getDeclaredConstructor().newInstance()
            val method = apiClass.getMethod("getItemHead", String::class.java)
            method.invoke(api, id) as? ItemStack
        }.getOrNull()
    }

}

