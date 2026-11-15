package net.quasarmc.quasar.core.administration.items

import net.kyori.adventure.key.Key
import net.quasarmc.quasar.api.item.CustomItem
import net.quasarmc.quasar.api.item.CustomItemBehaviorType
import net.quasarmc.quasar.api.util.component.CustomComponentMap
import net.quasarmc.quasar.core.QuasarPlugin
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.damage.DamageSource
import org.bukkit.damage.DamageType
import org.bukkit.entity.Entity
import org.bukkit.entity.Mob
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.loot.LootContext
import kotlin.math.pow

/**
 * An admin tool that instantly kills a clicked entity.
 */
class AdminEraseEntityItem(
    identifier: NamespacedKey,
    prototype: ItemStack,
    behavior: CustomComponentMap<CustomItemBehaviorType<Any>, Any>,

    /**
     * Remove dropped entity loot.
     */
    val deleteLoot: Boolean,

    /**
     * Remove inventory items.
     */
    val deleteInventory: Boolean,

    /**
     * Sound to play
     */
    val sound: Key?,

    /**
     * Draw an effect indicating admin action (aura)
     */
    val spawnParticles: Boolean
) : CustomItem(identifier, prototype, behavior) {
    override fun onInteract(ev: PlayerInteractEvent) {
        if (!doPermissionCheck(ev.player, ev.hand))
            return

        val res = ev.player.rayTraceEntities(120) ?: return;
        val hit = res.hitEntity ?: return;

        erase(ev.player, hit)
    }

    override fun onInteractEntity(ev: PlayerInteractEntityEvent) {
        if (!doPermissionCheck(ev.player, EquipmentSlot.HAND))
            return

        erase(ev.player, ev.rightClicked)
    }

    private fun doPermissionCheck(player: Player, hand: EquipmentSlot?): Boolean {
        if (!player.hasPermission("quasar.core.admin.erase_entity")) {
            player.sendRichMessage("<c:red><i>The artifact fizzles away in your hand</i></c:red>")
            when (hand) {
                EquipmentSlot.HAND -> player.inventory.setItemInMainHand(null)
                EquipmentSlot.OFF_HAND -> player.inventory.setItemInOffHand(null)
                else -> {}
            }

            QuasarPlugin.LOGGER.info("Blocked ${player.name} from using the erase entity tool.")

            return false
        }

        return true
    }

    private fun erase(player: Player, target: Entity) {
        if (!deleteLoot && target is Mob) {
            val loot = target.lootTable?.populateLoot(null, LootContext.Builder(target.location)
                .lootedEntity(target)
                .killer(player)
                .build())

            if (loot != null)
                for (item in loot)
                    target.world.dropItemNaturally(target.location, item)
        }

        if (deleteInventory && target is InventoryHolder && target !is Player) // Players are killed normally
            for (item in target.inventory.contents)
                target.world.dropItemNaturally(target.location, item ?: continue)

        if (sound != null)
            target.world.playSound(target.location, sound.toString(), 1.0f, 0.5f)

        if (spawnParticles) {
            val count = (player.location.distance(target.location) / 2).toInt()
            val incr = target.location.clone().subtract(player.location).multiply(count.toDouble().pow(-1.0))
            val offset = player.location.clone()
            for (i in 0..count)
                target.world.spawnParticle(Particle.SONIC_BOOM, offset.add(incr), 1)
        }

        if (target is Player) {
            target.kill(DamageSource.builder(DamageType.ARROW)
                .withDirectEntity(player)
                .withCausingEntity(player)
                .build())
        } else {
            target.remove()
        }
    }

    class Builder(identifier: NamespacedKey, material: Material) : CustomItemBuilder<Builder, AdminEraseEntityItem>(identifier, material) {
        var itemDeleteLoot: Boolean = true;
        var itemDeleteInventory: Boolean = false;
        var itemSound: Key? = null;
        var itemSpawnParticles: Boolean = false;

        fun deleteLoot(value: Boolean): Builder = this.apply { itemDeleteLoot = value }
        fun deleteInventory(value: Boolean): Builder = this.apply { itemDeleteInventory = value }
        fun sound(value: Key): Builder = this.apply { itemSound = value }
        fun spawnParticles(value: Boolean): Builder = this.apply { itemSpawnParticles = value }

        override fun build(): AdminEraseEntityItem {
            val item = AdminEraseEntityItem(
                identifier,
                ItemStack.of(material),
                behavior.build(),
                itemDeleteLoot,
                itemDeleteInventory,
                itemSound,
                itemSpawnParticles)

            augment(item)

            return item
        }
    }
}
