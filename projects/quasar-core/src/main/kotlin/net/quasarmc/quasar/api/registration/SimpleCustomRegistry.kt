package net.quasarmc.quasar.api.registration

import org.bukkit.NamespacedKey
import org.bukkit.plugin.Plugin

/**
 * Custom registry that automatically handles reloading and provides helpers to simplify registry building.
 */
open class SimpleCustomRegistry<TValue>(
    /**
     * The identifier of the registry
     */
    val identifier: NamespacedKey,

    /**
     * The namespace to register hardcoded values into
     */
    val nativeNamespace: String
) : CustomRegistry<TValue>() {
    private val loader = HardcodedCustomRegistryLoader<TValue>(identifier)

    /**
     * Subscribe to required bukkit events.
     *
     * @param plugin The plugin for the addon that owns this registry
     */
    fun registerEventHandlers(plugin: Plugin) {
        plugin.server.pluginManager.registerEvents(loader, plugin)
    }

    /**
     * Add a hardcoded value to automatically register
     *
     * @param identifier The identifier of the object to register
     * @param provider   A function that provides the value to register
     */
    fun <TObject : TValue> registerHardcoded(identifier: NamespacedKey, provider: () -> TObject): HardcodedCustomResourcePointer<TValue, TObject> {
        return loader.register(identifier, provider)
    }

    /**
     * Add a hardcoded value to automatically register. Uses the registry native namespace and provided id to make the full identifier.
     *
     * @param identifier The identifier of the object to register
     * @param provider   A function that provides the value to register
     */
    fun <TObject : TValue> registerHardcoded(id: String, provider: () -> TObject): HardcodedCustomResourcePointer<TValue, TObject> {
        return registerHardcoded(NamespacedKey(nativeNamespace, id), provider);
    }
}
