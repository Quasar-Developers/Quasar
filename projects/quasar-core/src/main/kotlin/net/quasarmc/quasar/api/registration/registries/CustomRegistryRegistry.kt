package net.quasarmc.quasar.api.registration.registries

import net.quasarmc.quasar.api.registration.CustomRegistry
import net.quasarmc.quasar.api.registration.CustomResourceKey
import net.quasarmc.quasar.api.registration.ICustomRegistry

/**
 * The root registry that stores all other custom registries.
 *
 * Do not register directly, handle the RegistryRegistrationEvent instead.
 */
object CustomRegistryRegistry : CustomRegistry<ICustomRegistry<*>>() {
    /**
     * Get the object specified by the given resource key
     *
     * @param key The resource key of the requested object
     *
     * @throws NoSuchElementException
     *         The requested object does not exist
     *         (either because the registry could not be found or because the object was not registered)
     */
    inline operator fun <reified TValue> get(key: CustomResourceKey<TValue>): TValue {
        return this[key.registry][key.identifier] as TValue
    }
}
