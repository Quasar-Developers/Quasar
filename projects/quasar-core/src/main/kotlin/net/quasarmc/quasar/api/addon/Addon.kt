package net.quasarmc.quasar.api.addon

import org.bukkit.Bukkit
import org.bukkit.event.Listener
import org.bukkit.plugin.Plugin

/**
 * Addon class for the Quasar API. See {QuasarCoreAddon} for an example on how to use it.
 *
 * Quasar addons are initialized during plugin bootstrapping, and therefore can only be used from
 * Paper plugins.
 */
abstract class Addon {
    /**
     * The unique ID of the addon. *Should* match the addon's resource/data namespace.
     */
    abstract val id: String

    /**
     * The name of the addon that is displayed to users.
     */
    abstract val name: String

    abstract val version: String

    abstract val description: String

    abstract val hasListeners: Boolean
    protected var hasListenersRegistered: Boolean = false

    abstract fun enable()

    abstract fun disable()

    fun registerListener(plugin: Plugin) {
        if(!hasListeners || hasListenersRegistered)return
        hasListenersRegistered = true
        Bukkit.getServer().pluginManager.registerEvents(this as Listener, plugin)
    }

    fun isEnabled(): Boolean{
        return AddonManager.addons.containsKey(id)
    }
}
