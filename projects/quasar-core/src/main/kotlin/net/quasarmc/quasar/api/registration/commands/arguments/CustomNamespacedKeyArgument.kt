package net.quasarmc.quasar.api.registration.commands.arguments

import com.mojang.brigadier.arguments.ArgumentType
import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import net.quasarmc.quasar.api.registration.ICustomRegistry
import org.bukkit.NamespacedKey

/**
 * Namespaced key with completions for Quasar registries.
 */
class CustomNamespacedKeyArgument(
    registry: ICustomRegistry<*>,
    defaultNamespace: String = "quasar.core"
) : BaseCustomNamespacedKeyArgument<NamespacedKey, NamespacedKey>(registry, defaultNamespace) {
    override fun getNativeType(): ArgumentType<NamespacedKey> = ArgumentTypes.namespacedKey()
    override fun parseArgument(raw: String): NamespacedKey = parseIdentifierStrict(raw)
}
