package net.quasarmc.quasar.api.registration

import net.quasarmc.quasar.api.registration.exceptions.KeyAlreadyRegisteredException
import org.bukkit.NamespacedKey

/**
 * Bare minimum custom registry implementation, for use by the root registry.
 * Prefer CustomRegistry for code simplicity.
 */
open class CustomRegistryLow<TValue> : AbstractCustomRegistry<TValue>() {
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

        override fun next(): Pair<NamespacedKey, TValue> = Pair(iterator.next().key, iterator.next().value)
        override fun hasNext(): Boolean = iterator.hasNext()
    }
}
