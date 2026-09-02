package net.quasarmc.quasar.api.item.registries

import net.quasarmc.quasar.api.item.CustomItemBehaviorType
import net.quasarmc.quasar.api.keyOf
import net.quasarmc.quasar.api.registration.HardcodedCustomResourcePointer
import net.quasarmc.quasar.api.registration.SimpleCustomRegistry
import org.bukkit.NamespacedKey

object CustomItemBehaviorTypeRegistry : SimpleCustomRegistry<CustomItemBehaviorType<Any>>(
    keyOf("quasar", "item_behavior_types"),
    "quasar.core"
) {
    /**
     * An component that can store arbitrary data. For testing the component API. Not intended for normal use.
     */
    val DEBUG_CUSTOM_DATA = registerHardcoded<Map<NamespacedKey, Any>>("debug_custom_data")

    /**
     * An component that can store an arbitrary integer. For testing the component API. Not intended for normal use.
     */
    val DEBUG_INTEGER = registerHardcoded<Int>("debug_integer")

    /**
     * An component that can store an arbitrary number. For testing the component API. Not intended for normal use.
     */
    val DEBUG_NUMBER = registerHardcoded<Number>("debug_number")

    private fun <TType : Any> registerHardcoded(key: String): HardcodedCustomResourcePointer<CustomItemBehaviorType<Any>, CustomItemBehaviorType<TType>> {
        return super.registerHardcoded<CustomItemBehaviorType<TType>>(key) {
            CustomItemBehaviorType(keyOf(nativeNamespace, key))
        }
    }
}
