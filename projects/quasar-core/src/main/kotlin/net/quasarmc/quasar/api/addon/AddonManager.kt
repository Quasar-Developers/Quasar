package net.quasarmc.quasar.api.addon

/**
 * Central class to manage all Quasar API addons and their life-cycles.
 */
object AddonManager {
    val addons = HashMap<String, Addon>();

    /**
     * Unregisters an existing addon.
     *
     * @param id ID of the addon to unregister.
    **/
    fun unregister(id: String) {
        if (!addons.contains(id))
            throw AddonRegistrationException("There's no addon $id to unregister.")
        addons.remove(id)
    }

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
}

/**
 * Exception indicating an unrecoverable error during plugin registration.
 */
class AddonRegistrationException(message: String) : RuntimeException(message);
