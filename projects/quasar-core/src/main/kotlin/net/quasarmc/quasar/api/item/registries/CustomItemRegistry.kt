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
import org.bukkit.Material
import org.bukkit.MusicInstrument
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
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

    val VIRTUAL_SIN_FORGIVENESS = registerHardcoded("virtual_sin_forgiveness") {
        CustomItem.Builder(Material.GOAT_HORN)
            .setComponent(DataComponentTypes.ITEM_MODEL, keyOf("quasar", "beam"))
            .setComponent(DataComponentTypes.ITEM_NAME, createFormatted("Virtual Sin Forgiveness"))
            .setComponent(DataComponentTypes.CUSTOM_NAME, createFormatted("<!i><b><c:white><shadow:green>Virtual Sin Forgiveness</shadow></c></b></!i>"))
            .setComponent(DataComponentTypes.RARITY, ItemRarity.EPIC)
            .setComponent(DataComponentTypes.MAX_STACK_SIZE, 1)
            .setComponent(DataComponentTypes.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.itemAttributes()
                .addModifier(Attribute.ATTACK_DAMAGE, AttributeModifier(
                    keyOf("quasar", "attack_damage"),
                    Double.MAX_VALUE / 100,
                    AttributeModifier.Operation.ADD_NUMBER))
                .addModifier(Attribute.ATTACK_SPEED, AttributeModifier(
                    keyOf("quasar", "attack_speed"),
                    Double.MAX_VALUE / 100,
                    AttributeModifier.Operation.ADD_NUMBER))
                .build())
            .setComponent(DataComponentTypes.INSTRUMENT, MusicInstrument.create { it ->
                it.empty()
                    .description(createFormatted("Your SOUL shines brighter"))
                    .duration(0.1f)
                    .soundEvent { it.empty().fixedRange(20f).location(SoundEventKeys.ENTITY_WARDEN_SONIC_BOOM) }
                    .range(20f)})
            .setComponent(DataComponentTypes.WEAPON, Weapon.weapon().build())
            .setBehavior(CustomItemBehaviorTypeRegistry.DEBUG_INTEGER, 67)
            .setBehavior(CustomItemBehaviorTypeRegistry.DEBUG_NUMBER, 0.123251)
            .setBehavior(CustomItemBehaviorTypeRegistry.DEBUG_INTEGER, 61)
            .setBehavior(CustomItemBehaviorTypeRegistry.DEBUG_CUSTOM_DATA, mapOf(
                keyOf("quasar", "test") to "this is a test",
                keyOf("quasar", "test_2") to "this is also a test",
                keyOf("quasar", "registries") to CustomRegistryRegistry
            ))
            .removeBehavior(CustomItemBehaviorTypeRegistry.DEBUG_INTEGER)
            .build()
    }
}
