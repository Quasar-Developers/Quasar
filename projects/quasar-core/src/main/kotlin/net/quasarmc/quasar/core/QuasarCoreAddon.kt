package net.quasarmc.quasar.core

import io.papermc.paper.chat.ChatRenderer
import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.quasarmc.quasar.api.addon.Addon
import org.bukkit.Bukkit
import org.bukkit.Server
import org.bukkit.WorldCreator
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.server.ServerLoadEvent

/**
 * Core addon for Quasar, containing all built-in content.
 */
class QuasarCoreAddon : Addon(), Listener {
    override val id   = "quasar"
    override val name = "Quasar Core"
    override val version = "0.0.0"
    override val description = "The core addon for Quasar."

    override val hasListeners = true

    val minimsg = MiniMessage.miniMessage()
    var server: Server? = null

    // Addon overrides
    override fun enable() {
        if(server===null)return
        broadcast("<#97e4ef>Quasar<#38e5e4> successfully initialized.")
        server!!.createWorld(WorldCreator("overworld_reset_storage"))
    }

    override fun disable() {
        if(server===null)return
        broadcast("<#97e4ef>Quasar<#38e5e4> successfully disabled.")
    }

    // Methods
    fun broadcast(text: String) {
        (server!! as Audience).sendMessage(minimsg.deserialize(text))
    }

    // Handlers
    @EventHandler
    fun serverStart(evt: ServerLoadEvent){
        server = Bukkit.getServer()
        if(isEnabled())disable()
        enable()
    }

    @EventHandler
    fun chatted(evt: AsyncChatEvent){
        if(!isEnabled())return
        evt.viewers().forEach {
            it.sendMessage(evt.player.displayName().append(Component.text(": ")).append(evt.message()))
        }
        evt.isCancelled = true
    }

}
