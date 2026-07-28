package net.quasarmc.quasar.api.registration

import net.quasarmc.quasar.api.plugin.QuasarPlugin
import net.quasarmc.quasar.api.registration.events.BeginReloadEvent
import net.quasarmc.quasar.api.registration.events.EndReloadEvent
import net.quasarmc.quasar.api.registration.events.RegistrationEvent
import net.quasarmc.quasar.api.registration.events.RegistryRegistrationEvent
import net.quasarmc.quasar.api.registration.registries.CustomRegistryRegistry
import org.bukkit.NamespacedKey

/**
 * Singleton for managing the Quasar API's registration system lifecycle
 *
 * Registration API notes - https://hackmd.io/PXvzu9osSPmXCv8l3WI0dQ
 */
object RegistrationManager {
    /**
     * Clear all registries and reload data.
     */
    fun reload() {
        QuasarPlugin.LOGGER.info("Reloading registries...")

        BeginReloadEvent().callEvent()

        // purge registries
        for ((key, registry) in CustomRegistryRegistry) {
            if (registry != CustomRegistryRegistry) registry.removeAll()
        }
        CustomRegistryRegistry.removeAll()

        // load new registries
        RegistryRegistrationEvent().callEvent()

        // load new registry data
        RegistrationEvent().callEvent()

        // registration done
        EndReloadEvent().callEvent()

        QuasarPlugin.LOGGER.info("Registry reload complete.")
    }
}
