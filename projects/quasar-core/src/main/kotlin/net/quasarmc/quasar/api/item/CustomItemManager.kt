package net.quasarmc.quasar.api.item

import net.quasarmc.quasar.api.item.registries.CustomItemRegistry
import net.quasarmc.quasar.core.QuasarPlugin
import org.bukkit.NamespacedKey
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractAtEntityEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack

/**
 * Plumbing for custom item events.
 */
object CustomItemManager : Listener {
    @EventHandler
    private fun onPlayerInteract(ev: PlayerInteractEvent) {
        if (!ev.hasItem())
            return

        getCustomItem(ev.item!!)?.onInteract(ev)
    }

    @EventHandler
    private fun onPlayerInteractEntity(ev: PlayerInteractEntityEvent) {
        val item = ev.player.inventory.itemInMainHand
        if (item.isEmpty)
            return

        getCustomItem(item)?.onInteractEntity(ev)
    }

    private fun getCustomItem(item: ItemStack): CustomItem? {
        val rawCustomIdentifier = item.persistentDataContainer.get(CustomItem.PDC_IDENTIFIER_KEY)
            ?: return null
        val customIdentifier = NamespacedKey.fromString(rawCustomIdentifier)
            ?: throw RuntimeException("Invalid item id $rawCustomIdentifier")
        val customItem = CustomItemRegistry.getOrNull(customIdentifier)

        if (customItem == null)
            QuasarPlugin.LOGGER.warning("Unrecognized custom item id $rawCustomIdentifier")

        return customItem
    }
}
