package net.quasarmc.quasar.api.registration.events

import net.quasarmc.quasar.api.registration.ICustomRegistry
import net.quasarmc.quasar.api.registration.IReloadableCustomRegistry
import net.quasarmc.quasar.api.registration.registries.CustomRegistryRegistry
import org.bukkit.NamespacedKey
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * Event signaling that registries are now accepting data.
 *
 * Addons that want to add new registry objects should handle this.
 */
class RegistrationEvent(
    /**
     * If this event was fired as part of the Quasar API init and includes non-reloadable registries.
     */
    val init: Boolean = false
) : Event() {
    private companion object {
        val HANDLER_LIST = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLER_LIST
    }

    override fun getHandlers(): HandlerList = HANDLER_LIST

    /**
     * Safely register to a registry
     *
     * @param registry The registry to register to
     * @param lambda   Will be called with the requested registry
     */
    inline fun <reified TRegistry : ICustomRegistry<TValue>, TValue> register(
        registry: TRegistry,
        lambda: (registry: TRegistry) -> Unit
    ) {
        // Don't register if the registry isn't reloadable and we aren't in init
        if (!init && registry !is IReloadableCustomRegistry<*>)
            return

        lambda(registry)
    }

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
    ) = register(CustomRegistryRegistry[id] as TRegistry, lambda);

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
