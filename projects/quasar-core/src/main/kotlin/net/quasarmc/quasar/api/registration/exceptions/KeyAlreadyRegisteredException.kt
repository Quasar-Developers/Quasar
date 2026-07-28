package net.quasarmc.quasar.api.registration.exceptions

/**
 * Exception indicating that a value with the provided key has already been registered.
 */
class KeyAlreadyRegisteredException(message: String) : RuntimeException(message);
