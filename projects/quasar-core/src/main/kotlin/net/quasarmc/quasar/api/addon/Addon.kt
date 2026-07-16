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

    // TODO: Make a proper version class.
    /**
     * The addon's version.
     *
     * This doesn't have a specific format yet,
     * and isn't used for any other than displaying.
    **/
    abstract val version: String

    /**
     * A description of the addon that will be displayed.
     *
     * You can use newlines (\n) in the description.
    **/
    abstract val description: String
}
