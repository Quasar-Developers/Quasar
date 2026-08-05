package net.quasarmc.quasar.api.addon

import org.bukkit.plugin.java.JavaPlugin

/**
 * Addon class for the Quasar API. See {QuasarCoreAddon} for an example on how to use it.
 */
abstract class Addon<TPlugin : JavaPlugin> {
    /**
     * The unique ID of the addon. *Should* match the addon's resource/data namespace.
     */
    abstract val id: String

    /**
     * The name of the addon that is displayed to users.
     */
    abstract val name: String

    /**
     * The plugin providing this addon. Only valid after the paper plugin loading phase.
     *
     * Initialized by [AddonManager.attachPlugin]
     */
    lateinit var plugin: TPlugin
        private set;

    /**
     * Attach an addons owning plugin to it. Should only be called by the owning plugin.
     *
     * @param plugin The owning plugin
     */
    fun attachPlugin(plugin: TPlugin) {
        this.plugin = plugin;
    }
}
