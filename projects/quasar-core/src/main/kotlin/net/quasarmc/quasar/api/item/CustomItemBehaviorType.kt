package net.quasarmc.quasar.api.item

import net.quasarmc.quasar.api.util.component.ICustomComponentType
import org.bukkit.NamespacedKey

/**
 * Information about a custom item behavior component, which describes innate information about
 * a type of custom item. Behavior components are immutable and cannot change after an item is
 * registered.
 */
class CustomItemBehaviorType<TValue>(
    val identifier: NamespacedKey,
) : ICustomComponentType<CustomItemBehaviorType<Any>, TValue> {
    override fun toString() = "CustomItemBehaviorType[$identifier]"
}
