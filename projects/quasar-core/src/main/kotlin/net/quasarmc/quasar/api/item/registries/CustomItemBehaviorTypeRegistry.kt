package net.quasarmc.quasar.api.item.registries

import net.quasarmc.quasar.api.item.CustomItemBehaviorType
import net.quasarmc.quasar.api.keyOf
import net.quasarmc.quasar.api.registration.HardcodedCustomResourcePointer
import net.quasarmc.quasar.api.registration.SimpleCustomRegistry
import org.bukkit.NamespacedKey

object CustomItemBehaviorTypeRegistry : SimpleCustomRegistry<CustomItemBehaviorType<*>>(
    keyOf("quasar", "item_behavior_types"),
    "quasar.api"
) {
    /**
     * An component that can store arbitrary data. For testing the component API. Not intended for normal use.
     */
    val DEBUG_CUSTOM_DATA = registerHardcoded<Map<NamespacedKey, Any>>("debug_custom_data")

    private fun <TType : Any> registerHardcoded(key: String): HardcodedCustomResourcePointer<CustomItemBehaviorType<*>, CustomItemBehaviorType<TType>> {
        return super.registerHardcoded<CustomItemBehaviorType<TType>>(key) {
            CustomItemBehaviorType(keyOf(nativeNamespace, key))
        }
    }

    override fun keyIterator(): Iterator<NamespacedKey> {
        throw UnsupportedOperationException("Do not iterate over CustomItemBehaviorTypeRegistry")
    }

    override fun valueIterator(): Iterator<CustomItemBehaviorType<*>> {
        throw UnsupportedOperationException("Do not iterate over CustomItemBehaviorTypeRegistry")
    }
}
