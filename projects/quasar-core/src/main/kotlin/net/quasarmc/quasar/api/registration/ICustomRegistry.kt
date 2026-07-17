package net.quasarmc.quasar.api.registration

import org.bukkit.NamespacedKey

/**
 * Custom registry managed by the RegistrationManager
 */
interface ICustomRegistry<TValue> : Iterable<Pair<NamespacedKey, TValue>> {
    /**
     * Register a new value.
     *
     * @throws net.quasarmc.quasar.api.registration.exceptions.KeyAlreadyRegisteredException
     *         A value with the specified key has already been registered.
     */
    operator fun set(key: NamespacedKey, value: TValue);
    operator fun set(namespace: String, key: String, value: TValue) = set(NamespacedKey(namespace, key), value);

    /**
     * Get a value from the registry.
     *
     * @throws NoSuchElementException There is no registered value with the requested key
     */
    operator fun get(key: NamespacedKey): TValue;
    operator fun get(namespace: String, key: String): TValue = get(NamespacedKey(namespace, key));

    /**
     * Get a value from the registry or return null if not found
     */
    fun getOrNull(key: NamespacedKey): TValue?;
    fun getOrNull(namespace: String, key: String): TValue? = getOrNull(NamespacedKey(namespace, key));

    /**
     * Remove all registered values from the registry.
     */
    fun removeAll();
}
