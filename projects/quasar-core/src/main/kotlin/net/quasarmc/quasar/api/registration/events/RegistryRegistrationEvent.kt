package net.quasarmc.quasar.api.registration.events

import net.quasarmc.quasar.api.registration.ICustomRegistry
import net.quasarmc.quasar.api.registration.SimpleCustomRegistry
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

    fun register(key: NamespacedKey, value: ICustomRegistry<*>) {
        CustomRegistryRegistry[key] = value;
    }

    fun register(namespace: String, key: String, value: ICustomRegistry<*>)
        = register(NamespacedKey(namespace, key), value);

    fun register(registry: SimpleCustomRegistry<*>)
        = register(registry.identifier, registry);
}
