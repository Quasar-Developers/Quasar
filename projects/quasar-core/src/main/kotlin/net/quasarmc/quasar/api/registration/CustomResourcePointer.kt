package net.quasarmc.quasar.api.registration

import net.quasarmc.quasar.api.registration.registries.CustomRegistryRegistry

/**
 * An object that provides a value from a registry.
 */
open class CustomResourcePointer<out TValue>(val key: CustomResourceKey<TValue>) {
    /**
     * Get the value, if it exists
     *
     * @throws NoSuchElementException There is no value with that key. Can be an invalid registry or ID.
     */
    open fun get(): TValue = CustomRegistryRegistry[key.registry][key.identifier] as TValue;
}
