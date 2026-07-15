package net.quasarmc.quasar.api.registration

import net.quasarmc.quasar.api.registration.events.BeginReloadEvent
import net.quasarmc.quasar.api.registration.events.EndReloadEvent
import net.quasarmc.quasar.api.registration.events.RegistrationEvent
import net.quasarmc.quasar.api.registration.events.RegistryRegistrationEvent

/**
 * Singleton for managing the Quasar API's registration system lifecycle
 *
 * Registration API notes - https://hackmd.io/PXvzu9osSPmXCv8l3WI0dQ
 */
object RegistrationManager {
    /**
     * Clears registries and initiates registration
     */
    fun reload() {
        // i've considered making this cancellable, i may do that in the future.
        BeginReloadEvent().callEvent()

        // todo: purge registries

        // load new registries
        RegistryRegistrationEvent().callEvent()

        // load new registry data
        RegistrationEvent().callEvent()

        // registration done
        EndReloadEvent().callEvent()
    }
}
