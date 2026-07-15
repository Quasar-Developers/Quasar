package net.quasarmc.quasar.api.registration

import org.bukkit.NamespacedKey

/**
 * Base class for all custom registries providing a standard way to read and
 * write from registry classes.
 */
abstract class AbstractCustomRegistry<TValue> : Iterable<Pair<NamespacedKey, TValue>> {
    /**
     * Register a new value.
     *
     * @throws net.quasarmc.quasar.api.registration.exceptions.KeyAlreadyRegisteredException
     *         A value with the specified key has already been registered.
     */
    abstract operator fun set(key: NamespacedKey, value: TValue);
    open operator fun set(namespace: String, key: String, value: TValue) = set(NamespacedKey(namespace, key), value);

    /**
     * Get a value from the registry.
     *
     * @throws NoSuchElementException There is no registered value with the requested key
     */
    abstract operator fun get(key: NamespacedKey): TValue;
    open operator fun get(namespace: String, key: String): TValue = get(NamespacedKey(namespace, key));

    /**
     * Get a value from the registry or return null if not found
     */
    abstract fun getOrNull(key: NamespacedKey): TValue?;
    open fun getOrNull(namespace: String, key: String): TValue? = getOrNull(NamespacedKey(namespace, key));

    /**
     * Remove all registered values from the registry.
     */
    abstract fun removeAll();

    //#region Lifecycle Methods
    //        Registry lifecycle methods, for use by the RegistrationManager

    /**
     * Called by RegistryManager after the registry has been purged but before
     * new values have been loaded into the registry.
     *
     * Use this to load hardcoded registry data.
     */
    protected open fun handlePreRegistration() {};
    internal fun runPreRegistration() = handlePreRegistration();

    //#endregion
}
