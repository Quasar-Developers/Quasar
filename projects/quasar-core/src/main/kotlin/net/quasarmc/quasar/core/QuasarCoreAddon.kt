package net.quasarmc.quasar.core

import net.quasarmc.quasar.api.addon.Addon

/**
 * Core addon for Quasar, containing all built-in content.
 */
class QuasarCoreAddon : Addon() {
    override val id   = "quasar"
    override val name = "Quasar Core"
}
