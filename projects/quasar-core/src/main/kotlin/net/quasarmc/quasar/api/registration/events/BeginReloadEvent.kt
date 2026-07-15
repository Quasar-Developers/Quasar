package net.quasarmc.quasar.api.registration.events

import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * Event signaling that the registration manager is about to reload registry data.
 */
class BeginReloadEvent : Event() {
    private companion object {
        val HANDLER_LIST = HandlerList()
    }

    override fun getHandlers(): HandlerList {
        return HANDLER_LIST
    }
}
