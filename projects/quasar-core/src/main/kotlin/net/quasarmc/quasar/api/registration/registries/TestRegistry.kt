package net.quasarmc.quasar.api.registration.registries

import net.quasarmc.quasar.api.registration.SimpleCustomRegistry
import org.bukkit.NamespacedKey

object TestRegistry : SimpleCustomRegistry<Any>(
    NamespacedKey("quasar", "test"),
    "quasar"
) {
    val ELEVEN = registerHardcoded("eleven") { 67 }
    val HELLO  = registerHardcoded("hello") { "hello" }
    val IDK    = registerHardcoded("idk") { "magic!" }
}
