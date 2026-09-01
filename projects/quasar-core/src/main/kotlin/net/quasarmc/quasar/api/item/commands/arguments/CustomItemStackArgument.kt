package net.quasarmc.quasar.api.item.commands.arguments

import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import org.bukkit.inventory.ItemStack

/**
 * ItemStack argument with support for Quasar items. Ideally should be able to go anywhere, but brigadier doesn't
 * have a word type that has a colon or brackets in it, and we can't use ItemStack because the client doesn't know
 * about our items or components. If it is absolutely necessary for the item to go in the middle of the command, you
 * can use [MiniCustomItemStackArgument] instead, which only checks a NamespacedKey.
 */
class CustomItemStackArgument (
    defaultNamespace: String = "quasar.core",
    includeVanilla: Boolean = true,
    allowComponents: Boolean = true
) : BaseCustomItemStackArgument<String>(defaultNamespace, includeVanilla, allowComponents) {
    override fun getNativeType(): ArgumentType<String> = StringArgumentType.greedyString()
}
