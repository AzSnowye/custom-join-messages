package net.insprill.cjm.formatting

import net.md_5.bungee.api.ChatColor
import java.awt.Color
import java.util.regex.Pattern

object LegacyGradientUtils {

    private val GRADIENT_PATTERN = Pattern.compile("<gradient:([#0-9a-fA-F:]+)>(.*?)</gradient>", Pattern.CASE_INSENSITIVE)

    fun parseGradients(text: String): String {
        if (!net.insprill.spigotutils.MinecraftVersion.isAtLeast(net.insprill.spigotutils.MinecraftVersion.v1_16_0)) {
            return text
        }
        var workingStr = text
        val matcher = GRADIENT_PATTERN.matcher(workingStr)
        val sb = StringBuffer()

        while (matcher.find()) {
            val colorsStr = matcher.group(1)
            val content = matcher.group(2)
            
            val colors = colorsStr.split(":").mapNotNull { parseColor(it) }
            if (colors.size < 2 || content.isEmpty()) {
                matcher.appendReplacement(sb, matcher.group(0))
                continue
            }

            val interpolated = interpolateGradient(content, colors)
            matcher.appendReplacement(sb, interpolated)
        }
        matcher.appendTail(sb)
        return sb.toString()
    }

    private fun parseColor(hex: String): Color? {
        val cleanHex = if (hex.startsWith("#")) hex else "#$hex"
        return try {
            Color.decode(cleanHex)
        } catch (e: NumberFormatException) {
            null
        }
    }

    private fun interpolateGradient(text: String, colors: List<Color>): String {
        val contentChars = text.toCharArray()
        
        // Count characters ignoring legacy color codes
        var length = 0
        var i = 0
        while (i < contentChars.size) {
            if (contentChars[i] == '&' && i + 1 < contentChars.size && "0123456789a-fA-Fk-oK-OrR".contains(contentChars[i + 1])) {
                i += 2
                continue
            }
            length++
            i++
        }

        if (length <= 1) {
            return ChatColor.of(colors.first()).toString() + text
        }

        val stepPerColor = (length - 1).toFloat() / (colors.size - 1)
        val sb = StringBuilder()
        
        var charIndex = 0
        var j = 0
        var currentFormats = ""
        while (j < contentChars.size) {
            if (contentChars[j] == '&' && j + 1 < contentChars.size && "0123456789a-fA-Fk-oK-OrR".contains(contentChars[j + 1])) {
                val code = contentChars[j + 1].lowercaseChar()
                if (code == 'r') {
                    currentFormats = ""
                } else if ("0123456789abcdef".contains(code)) {
                    currentFormats = "" // color code resets formatting
                } else {
                    currentFormats += "&" + code
                }
                j += 2
                continue
            }

            val colorIndex = (charIndex / stepPerColor).toInt()
            val startColor = colors[colorIndex.coerceAtMost(colors.size - 2)]
            val endColor = colors[(colorIndex + 1).coerceAtMost(colors.size - 1)]
            val factor = if (stepPerColor == 0f) 0f else (charIndex - colorIndex * stepPerColor) / stepPerColor
            
            val color = interpolate(startColor, endColor, factor.coerceIn(0f, 1f))
            sb.append(ChatColor.of(color).toString()).append(currentFormats).append(contentChars[j])
            
            charIndex++
            j++
        }
        return sb.toString()
    }

    private fun interpolate(color1: Color, color2: Color, factor: Float): Color {
        val r = (color1.red + (color2.red - color1.red) * factor).toInt()
        val g = (color1.green + (color2.green - color1.green) * factor).toInt()
        val b = (color1.blue + (color2.blue - color1.blue) * factor).toInt()
        return Color(r, g, b)
    }
}
