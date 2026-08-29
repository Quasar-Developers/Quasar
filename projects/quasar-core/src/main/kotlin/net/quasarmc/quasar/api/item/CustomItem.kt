package net.quasarmc.quasar.api.item

import io.papermc.paper.datacomponent.DataComponentType
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

/**
 * A custom item singleton.
 */
open class CustomItem(baseMaterial: Material) {
    /**
     * The vanilla item that represents this item.
     */
    val prototype = ItemStack.of(baseMaterial)

    /**
     * Builder for a custom item.
     */
    abstract class CustomItemBuilder<TSelf : CustomItem> {
        /**
         * The item instance being build
         */
        abstract val item: TSelf;

        /**
         * Set a component on the item
         *
         * @param type The type of component to set
         */
        fun setComponent(type: DataComponentType.NonValued): CustomItemBuilder<TSelf> {
            item.prototype.setData(type)
            return this
        }

        /**
         * Set a component on the item
         *
         * @param type The type of component to set
         * @param data The component data
         */
        fun <TType : Any> setComponent(type: DataComponentType.Valued<TType>, data: TType): CustomItemBuilder<TSelf> {
            item.prototype.setData(type, data)
            return this
        }

        /**
         * Remove a component from the item
         *
         * @param type The type of component to remove
         */
        fun removeComponent(type: DataComponentType): CustomItemBuilder<TSelf> {
            item.prototype.unsetData(type)
            return this
        }

        /**
         * Return an instance of this item class.
         */
        fun build(): TSelf {
            return item
        }
    }

    /**
     * Builder for base CustomItems.
     */
    class Builder(baseMaterial: Material) : CustomItemBuilder<CustomItem>() {
        override val item = CustomItem(baseMaterial)
    }
}

