package net.quasarmc.quasar.api.registration.events

import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * Event signaling that registries are now accepting data.
 * Addons that want to add new registry objects should handle this.
 */
class RegistrationEvent : Event() {
    private companion object {
        val HANDLER_LIST = HandlerList()
    }

    override fun getHandlers(): HandlerList {
        return HANDLER_LIST
    }
}
