package net.quasarmc.quasar.api.item

import net.quasarmc.quasar.api.item.registries.CustomItemRegistry
import net.quasarmc.quasar.core.QuasarPlugin
import org.bukkit.NamespacedKey
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEvent

/**
 * Plumbing for custom item events.
 */
object CustomItemManager : Listener {
    @EventHandler
    private fun onPlayerInteract(ev: PlayerInteractEvent) {
        if (!ev.hasItem())
            return

        val item = ev.item!!;
        val rawCustomIdentifier = item.persistentDataContainer.get(CustomItem.PDC_IDENTIFIER_KEY)
            ?: return
        val customIdentifier = NamespacedKey.fromString(rawCustomIdentifier)
            ?: throw RuntimeException("Invalid item id $rawCustomIdentifier")
        val customItem = CustomItemRegistry.getOrNull(customIdentifier)
            ?: return QuasarPlugin.LOGGER.warning("Unrecognized custom item id $rawCustomIdentifier")

        customItem.onInteract(ev)
    }
}
