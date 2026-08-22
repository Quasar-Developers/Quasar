package net.quasarmc.quasar.api.item.registries

import io.papermc.paper.datacomponent.DataComponentType
import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.ItemLore
import net.kyori.adventure.text.minimessage.MiniMessage
import net.quasarmc.quasar.api.createFormatted
import net.quasarmc.quasar.api.item.CustomItem
import net.quasarmc.quasar.api.keyOf
import net.quasarmc.quasar.api.registration.SimpleCustomRegistry
import org.bukkit.Material
import org.bukkit.inventory.ItemRarity

object CustomItemRegistry : SimpleCustomRegistry<CustomItem>(keyOf("quasar", "items"), "quasar.core") {
    val DEBUG_ITEM = registerHardcoded("debug_item") {
        CustomItem.Builder(Material.POISONOUS_POTATO)
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
