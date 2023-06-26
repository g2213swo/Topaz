package net.momirealms.topaz.bukkit

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.command.CommandSender
import org.bukkit.command.ConsoleCommandSender
import org.bukkit.entity.Player

object AdventureUtils {

    private val legacyMapping = mapOf(
        '0' to "<black>",
        '1' to "<dark_blue>",
        '2' to "<dark_green>",
        '3' to "<dark_aqua>",
        '4' to "<dark_red>",
        '5' to "<dark_purple>",
        '6' to "<gold>",
        '7' to "<gray>",
        '8' to "<dark_gray>",
        '9' to "<blue>",
        'a' to "<green>",
        'b' to "<aqua>",
        'c' to "<red>",
        'd' to "<light_purple>",
        'e' to "<yellow>",
        'f' to "<white>",
        'r' to "<reset>",
        'l' to "<bold>",
        'm' to "<strikethrough>",
        'o' to "<italic>",
        'n' to "<underlined>",
        'k' to "<obfuscated>"
    )

    @JvmStatic
    fun getComponent(
        raw : String
    ) : Component {
        return MiniMessage.miniMessage().deserialize(replaceLegacy(raw))
    }

    @JvmStatic
    fun sendPlayerMessage(
        player : Player,
        message : String
    ) {
        BukkitPluginImpl.adventure?.player(player)?.sendMessage(getComponent(message))
    }

    @JvmStatic
    fun sendConsoleMessage(
        message : String
    ) {
        BukkitPluginImpl.adventure?.console()?.sendMessage(getComponent(message));
    }

    @JvmStatic
    fun sendMessage(
        sender : CommandSender,
        message : String
    ) {
        when (sender) {
            is Player -> sendPlayerMessage(sender, message)
            is ConsoleCommandSender -> sendConsoleMessage(message)
        }
    }

    @JvmStatic
    fun replaceLegacy(
        legacy: String
    ): String {
        val stringBuilder = StringBuilder()
        var i = 0
        while (i < legacy.length) {
            if (!isColorCode(legacy[i])) {
                stringBuilder.append(legacy[i])
                i++
                continue
            }
            if (i + 1 >= legacy.length) {
                stringBuilder.append(legacy[i])
                i++
                continue
            }
            val mappedValue = legacyMapping[legacy[i + 1]]
            i += if (mappedValue != null) {
                stringBuilder.append(mappedValue)
                2
            } else if (legacy[i + 1] == 'x' && i + 13 < legacy.length &&
                (2..12 step 2).all { isColorCode(legacy[i + it]) }) {
                stringBuilder
                    .append("<#")
                    .append((3..13 step 2).map { legacy[i + it] }.joinToString(""))
                    .append(">")
                13
            } else {
                stringBuilder.append(legacy[i])
                1
            }
        }
        return stringBuilder.toString()
    }

    @JvmStatic
    fun isColorCode(
        c: Char
    ): Boolean {
        return c == '§' || c == '&'
    }
}