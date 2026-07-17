package net.quasarmc.quasar.api.registration

import org.bukkit.NamespacedKey
import org.bukkit.plugin.Plugin

/**
 * Custom registry that automatically handles reloading and provides helpers to simplify registry building.
 */
open class SimpleCustomRegistry<TValue>(
    val identifier: NamespacedKey,
    val nativeNamespace: String
) : CustomRegistry<TValue>() {
    private val loader = HardcodedCustomRegistryLoader<TValue>(identifier)

    /**
     * Subscribe to required bukkit events.
     */
    fun registerEventHandlers(plugin: Plugin) {
        plugin.server.pluginManager.registerEvents(loader, plugin)
    }

    /**
     * Add a hardcoded value to automatically register
     */
    fun <TObject : TValue> registerHardcoded(id: NamespacedKey, provider: () -> TObject): HardcodedCustomResourcePointer<TValue, TObject> {
        return loader.register(id, provider)
    }

    /**
     * Add a hardcoded value to automatically register. Uses the registry native namespace and provided id to make the full identifier.
     */
    fun <TObject : TValue> registerHardcoded(id: String, provider: () -> TObject): HardcodedCustomResourcePointer<TValue, TObject> {
        return registerHardcoded(NamespacedKey(nativeNamespace, id), provider);
    }
}
