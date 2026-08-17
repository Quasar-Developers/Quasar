package net.quasarmc.quasar.core

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.minimessage.MiniMessage
import net.quasarmc.quasar.api.registration.RegistrationManager
import net.quasarmc.quasar.api.registration.events.RegistryRegistrationEvent
import net.quasarmc.quasar.api.registration.registries.CustomRegistryRegistry
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin
import io.papermc.paper.plugin.bootstrap.BootstrapContext
import io.papermc.paper.plugin.bootstrap.PluginBootstrap
import io.papermc.paper.plugin.bootstrap.PluginProviderContext
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import net.quasarmc.quasar.api.addon.AddonManager
import net.quasarmc.quasar.api.addon.Addon
import net.quasarmc.quasar.api.addon.registries.AddonRegistry
import org.bukkit.NamespacedKey
import org.bukkit.event.server.PluginEnableEvent

@Suppress("UnstableApiUsage")
class QuasarPluginBootstrap : PluginBootstrap {
    /**
     * The core addon for Quasar.
     */
    val core = QuasarCoreAddon();

    override fun bootstrap(context: BootstrapContext) {
        // add our datapack
        context.lifecycleManager.registerEventHandler(LifecycleEvents.DATAPACK_DISCOVERY.newHandler {
            it.registrar().discoverPack(this.javaClass.getResource("/datapack")!!.toURI(), "data");
        })

        // register the core addon
        AddonManager.register(core)
    }

    override fun createPlugin(context: PluginProviderContext): JavaPlugin {
        return QuasarPlugin(core)
    }
}

class QuasarPlugin(
    val addon: QuasarCoreAddon
) : JavaPlugin() {
    companion object {
        lateinit var plugin: QuasarPlugin;

        val LOGGER get() = plugin.logger;
    };

    override fun onLoad() {
        plugin = this;

        addon.attachPlugin(this)
    }

    override fun onEnable() {
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

        server.pluginManager.registerEvents(AddonManager, this)
        server.pluginManager.registerEvents(object : Listener {
            // TODO: This should be handled by the core addon.
            @EventHandler
            fun onRegisterRegistries(ev: RegistryRegistrationEvent) {
                ev.register("quasar", "root", CustomRegistryRegistry);
                ev.register("quasar", "addons", AddonRegistry)
            }
        }, this)
    }

    /**
     * There's no easy way to know when all addons have been enabled and have attached listeners to Quasar's events,
     * as there's no startup event that runs after onEnable but before things we care about, like world loading.
     *
     * This *should* be called when all addons are ready to do things like setting up registries.
     *
     * The method gets called from [AddonManager.onPluginEnable]
     */
    internal fun finalizeAPIStartup() {
        RegistrationManager.reload()

        // TODO: Make this an event???
        // This control flow is shit.
        AddonManager.finalizeInitialization();
    }
}

/**
 * Core addon for Quasar, containing all built-in content.
 */
class QuasarCoreAddon : Addon<QuasarPlugin>() {
    override val identifier  = NamespacedKey("quasar", "quasar.core")
    override val name        = "Quasar Core"
    override val description = "Quasar core content"
    override val author      = "Quasar Contributors"
    override val version     = "v3.0.0.1"
    override val sourceURL   = "https://github.com/Quasar-Developers/Quasar"
}
