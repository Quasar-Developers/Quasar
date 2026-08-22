package net.quasarmc.quasar.api

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.NamespacedKey

/**
 * Shorthand for constructing a [NamespacedKey]
 */
fun keyOf(namespace: String, key: String) = NamespacedKey(namespace, key)

/**
 * Format text with minimessage
 */
fun createFormatted(text: String): Component = MiniMessage.miniMessage().deserialize(text)
