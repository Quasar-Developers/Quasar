package net.quasarmc.quasar.api.registration.events

import net.quasarmc.quasar.api.registration.AbstractCustomRegistry
import net.quasarmc.quasar.api.registration.registries.CustomRegistryRegistry
import org.bukkit.NamespacedKey
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * Event signaling that the root registry is now accepting data.
 * Addons that wish to add custom registries should handle this event.
 */
class RegistryRegistrationEvent : Event() {
    private companion object {
        val HANDLER_LIST = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLER_LIST
    }

    override fun getHandlers(): HandlerList = HANDLER_LIST

    fun register(key: NamespacedKey, value: AbstractCustomRegistry<*>) {
        CustomRegistryRegistry[key] = value;
    }

    fun register(namespace: String, key: String, value: AbstractCustomRegistry<*>)
        = register(NamespacedKey(namespace, key), value);
}
