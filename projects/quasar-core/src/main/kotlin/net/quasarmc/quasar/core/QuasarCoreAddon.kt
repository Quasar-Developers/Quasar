package net.quasarmc.quasar.core

import net.quasarmc.quasar.api.addon.Addon
import net.quasarmc.quasar.api.plugin.QuasarPlugin

/**
 * Core addon for Quasar, containing all built-in content.
 */
class QuasarCoreAddon : Addon<QuasarPlugin>() {
    override val id   = "quasar"
    override val name = "Quasar Core"
}
