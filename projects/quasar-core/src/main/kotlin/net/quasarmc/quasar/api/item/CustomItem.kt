package net.quasarmc.quasar.api.item

import io.papermc.paper.datacomponent.DataComponentType
import io.papermc.paper.persistence.PersistentDataKey
import net.quasarmc.quasar.api.keyOf
import net.quasarmc.quasar.api.registration.CustomResourcePointer
import net.quasarmc.quasar.api.util.component.CustomComponentMap
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

/**
 * A custom item singleton.
 */
open class CustomItem(
    /**
     * The identifier used to reference this item
     */
    val identifier: NamespacedKey,

    /**
     * The vanilla item that represents this item.
     */
    protected val prototype: ItemStack,

    /**
     * The behavior components on the item.
     */
    private val behavior: CustomComponentMap<CustomItemBehaviorType<Any>, Any>
) {
    /**
     * Get a behavior component
     *
     * @param type The component type to get
     * @return The component if present, null otherwise
     */
    fun <TType : Any> getBehaviorOrNull(type: CustomItemBehaviorType<TType>): TType?
        = behavior.getOrNull(type)

    /**
     * Get a behavior component
     *
     * @param type The component type to get
     * @return The component if present, null otherwise
     */
    fun <TType : Any> getBehaviorOrNull(type: CustomResourcePointer<CustomItemBehaviorType<TType>>): TType?
        = getBehaviorOrNull(type.get())

    /**
     * Get a behavior component
     *
     * @param type The component type to get
     * @throws NoSuchElementException The component is not present on the item
     */
    fun <TType : Any> getBehavior(type: CustomItemBehaviorType<TType>): TType
        = getBehaviorOrNull(type)
       ?: throw NoSuchElementException("The behavior component \"$type\" is not present on the item.")

    /**
     * Get a behavior component
     *
     * @param type The component type to get
     * @throws NoSuchElementException The component is not present on the item
     */
    fun <TType : Any> getBehavior(type: CustomResourcePointer<CustomItemBehaviorType<TType>>): TType
        = getBehavior(type.get())

    /**
     * Create an ItemStack representing this item
     */
    open fun create(): ItemStack = prototype.clone()

    /**
     * Called when this item receives a PlayerInteractionEvent
     *
     * Item is guaranteed to be non-null
     */
    open fun onInteract(ev: PlayerInteractEvent) {}

    /**
     * Called when this item receives a PlayerInteractEntityEvent
     */
    open fun onInteractEntity(ev: PlayerInteractEntityEvent) {}

    companion object {
        /**
         * The key used to store the item identifier in an item's PDC
         */
        val PDC_IDENTIFIER_KEY = PersistentDataKey.of(keyOf("quasar.api", "identifier"), PersistentDataType.STRING)!!
    }

    /**
     * Builder for a custom item.
     */
    @Suppress("UNCHECKED_CAST")
    abstract class CustomItemBuilder<TSelf : CustomItemBuilder<TSelf, TResult>, TResult : CustomItem>(
        protected val identifier: NamespacedKey,
        protected val material: Material
    ) {
        protected val addedComponents = HashMap<DataComponentType, Any>()
        protected val removedComponents = HashSet<DataComponentType>()
        protected val behavior = CustomComponentMap.Builder<CustomItemBehaviorType<Any>, Any>()

        /**
         * Set a component on the item
         *
         * @param type The type of component to set
         */
        fun setComponent(type: DataComponentType.NonValued): TSelf {
            addedComponents[type] = Unit
            removedComponents.remove(type)
            return this as TSelf
        }

        /**
         * Set a component on the item
         *
         * @param type The type of component to set
         * @param data The component data
         */
        fun <TType : Any> setComponent(type: DataComponentType.Valued<TType>, data: TType): TSelf {
            addedComponents[type] = data
            removedComponents.remove(type)
            return this as TSelf
        }

        /**
         * Remove a component from the item
         *
         * @param type The type of component to remove
         */
        fun removeComponent(type: DataComponentType): TSelf {
            addedComponents.remove(type)
            removedComponents.add(type)
            return this as TSelf
        }

        /**
         * Add a behavior component to the item
         *
         * @param type  The type of component to add
         * @param value The value of the component
         */
        fun <TType : Any> setBehavior(type: CustomItemBehaviorType<TType>, value: TType): TSelf
            = this.apply { this.behavior.set(type, value) } as TSelf

        /**
         * Add a behavior component to the item
         *
         * @param type  The type of component to add
         * @param value The value of the component
         */
        fun <TType : Any> setBehavior(type: CustomResourcePointer<CustomItemBehaviorType<TType>>, value: TType): TSelf
            = setBehavior(type.get(), value)

        /**
         * Add a valueless behavior component to the item
         *
         * @param type The type of component to add
         */
        fun setBehavior(type: CustomItemBehaviorType<Unit>): TSelf
            = setBehavior(type, Unit)

        /**
         * Add a valueless behavior component to the item
         *
         * @param type The type of component to add
         */
        fun setBehavior(type: CustomResourcePointer<CustomItemBehaviorType<Unit>>): TSelf
            = setBehavior(type.get())

        /**
         * Remove a behavior component from the item
         *
         * @param type The type of component to remove
         */
        fun <TType : Any> removeBehavior(type: CustomItemBehaviorType<TType>): TSelf
            = this.apply { this.behavior.remove(type) } as TSelf

        /**
         * Remove a behavior component from the item
         *
         * @param type The type of component to remove
         */
        fun <TType : Any> removeBehavior(type: CustomResourcePointer<CustomItemBehaviorType<TType>>): TSelf
            = removeBehavior(type.get())

        /**
         * Return an instance of this item class.
         *
         * Prefer [augment] when applying properties to the item.
         */
        abstract fun build(): TResult

        /**
         * Apply the collected properties to the custom item.
         *
         * Call this in inheriting [build] methods
         */
        open fun augment(item: TResult): TResult {
            for (comp in removedComponents)
                item.prototype.unsetData(comp)

            for ((comp, data) in addedComponents) {
                when (comp) {
                    is DataComponentType.NonValued -> item.prototype.setData(comp)
                    // TYPE SAFETY: Type checked by compiler in addComponent
                    is DataComponentType.Valued<*> -> item.prototype.setData(comp as DataComponentType.Valued<Any>, data)
                }
            }

            item.prototype.editPersistentDataContainer {
                it[PDC_IDENTIFIER_KEY] = identifier.toString()
            }

            return item
        }
    }

    /**
     * Builder for base CustomItems.
     */
    class Builder(identifier: NamespacedKey, material: Material) : CustomItemBuilder<Builder, CustomItem>(identifier, material) {
        override fun build(): CustomItem {
            val item = CustomItem(identifier, ItemStack.of(this.material), this.behavior.build())

            augment(item)

            return item
        }
    }
}

