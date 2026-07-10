package net.quasarmc.quasar.api.plugin

import io.papermc.paper.plugin.bootstrap.BootstrapContext
import io.papermc.paper.plugin.bootstrap.PluginBootstrap
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents

class QuasarAPIPluginBootstrap : PluginBootstrap {
    override fun bootstrap(context: BootstrapContext) {
        context.lifecycleManager.registerEventHandler(LifecycleEvents.DATAPACK_DISCOVERY.newHandler {
            it.registrar().discoverPack(this.javaClass.getResource("/datapack")!!.toURI(), "data");
        })
    }
}
