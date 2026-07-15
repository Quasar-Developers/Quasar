package net.quasarmc.quasar.api.plugin

import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.TextColor
import net.quasarmc.quasar.api.addon.AddonManager
import net.quasarmc.quasar.api.registration.RegistrationManager
import net.quasarmc.quasar.core.QuasarCoreAddon
import org.bukkit.Color
import org.bukkit.NamespacedKey
import org.bukkit.plugin.java.JavaPlugin

class QuasarPlugin : JavaPlugin() {
    companion object {
        lateinit var plugin: QuasarPlugin;

        val LOGGER get() = plugin.logger;
    };

    /**
     * Quasar core addon. This is the entry point to all core code.
     */
    private val addon = QuasarCoreAddon();

    override fun onLoad() {
        plugin = this;

        // Register core addon
        AddonManager.register(addon);
    }

    override fun onEnable() {
        // Reload registries
        RegistrationManager.reload()

        // TODO: This is bad, make this into a /quasar subcommand or something
        registerCommand("quasar_version") { commandSourceStack, args ->
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

        registerCommand("quasar_reload") { commandSourceStack, args ->
            commandSourceStack.sender.sendMessage("Reloading!")
            RegistrationManager.reload()
            commandSourceStack.sender.sendMessage("Done.")
        }
    }
}
