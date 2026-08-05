package net.quasarmc.quasar.api.addon

import net.quasarmc.quasar.api.addon.exceptions.AddonLifecycleException
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
     * The state of the plugin.
     */
    var state: AddonState = AddonState.LOADED

    /**
     * The plugin providing this addon. Only valid after the paper plugin loading phase.
     *
     * Initialized by [AddonManager.attachPlugin]
     */
    lateinit var plugin: TPlugin
        private set;

    /**
     * Attach an addon's plugin to it. Should only be called by the providing plugin.
     *
     * @param plugin The providing plugin
     */
    fun attachPlugin(plugin: TPlugin) {
        if (state != AddonState.LOADED)
            throw AddonLifecycleException("Attempted to attach a second plugin to an addon.")

        this.plugin = plugin;
        this.state = AddonState.PRE_INIT
    }
}
