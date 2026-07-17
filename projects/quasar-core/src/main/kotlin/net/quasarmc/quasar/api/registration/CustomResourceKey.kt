package net.quasarmc.quasar.api.registration

import org.bukkit.NamespacedKey

/**
 * An absolute identifier for any custom registry resource.
 */
data class CustomResourceKey<out TValue>(val registry: NamespacedKey, val identifier: NamespacedKey);
