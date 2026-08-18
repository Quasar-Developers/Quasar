package net.quasarmc.quasar.api.registration

import net.quasarmc.quasar.core.QuasarPlugin
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
     *
     * @param init If the reload is a API initialization reload. If true, non-reloadable registries will
     *             also be reset. You shouldn't ever have to use this.
     */
    fun reload(init: Boolean = false) {
        QuasarPlugin.LOGGER.info("Reloading registries...")

        BeginReloadEvent(init).callEvent()

        // purge registries
        for ((key, registry) in CustomRegistryRegistry) {
            if (registry == CustomRegistryRegistry ||
                !init && registry !is IReloadableCustomRegistry)
                continue

            registry.removeAll()
        }
        CustomRegistryRegistry.removeAll()

        // load new registries
        RegistryRegistrationEvent(init).callEvent()

        // load new registry data
        RegistrationEvent(init).callEvent()

        // registration done
        EndReloadEvent(init).callEvent()

        QuasarPlugin.LOGGER.info("Registry reload complete.")
    }
}
