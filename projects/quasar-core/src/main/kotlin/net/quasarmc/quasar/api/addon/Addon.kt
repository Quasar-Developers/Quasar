package net.quasarmc.quasar.api.addon

import net.quasarmc.quasar.api.addon.exceptions.AddonLifecycleException
import org.bukkit.NamespacedKey
import org.bukkit.plugin.java.JavaPlugin

/**
 * Addon class for the Quasar API.
 *
 * @see net.quasarmc.quasar.core.QuasarCoreAddon
 */
abstract class Addon<TPlugin : JavaPlugin> {
    /**
     * The unique ID of the addon. *Should* match the addon's resource/data namespace.
     *
     * The namespace should be the namespace of the providing plugin
     * The path should be:
     * - For a plugin providing a single addon, the plugin's namespace (quasar:quasar)
     * - For a plugin providing multiple addons, plugin.addon_name (quasar:quasar.core, quasar:quasar.api)
     */
    abstract val identifier: NamespacedKey

    /**
     * The name of the addon that is displayed to users.
     */
    abstract val name: String

    /**
     * A short description of the addon.
     */
    abstract val description: String

    /**
     * The addon version.
     */
    abstract val version: String

    /**
     * The author or authors of the addon. Prefer "{ADDON} Authors" for free/open-source addons.
     */
    abstract val author: String

    /**
     * A link to the source code for the addon.
     */
    abstract val sourceURL: String

    /**
     * The state of the plugin.
     */
    var state: AddonState = AddonState.LOADED

    /**
     * The plugin providing this addon. Only valid after the paper plugin loading phase.
     *
     * Initialized by [attachPlugin]
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
