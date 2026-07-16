package net.quasarmc.quasar.api.plugin

import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.minimessage.MiniMessage
import net.quasarmc.quasar.api.addon.AddonManager
import org.bukkit.Color
import org.bukkit.NamespacedKey
import org.bukkit.plugin.java.JavaPlugin

class QuasarPlugin : JavaPlugin {
    constructor() {
        // TODO: Placeholder command. (so don't bother softcoding this)
        registerCommand("quasar") { commandSourceStack, args ->
            var subcmd = args.getOrNull(0)
            when (subcmd) {
                "about" -> {
                    commandSourceStack.sender.sendMessage(MiniMessage.miniMessage().deserialize(
                        "<#ffffff>Quasar <#38e5e4>v[VERSION]\n"
                        +"<#e0e0e0>    Description currently unfinished.\n"
                        +"<#a0e0ff>You can access this plugin's source code <click:open_url:\"https://quasarmc.net/\"><u>here</u></click>.\n"
                        +"<#ffffff>Do <#a0e0ff>/quasar help</#a0e0ff> for a list of subcommands."
                    ))
                }
                "help" -> {
                    commandSourceStack.sender.sendMessage(MiniMessage.miniMessage().deserialize(
                        "<#ffffff>Quasar's subcommands:\n"
                        +"<#e0e0e0>    /quasar about - Display Quasar's version and information about it.\n"
                        +"<#e0e0e0>    /quasar addons - Display all currently installed addons.\n"
                        +"<#e0e0e0>    /quasar help - Display help about Quasar's subcommands."
                    ))
                }
                "addons" -> {
                    var addons = AddonManager.addons
                    commandSourceStack.sender.sendMessage(MiniMessage.miniMessage().deserialize(
                        "<#ffffff>Current addons:\n"
                        + addons.values.joinToString("\n"){a ->
                            "<#e0e0e0>    ${a.name} [${a.id}] ${a.version}" as CharSequence
                        }
                    ))
                }
                else -> {
                    commandSourceStack.sender.sendMessage(MiniMessage.miniMessage().deserialize(
                        "<#ffa0a0>Unknown subcommand${if(subcmd == null){""} else {" \"$subcmd\""}}.\n"
                        +"Type <#a0e0ff>/quasar help</#a0e0ff> for a list of subcommands."
                    ))
                }
            }
        }
    }
}
