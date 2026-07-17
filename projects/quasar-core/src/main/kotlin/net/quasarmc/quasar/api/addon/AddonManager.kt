package net.quasarmc.quasar.api.addon

import org.bukkit.Bukkit
import org.bukkit.event.Listener
import org.bukkit.plugin.Plugin

/**
 * Central class to manage all Quasar API addons and their life-cycles.
 */
object AddonManager {
    val addons = HashMap<String, Addon>();

    /**
     * Register a new addon.
     *
     * @param addon Addon to register
     */
    fun register(addon: Addon) {
        if (addons.contains(addon.id))
            throw AddonRegistrationException("An addon with the id ${addon.id} has already been registered")

        addons.set(addon.id, addon)
    }

    fun registerListeners(plugin: Plugin) {
        addons.values.forEach {
            it.registerListener(plugin)
        }
    }

    fun enable(id: String){
        addons[id]?.enable()
    }

    fun disable(id: String){
        addons[id]?.disable()
    }
}

/**
 * Exception indicating an unrecoverable error during plugin registration.
 */
class AddonRegistrationException(message: String) : RuntimeException(message);
