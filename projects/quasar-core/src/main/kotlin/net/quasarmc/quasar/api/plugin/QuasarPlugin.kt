package net.quasarmc.quasar.api.plugin

import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.TextColor
import net.quasarmc.quasar.api.addon.AddonManager
import org.bukkit.Color
import org.bukkit.NamespacedKey
import org.bukkit.plugin.java.JavaPlugin

class QuasarPlugin : JavaPlugin {
    constructor() {
        // TODO: This is bad, make this into a /quasar subcommand or something
        registerCommand("version") { commandSourceStack, args ->
            commandSourceStack.sender.sendMessage(
                Component.textOfChildren(
                    Component.text("Quasar ").color(TextColor.fromHexString("#38e5e4")),
                    Component.text("[VERSION]"),
                    Component.newline(),
                    Component.text("Quasar is licensed under the GNU Affero General Public License 3.0, you " +
                            "are free to modify and distribute it. You should have access to the source " +
                            "code of the version running on this server, if a modified version of the plugin " +
                            "is being hosted without sharing the source, please contact @xwashere or @corrstud " +
                            "on Discord."),
                    Component.newline(),
                    Component.text("You can access an unmodified version of the plugin's code "),
                    Component.text("here").clickEvent(ClickEvent.openUrl("https://quasarmc.net")),
                    Component.text(".")
                )
            )
        }
    }

    override fun onEnable() {
        AddonManager.registerListeners(this)
    }
}

