package net.quasarmc.quasar.api.addon

import org.bukkit.plugin.java.JavaPlugin

/**
 * Central class to manage all Quasar API addons and their life-cycles.
 */
object AddonManager {
    val addons = HashMap<String, Addon<*>>();

    /**
     * Register a new addon.
     * Can only be called during plugin bootstrapping.
     *
     * @param addon Addon to register
     */
    fun register(addon: Addon<*>) {
        if (addons.contains(addon.id))
            throw AddonRegistrationException("An addon with the id ${addon.id} has already been registered")

        addons[addon.id] = addon
    }
}

/**
 * Exception indicating an unrecoverable error during plugin registration.
 */
class AddonRegistrationException(message: String) : RuntimeException(message);
