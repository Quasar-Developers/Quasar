package net.quasarmc.quasar.api.addon.exceptions

/**
 * Exception indicating a method was called at the wrong point in an addon's lifecycle.
 */
class AddonLifecycleException(message: String) : RuntimeException(message);
