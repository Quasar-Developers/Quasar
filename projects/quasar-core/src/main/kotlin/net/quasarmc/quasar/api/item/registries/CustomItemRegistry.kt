package net.quasarmc.quasar.api.item.registries

import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.ItemAttributeModifiers
import io.papermc.paper.datacomponent.item.ItemLore
import io.papermc.paper.datacomponent.item.Weapon
import io.papermc.paper.registry.keys.SoundEventKeys
import net.quasarmc.quasar.api.createFormatted
import net.quasarmc.quasar.api.item.CustomItem
import net.quasarmc.quasar.api.item.CustomItemBehaviorType
import net.quasarmc.quasar.api.keyOf
import net.quasarmc.quasar.api.registration.SimpleCustomRegistry
import net.quasarmc.quasar.api.registration.registries.CustomRegistryRegistry
import net.quasarmc.quasar.core.administration.commands.items.AdminEraseEntityItem
import net.quasarmc.quasar.core.registration.CoreCustomItems
import org.bukkit.Material
import org.bukkit.MusicInstrument
import org.bukkit.NamespacedKey
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.inventory.ItemRarity

object CustomItemRegistry : SimpleCustomRegistry<CustomItem>(keyOf("quasar", "items"), "quasar.api") {
    val DEBUG_ITEM = registerHardcoded("debug_item") {
        CustomItem.Builder(keyOf("quasar.api", "debug_item"), Material.POISONOUS_POTATO)
            .setComponent(DataComponentTypes.ITEM_MODEL, keyOf("quasar", "debug"))
            .setComponent(DataComponentTypes.ITEM_NAME, createFormatted("debug item"))
            .setComponent(DataComponentTypes.LORE, ItemLore.lore(listOf(createFormatted("<red>You shouldn't have this!!!</red>"))))
            .setComponent(DataComponentTypes.RARITY, ItemRarity.EPIC)
            .setComponent(DataComponentTypes.MAX_STACK_SIZE, 1)
            .removeComponent(DataComponentTypes.FOOD)
            .removeComponent(DataComponentTypes.CONSUMABLE)
            .build()
    }
}
