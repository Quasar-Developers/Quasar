package net.quasarmc.quasar.api.registration.exceptions

import net.quasarmc.quasar.api.exception.KeyAlreadyPresentException

/**
 * Exception indicating that a value with the provided key has already been registered.
 */
class KeyAlreadyRegisteredException(message: String) : KeyAlreadyPresentException(message);
