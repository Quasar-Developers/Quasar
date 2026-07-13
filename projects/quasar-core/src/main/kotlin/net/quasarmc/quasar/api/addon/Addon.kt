package net.quasarmc.quasar.api.addon

/**
 * Addon class for the Quasar API. See {QuasarCoreAddon} for an example on how to use it.
 *
 * Quasar addons are initialized during plugin bootstrapping, and therefore can only be used from
 * Paper plugins.
 */
abstract class Addon {
    /**
     * The unique ID of the addon. *Should* match the addon's resource/data namespace.
     */
    abstract val id: String

    /**
     * The name of the addon that is displayed to users.
     */
    abstract val name: String
}
