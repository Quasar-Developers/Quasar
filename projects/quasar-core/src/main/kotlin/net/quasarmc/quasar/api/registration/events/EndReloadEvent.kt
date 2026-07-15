package net.quasarmc.quasar.api.registration.events

import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * Event signaling that the registry reload is complete and registries are
 * now safe to access.
 */
class EndReloadEvent : Event() {
    private companion object {
        val HANDLER_LIST = HandlerList()
    }

    override fun getHandlers(): HandlerList {
        return HANDLER_LIST
    }
}
