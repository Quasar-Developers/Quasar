package net.quasarmc.quasar.api.plugin

import io.papermc.paper.plugin.bootstrap.BootstrapContext
import io.papermc.paper.plugin.bootstrap.PluginBootstrap
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import net.quasarmc.quasar.api.addon.AddonManager
import net.quasarmc.quasar.core.QuasarCoreAddon

@Suppress("UnstableApiUsage")
class QuasarPluginBootstrap : PluginBootstrap {
    /**
     * Quasar core addon. This is the entry point to all core code.
     */
    private val addon = QuasarCoreAddon();

    override fun bootstrap(context: BootstrapContext) {
        context.lifecycleManager.registerEventHandler(LifecycleEvents.DATAPACK_DISCOVERY.newHandler {
            it.registrar().discoverPack(this.javaClass.getResource("/datapack")!!.toURI(), "data");
        })

        AddonManager.register(addon);
    }
}
