package net.quasarmc.quasar.api

import org.bukkit.NamespacedKey

/**
 * Shorthand for constructing a [NamespacedKey]
 */
fun keyOf(namespace: String, key: String) = NamespacedKey(namespace, key)
