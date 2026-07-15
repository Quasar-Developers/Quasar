package net.quasarmc.quasar.api.registration.registries

import net.quasarmc.quasar.api.registration.AbstractCustomRegistry
import net.quasarmc.quasar.api.registration.CustomRegistryLow

object CustomRegistryRegistry : CustomRegistryLow<AbstractCustomRegistry<*>>() {
    override fun handlePreRegistration() {

    }
}
