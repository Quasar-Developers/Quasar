package net.quasarmc.quasar.api.registration

import net.quasarmc.quasar.api.registration.exceptions.KeyAlreadyRegisteredException
import org.bukkit.NamespacedKey

/**
 * Bare minimum custom registry implementation, for use by the root registry.
 * Prefer SimpleCustomRegistry for normal registries.
 */
open class CustomRegistry<TValue> : ICustomRegistry<TValue> {
    /**
     * Registered values
     */
    protected val data = HashMap<NamespacedKey, TValue>();

    override fun set(key: NamespacedKey, value: TValue) {
        if (data.contains(key))
            throw KeyAlreadyRegisteredException("Attempted to register already registered key \"${key}\"");

        data[key] = value;
    }

    override fun get(key: NamespacedKey): TValue {
        return data.getValue(key)
    }

    override fun getOrNull(key: NamespacedKey): TValue? {
        return data[key];
    }

    override fun removeAll() {
        data.clear()
    }

    override fun iterator(): Iterator<Pair<NamespacedKey, TValue>> {
        return CustomRegistryIteratorLow();
    }

    inner class CustomRegistryIteratorLow : Iterator<Pair<NamespacedKey, TValue>> {
        val iterator = data.iterator()

        override fun next(): Pair<NamespacedKey, TValue> = iterator.next().toPair()
        override fun hasNext(): Boolean = iterator.hasNext()
    }
}
