package net.quasarmc.quasar.api.item.commands.arguments

import com.mojang.brigadier.arguments.ArgumentType
import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import org.bukkit.NamespacedKey

/**
 * ItemStack that can go anywhere in the command. Prefer [CustomItemStackArgument], as this does not support components.
 */
/*
class MiniCustomItemStackArgument (
    defaultNamespace: String = "quasar.core",
    includeVanilla: Boolean = true,
) : BaseCustomItemStackArgument<NamespacedKey>(defaultNamespace, includeVanilla, false) {
    override fun getNativeType(): ArgumentType<NamespacedKey> = ArgumentTypes.namespacedKey()
}
*/
