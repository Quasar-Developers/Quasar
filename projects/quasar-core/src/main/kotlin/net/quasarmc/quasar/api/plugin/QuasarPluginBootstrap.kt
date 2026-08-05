package net.quasarmc.quasar.api.plugin

import io.papermc.paper.plugin.bootstrap.BootstrapContext
import io.papermc.paper.plugin.bootstrap.PluginBootstrap
import io.papermc.paper.plugin.bootstrap.PluginProviderContext
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import net.quasarmc.quasar.api.addon.AddonManager
import net.quasarmc.quasar.core.QuasarCoreAddon
import org.bukkit.plugin.java.JavaPlugin

@Suppress("UnstableApiUsage")
class QuasarPluginBootstrap : PluginBootstrap {
    /**
     * The core addon for Quasar.
     */
    val core = QuasarCoreAddon();

    override fun bootstrap(context: BootstrapContext) {
        // add our datapack
        context.lifecycleManager.registerEventHandler(LifecycleEvents.DATAPACK_DISCOVERY.newHandler {
            it.registrar().discoverPack(this.javaClass.getResource("/datapack")!!.toURI(), "data");
        })

        // register the core addon
        AddonManager.register(core)
    }

    override fun createPlugin(context: PluginProviderContext): JavaPlugin {
        return QuasarPlugin(core)
    }
}
