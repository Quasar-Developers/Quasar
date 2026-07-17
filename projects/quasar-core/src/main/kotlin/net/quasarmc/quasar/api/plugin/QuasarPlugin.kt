package net.quasarmc.quasar.api.plugin

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.minimessage.MiniMessage
import net.quasarmc.quasar.api.addon.AddonManager
import net.quasarmc.quasar.api.registration.CustomRegistry
import net.quasarmc.quasar.api.registration.CustomResourceKey
import net.quasarmc.quasar.api.registration.CustomResourcePointer
import net.quasarmc.quasar.api.registration.HardcodedCustomResourcePointer
import net.quasarmc.quasar.api.registration.RegistrationManager
import net.quasarmc.quasar.api.registration.events.RegistrationEvent
import net.quasarmc.quasar.api.registration.events.RegistryRegistrationEvent
import net.quasarmc.quasar.api.registration.registries.CustomRegistryRegistry
import net.quasarmc.quasar.api.registration.registries.TestRegistry
import net.quasarmc.quasar.core.QuasarCoreAddon
import org.bukkit.NamespacedKey
import org.bukkit.entity.Item
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
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
        // Register event listeners
        TestRegistry.registerEventHandlers(this)

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
            commandSourceStack.sender.sendMessage("Done... Dumping quasar:root:")

            for ((id, registry) in CustomRegistryRegistry) {
                commandSourceStack.sender.sendMessage(
                    MiniMessage.miniMessage().deserialize(
                        "L <#ff8080><bold>$id</bold> <yellow>-<reset> (${registry.count()} entries)"
                    )
                );
            }
        }

        server.pluginManager.registerEvents(object : Listener {
            @EventHandler
            fun onRegisterRegistries(ev: RegistryRegistrationEvent) {
                ev.register("quasar", "numbers", CustomRegistry<Int>())
                ev.register("quasar", "misc", CustomRegistry<Any>())
                ev.register("quasar", "item", CustomRegistry<Item>())
                ev.register(TestRegistry);
            }

            @EventHandler
            fun onRegister(ev: RegistrationEvent) {
                ev.register<CustomRegistry<Int>, _>("quasar", "numbers") { registry ->
                    registry["quasar", "one"]   = 1
                    registry["quasar", "two"]   = 2
                    registry["quasar", "three"] = 3
                    registry["quasar", "four"]  = 4
                    registry["quasar", "five"]  = 5
                    registry["quasar", "six"]   = 6
                    registry["quasar", "seven"] = 7
                    registry["quasar", "eight"] = 8
                    registry["quasar", "nine"]  = 9
                    registry["quasar", "ten"]   = 10
                }

                @Suppress("removal")
                ev.register<CustomRegistry<Any>, _>("quasar", "misc") { registry ->
                    registry["quasar", "dog"]           = "dog"
                    registry["quasar", "eleven"]        = 11
                    registry["quasar", "donotdothis"]   = CustomRegistry<Any>()
                    registry["quasar", "thisisabukkit"] = server
                    registry["quasar", "theresmore"]    = server.spigot()
                }
            }
        }, this)

        // Reload registries
        RegistrationManager.reload()
    }
}
