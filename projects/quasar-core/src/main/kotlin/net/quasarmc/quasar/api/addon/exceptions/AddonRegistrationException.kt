package net.quasarmc.quasar.api.addon.exceptions

/**
 * Exception indicating an unrecoverable error during plugin registration.
 */
class AddonRegistrationException(message: String) : RuntimeException(message);
