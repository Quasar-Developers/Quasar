package net.quasarmc.quasar.api.registration.registries

import net.quasarmc.quasar.api.registration.CustomRegistry
import net.quasarmc.quasar.api.registration.ICustomRegistry

object CustomRegistryRegistry : CustomRegistry<ICustomRegistry<*>>() {}
