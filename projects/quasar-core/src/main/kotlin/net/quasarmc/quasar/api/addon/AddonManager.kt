package net.quasarmc.quasar.api.addon

import net.quasarmc.quasar.api.addon.exceptions.AddonRegistrationException

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
