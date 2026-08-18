package net.quasarmc.quasar.api.addon

import net.quasarmc.quasar.api.addon.exceptions.AddonRegistrationException
import net.quasarmc.quasar.api.addon.registries.AddonRegistry
import net.quasarmc.quasar.core.QuasarPlugin
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.Server
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.server.PluginEnableEvent
import org.bukkit.event.server.ServerLoadEvent
import org.bukkit.event.world.WorldInitEvent

/**
 * Central class to manage all Quasar API addons and their life-cycles.
 */
object AddonManager : Listener {
    /**
     * Addon registration is complete and no new addons can be registered.
     */
    var addonsFinalized = false;

    /**
     * Register a new addon.
     * Can only be called during plugin bootstrapping.
     *
     * @param addon Addon to register
     */
    fun register(addon: Addon<*>) {
        if (addonsFinalized)
            throw AddonRegistrationException("Attempted to register an addon after bootstrapping")

        AddonRegistry[addon.identifier] = addon
    }

    /**
     * Finish initializing the addon API and the loaded addons.
     */
    internal fun finalizeInitialization() {
        // Set all addons to ACTIVE
        for ((_, addon) in AddonRegistry) {
            addon.state = AddonState.ACTIVE
        }

        // No more addon registrations (this should probably happen earlier)
        addonsFinalized = true;
    }

    @EventHandler
    private fun onPluginEnable(ev: PluginEnableEvent) {
        // Switch the addons the plugin provides to the INIT state.
        // Technically this event can get called outside of server initialization, but the default paper
        // server does not provide users with a way to enable/disable plugins, and I don't think there's
        // any widely used plugins that provide that functionality, even though the bukkit API does (wow legacy code!)
        var allReady = true;
        for ((_, addon) in AddonRegistry) {
            if (addon.plugin != ev.plugin) {
                if (addon.state != AddonState.INIT)
                    allReady = false

                continue
            }

            addon.state = AddonState.INIT
        }

        // This should probably get called in a more obvious location
        if (allReady)
            QuasarPlugin.plugin.finalizeAPIStartup()
    }

    @EventHandler
    private fun onServerLoad(ev: ServerLoadEvent) {
        // If for whatever reason the API did not finish setting up addons, crash the server. We could
        // keep the server running, but we wouldn't be able to reset every registry, which could lead
        // to more errors later down the line.
        if (ev.type != ServerLoadEvent.LoadType.STARTUP || addonsFinalized)
            return;

        QuasarPlugin.LOGGER.severe("Addon initialization failed, some plugins are not working correctly!")
        QuasarPlugin.LOGGER.severe("This is not an issue with Quasar, submit an issue to the problematic addons before submitting one to Quasar.")
        QuasarPlugin.LOGGER.severe("Loaded plugins:")
        for ((_, addon) in AddonRegistry) {
            QuasarPlugin.LOGGER.severe("- ${addon.name} [${addon.version}] (${addon.identifier} from ${addon.plugin.name})")
            QuasarPlugin.LOGGER.severe("| STATE: ${addon.state} ${
                if (addon.state != AddonState.INIT && addon.state != AddonState.ACTIVE) { "(!!!)" } else { "" }
            }")
            QuasarPlugin.LOGGER.severe("| AUTHOR: ${addon.author}")
            QuasarPlugin.LOGGER.severe("| SOURCE: ${addon.sourceURL}")
        }

        Bukkit.shutdown()
    }
}
