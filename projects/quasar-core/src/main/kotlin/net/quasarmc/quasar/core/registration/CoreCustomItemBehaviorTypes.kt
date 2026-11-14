package net.quasarmc.quasar.core.registration

import net.quasarmc.quasar.api.item.CustomItemBehaviorType
import net.quasarmc.quasar.api.item.registries.CustomItemBehaviorTypeRegistry
import net.quasarmc.quasar.api.registration.HardcodedCustomRegistryLoader

/**
 * All behavior types for the quasar core content
 *
 * @see CustomItemBehaviorTypeRegistry - quasar api content / debug content
 */
object CoreCustomItemBehaviorTypes {
    val loader = HardcodedCustomRegistryLoader<CustomItemBehaviorType<*>>(CustomItemBehaviorTypeRegistry.identifier)
}
