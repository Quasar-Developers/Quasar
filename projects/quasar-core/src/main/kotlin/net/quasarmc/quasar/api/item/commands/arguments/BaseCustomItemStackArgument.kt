package net.quasarmc.quasar.api.item.commands.arguments

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import io.papermc.paper.command.brigadier.MessageComponentSerializer
import io.papermc.paper.registry.RegistryAccess
import io.papermc.paper.registry.RegistryKey
import net.kyori.adventure.text.Component
import net.quasarmc.quasar.api.createFormatted
import net.quasarmc.quasar.api.item.registries.CustomItemRegistry
import net.quasarmc.quasar.api.registration.commands.arguments.BaseCustomNamespacedKeyArgument
import org.bukkit.inventory.ItemStack
import java.util.concurrent.CompletableFuture

/**
 * Shared logic for [CustomItemStackArgument] and [MiniCustomItemStackArgument]
 */
abstract class BaseCustomItemStackArgument<TNative : Any>(
    defaultNamespace: String = "quasar.core",
    val includeVanilla: Boolean = true,
    val allowComponents: Boolean = true
) : BaseCustomNamespacedKeyArgument<ItemStack, TNative>(CustomItemRegistry, defaultNamespace) {
    protected val vanillaItemRegistry = RegistryAccess.registryAccess().getRegistry(RegistryKey.ITEM)

    override fun parseArgument(raw: String): ItemStack {
        val (key, keyEnd) = parseIdentifier(raw, "")
        if (!allowComponents && keyEnd != raw.length)
            throw ERROR_DISABLED_COMPONENT_LIST.create(Unit)

        val item = vanillaItemRegistry.get(key)?.createItemStack()
                ?: CustomItemRegistry.getOrNull(key)?.prototype?.clone()
        if (item == null)
            throw ERROR_NOT_FOUND.create(key)

        return item
    }

    override fun <S: Any> listSuggestions(
        context: CommandContext<S>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        // this can throw an illegalargumentexception because brigadier doesnt validate this before we get it
        val (key, keyEnd) = try {
            parseIdentifier(builder.remainingLowerCase.substringBefore(' '), "")
        } catch (ex: IllegalArgumentException) { return builder.buildFuture() }

        if (!allowComponents &&
            keyEnd != builder.remainingLowerCase.substringBefore(' ').length)
            return builder.buildFuture()

        suggestRegistry(builder, CustomItemRegistry, key)
        suggestRegistry(builder, vanillaItemRegistry, key)

        return builder.buildFuture()
    }

    companion object {
        val ERROR_DISABLED_COMPONENT_LIST = DynamicCommandExceptionType {
            MessageComponentSerializer.message().serialize(Component.text("!!!: You can not include a component list in this argument!"))
        }

        val ERROR_INVALID_COMPONENT_LIST = DynamicCommandExceptionType {
            MessageComponentSerializer.message().serialize(Component.text("!!!: Invalid component list!"))
        }
    }
}
