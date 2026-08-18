package net.quasarmc.quasar.api.registration.events

import net.quasarmc.quasar.api.registration.ICustomRegistry
import net.quasarmc.quasar.api.registration.SimpleCustomRegistry
import net.quasarmc.quasar.api.registration.registries.CustomRegistryRegistry
import org.bukkit.NamespacedKey
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * Event signaling that the root registry is now accepting data.
 *
 * Addons that wish to add custom registries should handle this event.
 */
class RegistryRegistrationEvent(
    /**
     * If this event was fired as part of the Quasar API init and includes non-reloadable registries.
     */
    val init: Boolean = false
): Event() {
    private companion object {
        val HANDLER_LIST = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLER_LIST
    }

    override fun getHandlers(): HandlerList = HANDLER_LIST

    /**
     * Register a new registry.
     *
     * @param identifier The identifier of the new object
     * @param value      The object to register
     *
     * @throws net.quasarmc.quasar.api.registration.exceptions.KeyAlreadyRegisteredException
     *         A value with the specified identifier has already been registered
     */
    fun register(identifier: NamespacedKey, value: ICustomRegistry<*>) {
        CustomRegistryRegistry[identifier] = value;
    }

    /**
     * Register a new registry.
     *
     * @param namespace  The namespace to register the new object under
     * @param key        The key to register the new object as
     * @param value      The object to register
     *
     * @throws net.quasarmc.quasar.api.registration.exceptions.KeyAlreadyRegisteredException
     *         A value with the specified identifier has already been registered
     */
    fun register(namespace: String, key: String, value: ICustomRegistry<*>)
        = register(NamespacedKey(namespace, key), value);

    /**
     * Register a new simple registry.
     *
     * @param registry The simple registry to register. It will be registered under the identifier specified
     *                 when creating the registry.
     *
     * @throws net.quasarmc.quasar.api.registration.exceptions.KeyAlreadyRegisteredException
     *         A value with the specified identifier has already been registered
     */
    fun register(registry: SimpleCustomRegistry<*>)
        = register(registry.identifier, registry);
}
