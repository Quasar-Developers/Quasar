package net.quasarmc.quasar.api.registration.events

import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * Event signaling that the root registry is now accepting data.
 * Addons that wish to add custom registries should handle this event.
 */
class RegistryRegistrationEvent : Event() {
    private companion object {
        val HANDLER_LIST = HandlerList()
    }

    override fun getHandlers(): HandlerList {
        return HANDLER_LIST
    }
}
