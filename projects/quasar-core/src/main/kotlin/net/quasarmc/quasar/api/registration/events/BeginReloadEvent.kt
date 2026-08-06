package net.quasarmc.quasar.api.registration.events

import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * Event signaling that the registration manager is about to reload registry data.
 */
class BeginReloadEvent(
    /**
     * If this event was fired as part of the Quasar API init and includes non-reloadable registries.
     */
    val init: Boolean = false
) : Event() {
    private companion object {
        val HANDLER_LIST = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLER_LIST;
    }

    override fun getHandlers(): HandlerList = HANDLER_LIST
}
