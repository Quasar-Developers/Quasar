package net.quasarmc.quasar.api.item.commands.arguments

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import io.papermc.paper.registry.RegistryAccess
import io.papermc.paper.registry.RegistryKey
import net.quasarmc.quasar.api.item.registries.CustomItemRegistry
import net.quasarmc.quasar.api.registration.commands.arguments.BaseCustomNamespacedKeyArgument
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import java.util.concurrent.CompletableFuture

/**
 * Shared logic for [CustomItemStackArgument] and [MiniCustomItemStackArgument]
 */
class CustomItemStackArgument(
    defaultNamespace: String = "quasar.core",
    val includeVanilla: Boolean = true
) : BaseCustomNamespacedKeyArgument<ItemStack, NamespacedKey>(CustomItemRegistry, defaultNamespace) {
    protected val vanillaItemRegistry = RegistryAccess.registryAccess().getRegistry(RegistryKey.ITEM)

    override fun getNativeType(): ArgumentType<NamespacedKey> = ArgumentTypes.namespacedKey()

    override fun parseArgument(reader: StringReader): ItemStack {
        val (key, keyEnd) = parseIdentifier(reader, defaultNamespace)

        val item = vanillaItemRegistry.get(key)?.createItemStack()
                ?: CustomItemRegistry.getOrNull(key)?.prototype?.clone()
        if (item == null)
            throw ERROR_NOT_FOUND.createWithContext(reader, key)

        return item
    }

    override fun <S: Any> listSuggestions(
        context: CommandContext<S>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        val key = try {
            parseIdentifierStrict(StringReader(builder.remainingLowerCase.substringBefore(' ')), "")
        } catch (ex: IllegalArgumentException) { return builder.buildFuture() }

        suggestRegistry(builder, CustomItemRegistry, key)
        if (includeVanilla)
            suggestRegistry(builder, vanillaItemRegistry, key)

        return builder.buildFuture()
    }
}
