package net.quasarmc.quasar.api.item

import io.papermc.paper.datacomponent.DataComponentType
import net.quasarmc.quasar.api.util.component.CustomComponentMap
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

/**
 * A custom item singleton.
 */
open class CustomItem(
    baseMaterial: Material,

    /**
     * The behavior components on the item.
     */
    private val behavior: CustomComponentMap<CustomItemBehaviorType<Any>, Any>
) {
    /**
     * The vanilla item that represents this item.
     */
    val prototype = ItemStack.of(baseMaterial)

    /**
     * Builder for a custom item.
     */
    abstract class CustomItemBuilder<TSelf : CustomItem>(protected val material: Material) {
        protected val addedComponents   = HashMap<DataComponentType, Any>()
        protected val removedComponents = HashSet<DataComponentType>()
        protected val behavior = CustomComponentMap.Builder<CustomItemBehaviorType<Any>, Any>()

        /**
         * Set a component on the item
         *
         * @param type The type of component to set
         */
        fun setComponent(type: DataComponentType.NonValued): CustomItemBuilder<TSelf> {
            addedComponents[type] = Unit
            removedComponents.remove(type)
            return this
        }

        /**
         * Set a component on the item
         *
         * @param type The type of component to set
         * @param data The component data
         */
        fun <TType : Any> setComponent(type: DataComponentType.Valued<TType>, data: TType): CustomItemBuilder<TSelf> {
            addedComponents[type] = data
            removedComponents.remove(type)
            return this
        }

        /**
         * Remove a component from the item
         *
         * @param type The type of component to remove
         */
        fun removeComponent(type: DataComponentType): CustomItemBuilder<TSelf> {
            addedComponents.remove(type)
            removedComponents.add(type)
            return this
        }

        /**
         * Add a behavior component to the item
         *
         * @param type  The type of component to add
         * @param value The value of the component
         */
        fun <TType : Any> setBehavior(type: CustomItemBehaviorType<TType>, value: TType): CustomItemBuilder<TSelf>
            = this.apply { this.behavior.set(type, value) }

        /**
         * Add a valueless behavior component to the item
         *
         * @param type The type of component to add
         */
        fun setBehavior(type: CustomItemBehaviorType<Unit>): CustomItemBuilder<TSelf>
            = setBehavior(type, Unit)

        /**
         * Remove a behavior component from the item
         *
         * @param type The type of component to remove
         */
        fun <TType : Any> removeBehavior(type: CustomItemBehaviorType<TType>): CustomItemBuilder<TSelf>
            = this.apply { this.behavior.remove(type) }

        /**
         * Return an instance of this item class.
         */
        abstract fun build(): TSelf

        /**
         * Apply the prototype patches specified by [addedComponents] and [removedComponents].
         *
         * Call this in [build]
         */
        protected fun buildPrototype(prototype: ItemStack) {
            for (comp in removedComponents)
                prototype.unsetData(comp)

            for ((comp, data) in addedComponents) {
                when (comp) {
                    is DataComponentType.NonValued -> prototype.setData(comp)
                    // TYPE SAFETY: Type checked by compiler in addComponent
                    is DataComponentType.Valued<*> -> prototype.setData(comp as DataComponentType.Valued<Any>, data)
                }
            }
        }
    }

    /**
     * Builder for base CustomItems.
     */
    class Builder(baseMaterial: Material) : CustomItemBuilder<CustomItem>(baseMaterial) {
        override fun build(): CustomItem {
            val item = CustomItem(material, this.behavior.build())

            buildPrototype(item.prototype)

            return item
        }
    }
}

