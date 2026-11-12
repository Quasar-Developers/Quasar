package net.quasarmc.quasar.core.administration.fakeop

import net.quasarmc.quasar.core.QuasarPlugin
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent

/**
 * Sends a fake permission level to clients upon joining.
 * This should fix the F3+F4 menu stop opping random people holy shit.
 */
object FakeOperatorPermissionsManager : Listener {
    @EventHandler
    fun onPlayerJoin(ev: PlayerJoinEvent) {
        for (level in 4 downTo  0) {
            if (ev.player.hasPermission("quasar.core.admin.fakeop.$level")) {
                QuasarPlugin.LOGGER.fine("Sending fake OP level $level to ${ev.player.name}")
                ev.player.sendOpLevel(level.toByte())
                break
            }
        }
    }
}
