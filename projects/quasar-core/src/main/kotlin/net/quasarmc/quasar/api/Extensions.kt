package net.quasarmc.quasar.api

import com.mojang.brigadier.StringReader
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.minecraft.resources.Identifier
import org.bukkit.NamespacedKey

/**
 * Shorthand for constructing a [NamespacedKey]
 */
fun keyOf(namespace: String, key: String) = NamespacedKey(namespace, key)

/**
 * Format text with minimessage
 */
fun createFormatted(text: String): Component = MiniMessage.miniMessage().deserialize(text)

/**
 * Check if a reader is at the end of the string or the next character is whitespace
 */
fun StringReader.isAtArgumentEnd(): Boolean = !this.canRead() || this.peek() == ' '

/**
 * Convert a NMS namespaced key to a Bukkit namespaced key
 */
fun Identifier.toBukkitKey(): NamespacedKey = NamespacedKey(this.namespace, this.path)
