package net.quasarmc.quasar.core.registration

import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.ItemAttributeModifiers
import io.papermc.paper.datacomponent.item.ItemLore
import io.papermc.paper.registry.keys.SoundEventKeys
import net.quasarmc.quasar.api.createFormatted
import net.quasarmc.quasar.api.item.CustomItem
import net.quasarmc.quasar.api.item.registries.CustomItemRegistry
import net.quasarmc.quasar.api.keyOf
import net.quasarmc.quasar.api.registration.HardcodedCustomRegistryLoader
import net.quasarmc.quasar.core.administration.items.AdminEraseEntityItem
import org.bukkit.Material
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.inventory.ItemRarity

/**
 * All custom items for the quasar core content.
 *
 * @see CustomItemRegistry - quasar api content / debug content
 */
object CoreCustomItems {
    val loader = HardcodedCustomRegistryLoader<CustomItem>(CustomItemRegistry.identifier);

    val VIRTUAL_SIN_FORGIVENESS = loader.register(keyOf("quasar.core", "virtual_sin_forgiveness")) {
        AdminEraseEntityItem.Builder(keyOf("quasar.core", "virtual_sin_forgiveness"), Material.POISONOUS_POTATO)
            .deleteInventory(false)
            .deleteLoot(false)
            .sound(SoundEventKeys.ENTITY_WARDEN_SONIC_BOOM.key())
            .spawnParticles(true)
            .setComponent(DataComponentTypes.ITEM_MODEL, keyOf("quasar", "beam"))
            .setComponent(DataComponentTypes.ITEM_NAME, createFormatted("Virtual Sin Forgiveness"))
            .setComponent(DataComponentTypes.CUSTOM_NAME, createFormatted("<!i><b><c:white><shadow:green>Virtual Sin Forgiveness</shadow></c></b></!i>"))
            .setComponent(DataComponentTypes.LORE, ItemLore.lore(listOf(createFormatted("And you will know my name is the Lord when I lay my vengeance upon you"))))
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
            .removeComponent(DataComponentTypes.FOOD)
            .removeComponent(DataComponentTypes.CONSUMABLE)
            .build()
    }

    val ENTITY_ERASER = loader.register(keyOf("quasar.core", "entity_eraser")) {
        AdminEraseEntityItem.Builder(keyOf("quasar.core", "entity_eraser"), Material.POISONOUS_POTATO)
            .deleteLoot(true)
            .deleteInventory(false)
            .setComponent(DataComponentTypes.ITEM_MODEL, keyOf("minecraft", "iron_ingot"))
            .setComponent(DataComponentTypes.ITEM_NAME, createFormatted("Entity Eraser"))
            .setComponent(DataComponentTypes.LORE, ItemLore.lore(listOf(createFormatted("Kills entities and removes their loot."))))
            .setComponent(DataComponentTypes.RARITY, ItemRarity.EPIC)
            .setComponent(DataComponentTypes.MAX_STACK_SIZE, 1)
            .removeComponent(DataComponentTypes.FOOD)
            .removeComponent(DataComponentTypes.CONSUMABLE)
            .build()
    }
}
