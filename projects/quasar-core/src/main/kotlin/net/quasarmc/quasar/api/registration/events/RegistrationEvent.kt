package net.quasarmc.quasar.api.registration.events

import net.quasarmc.quasar.api.registration.ICustomRegistry
import net.quasarmc.quasar.api.registration.registries.CustomRegistryRegistry
import org.bukkit.NamespacedKey
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * Event signaling that registries are now accepting data.
 *
 * Addons that want to add new registry objects should handle this.
 */
class RegistrationEvent : Event() {
    private companion object {
        val HANDLER_LIST = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLER_LIST
    }

    override fun getHandlers(): HandlerList = HANDLER_LIST

    /**
     * Get a writable registry for registration
     *
     * @param id     The identifier of the registry
     * @param lambda Will be called with the requested registry
     *
     * @throws NoSuchElementException The requested registry does not exist
     */
    inline fun <reified TRegistry : ICustomRegistry<TValue>, TValue> register(
        id: NamespacedKey,
        lambda: (registry: TRegistry) -> Unit
    ) = lambda(CustomRegistryRegistry[id] as TRegistry);

    /**
     * Get a writable registry for registration
     *
     * @param namespace The namespace of the registry
     * @param id        The id of the registry
     * @param lambda    Will be called with the requested registry
     *
     * @throws NoSuchElementException The requested registry does not exist
     */
    inline fun <reified TRegistry : ICustomRegistry<TValue>, TValue> register(
        namespace: String,
        id: String,
        lambda: (registry: TRegistry) -> Unit
    ) = register(NamespacedKey(namespace, id), lambda);

    /**
     * Get a writable registry for registration without knowledge of the registry type
     *
     * @param id     The identifier of the registry
     * @param lambda Will be called with the requested registry
     *
     * @throws NoSuchElementException The requested registry does not exist
     */
    inline fun <TValue> registerBase(
        id: NamespacedKey,
        lambda: (registry: ICustomRegistry<TValue>) -> Unit
    ) = register<ICustomRegistry<TValue>, TValue>(id, lambda)

    /**
     * Get a writable registry for registration without knowledge of the registry type.
     *
     * @param namespace The namespace of the registry
     * @param id        The id of the registry
     * @param lambda    Will be called with the requested registry
     *
     * @throws NoSuchElementException The requested registry does not exist
     */
    inline fun <TValue> registerBase(
        namespace: String,
        id: String,
        lambda: (registry: ICustomRegistry<TValue>) -> Unit
    ) = registerBase(NamespacedKey(namespace, id), lambda);
}
