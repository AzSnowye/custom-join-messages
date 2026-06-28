package net.insprill.cjm.formatting

import net.insprill.spigotutils.MinecraftVersion
import net.insprill.spigotutils.ServerEnvironment
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import net.md_5.bungee.api.chat.BaseComponent
import net.md_5.bungee.chat.ComponentSerializer
import org.bukkit.Bukkit
import java.util.Locale
import java.util.regex.Pattern

class MiniMessageFormatter : Formatter {

    private val gsonSerializer: GsonComponentSerializer?

    init {
        if (isCompatible()) {
            @Suppress("DEPRECATION")
            val unsafe = Bukkit.getUnsafe()
            val clazz = unsafe::class.java
            gsonSerializer = clazz.getMethod("gsonComponentSerializer").invoke(unsafe) as GsonComponentSerializer
        } else {
            gsonSerializer = null
        }
    }

    override fun format(str: String): Array<BaseComponent> {
        if (!isCompatible()) throw IllegalStateException("MiniMessageFormatter isn't compatible with this server!")
        val json = gsonSerializer?.serialize(MiniMessage.miniMessage().deserialize(convertLegacyCodes(str)))
        return ComponentSerializer.parse(json)
    }

    private fun convertLegacyCodes(str: String): String {
        var newStr = str

        // Convert common legacy hex wrappers into MiniMessage hex tags.
        val formattedHexMatcher = FORMATTED_HEX_PATTERN.matcher(newStr)
        while (formattedHexMatcher.find()) {
            val wrappedHex = formattedHexMatcher.group()
            val hex = wrappedHex.substring(1, 8)
            newStr = newStr.replace(wrappedHex, "<$hex>")
        }

        val legacyMatcher = LEGACY_COLOR_PATTERN.matcher(newStr)
        val sb = StringBuffer()
        while (legacyMatcher.find()) {
            val code = legacyMatcher.group(2).lowercase(Locale.ROOT)
            val replacement = LEGACY_REPLACEMENTS[code] ?: legacyMatcher.group()
            legacyMatcher.appendReplacement(sb, replacement)
        }
        legacyMatcher.appendTail(sb)

        return sb.toString()
    }

    companion object {
        private val LEGACY_COLOR_PATTERN = Pattern.compile("([&§])([0-9a-fk-orA-FK-OR])")
        private val FORMATTED_HEX_PATTERN = Pattern.compile("[<{&]#[a-fA-F\\d]{6}[>}]?")

        val LEGACY_REPLACEMENTS = HashMap<String, String>()

        init {
            LEGACY_REPLACEMENTS["0"] = "<black>"
            LEGACY_REPLACEMENTS["1"] = "<dark_blue>"
            LEGACY_REPLACEMENTS["2"] = "<dark_green>"
            LEGACY_REPLACEMENTS["3"] = "<dark_aqua>"
            LEGACY_REPLACEMENTS["4"] = "<dark_red>"
            LEGACY_REPLACEMENTS["5"] = "<dark_purple>"
            LEGACY_REPLACEMENTS["6"] = "<gold>"
            LEGACY_REPLACEMENTS["7"] = "<gray>"
            LEGACY_REPLACEMENTS["8"] = "<dark_gray>"
            LEGACY_REPLACEMENTS["9"] = "<blue>"
            LEGACY_REPLACEMENTS["a"] = "<green>"
            LEGACY_REPLACEMENTS["b"] = "<aqua>"
            LEGACY_REPLACEMENTS["c"] = "<red>"
            LEGACY_REPLACEMENTS["d"] = "<light_purple>"
            LEGACY_REPLACEMENTS["e"] = "<yellow>"
            LEGACY_REPLACEMENTS["f"] = "<white>"
            LEGACY_REPLACEMENTS["k"] = "<magic>"
            LEGACY_REPLACEMENTS["l"] = "<bold>"
            LEGACY_REPLACEMENTS["m"] = "<strikethrough>"
            LEGACY_REPLACEMENTS["n"] = "<underline>"
            LEGACY_REPLACEMENTS["o"] = "<italic>"
            LEGACY_REPLACEMENTS["r"] = "<reset>"
        }

        fun isCompatible(): Boolean {
            return ServerEnvironment.isPaper() && MinecraftVersion.isAtLeast(MinecraftVersion.v1_18_2)
        }
    }

}
