package net.quasarmc.quasar.api.registration

import org.bukkit.NamespacedKey

/**
 * Base interface for all custom registries.
 */
interface ICustomRegistry<TValue> : Iterable<Pair<NamespacedKey, TValue>> {
    /**
     * Register a new value.
     *
     * @param key   The key to register the value as
     * @param value The value to register
     *
     * @throws net.quasarmc.quasar.api.registration.exceptions.KeyAlreadyRegisteredException
     *         A value with the specified key has already been registered.
     */
    operator fun set(key: NamespacedKey, value: TValue);

    /**
     * Register a new value.
     *
     * @param namespace The namespace to register the value under
     * @param key       The key to register the value as
     * @param value     The value to register
     *
     * @throws net.quasarmc.quasar.api.registration.exceptions.KeyAlreadyRegisteredException
     *         A value with the specified key has already been registered.
     */
    operator fun set(namespace: String, key: String, value: TValue) = set(NamespacedKey(namespace, key), value);

    /**
     * Get a value from the registry.
     *
     * @param key The key to fetch.
     *
     * @throws NoSuchElementException There is no registered value with the requested key
     */
    operator fun get(key: NamespacedKey): TValue;

    /**
     * Get a value from the registry.
     *
     * @param namespace The namespace to fetch from.
     * @param key       The key to fetch.
     *
     * @throws NoSuchElementException There is no registered value with the requested key
     */
    operator fun get(namespace: String, key: String): TValue = get(NamespacedKey(namespace, key));

    /**
     * Get a value from the registry or return null if not found
     *
     * @param key The key to fetch.
     */
    fun getOrNull(key: NamespacedKey): TValue?;

    /**
     * Get a value from the registry or return null if not found
     *
     * @param namespace The namespace to fetch from.
     * @param key       The key to fetch.
     */
    fun getOrNull(namespace: String, key: String): TValue? = getOrNull(NamespacedKey(namespace, key));

    /**
     * Remove all registered values from the registry.
     *
     * Do not call this outside of [RegistrationManager].
     */
    fun removeAll();
}
