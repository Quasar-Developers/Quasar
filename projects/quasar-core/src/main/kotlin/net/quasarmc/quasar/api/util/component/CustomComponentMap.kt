package net.quasarmc.quasar.api.util.component

import net.quasarmc.quasar.api.item.CustomItemBehaviorType
import net.quasarmc.quasar.api.keyOf

/**
 * An immutable map of component types to component data.
 *
 * @param TComponentType The component type used as a key within the map
 * @param TBaseValue     The base type for all component values
 * @param data           The underlying map object.
 */
data class CustomComponentMap<TComponentType : ICustomComponentType<TComponentType, TBaseValue>, TBaseValue>(
    private val data: Map<ICustomComponentType<TComponentType, TBaseValue>, TBaseValue>
) {
    fun <TType, TValue> getOrNull(type: TType): TValue?
        where TType  : ICustomComponentType<TComponentType, TValue>,
              TValue : TBaseValue
        = data[type] as TValue?

    operator fun <TType, TValue> get(type: TType): TValue
        where TType  : ICustomComponentType<TComponentType, TValue>,
              TValue : TBaseValue
        = getOrNull(type) ?: throw NoSuchElementException("Kill yourself.")

    class Builder<TComponentType : ICustomComponentType<TComponentType, TBaseValue>, TBaseValue>(
        private val components: HashMap<ICustomComponentType<TComponentType, TBaseValue>, TBaseValue> = HashMap()
    ) {
        fun <TType, TValue> set(type: TType, value: TValue): Builder<TComponentType, TBaseValue>
            where TType  : ICustomComponentType<TComponentType, TValue>,
                  TValue : TBaseValue
            = this.apply { this.components[type] = value }

        fun <TType, TValue> remove(type: TType): Builder<TComponentType, TBaseValue>
            where TType  : ICustomComponentType<TComponentType, TValue>,
                  TValue : TBaseValue
            = this.apply { this.components.remove(type) }

        fun build(): CustomComponentMap<TComponentType, TBaseValue> {
            return CustomComponentMap(components);
        }
    }
}
