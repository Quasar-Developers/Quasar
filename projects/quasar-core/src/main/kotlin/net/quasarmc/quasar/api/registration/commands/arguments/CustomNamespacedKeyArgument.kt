package net.quasarmc.quasar.api.registration.commands.arguments

import com.mojang.brigadier.Message
import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import io.papermc.paper.command.brigadier.MessageComponentSerializer
import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import io.papermc.paper.command.brigadier.argument.CustomArgumentType
import net.kyori.adventure.text.Component
import net.quasarmc.quasar.api.keyOf
import net.quasarmc.quasar.api.registration.ICustomRegistry
import net.quasarmc.quasar.core.QuasarPlugin
import org.bukkit.NamespacedKey
import java.util.concurrent.CompletableFuture

/**
 * Namespaced key with completions for Quasar registries.
 */
class CustomNamespacedKeyArgument(
    val registry: ICustomRegistry<*>,
    val defaultNamespace: String = "quasar.core"
) : CustomArgumentType<NamespacedKey, NamespacedKey> {
    companion object {
        private val ERROR_NOT_FOUND = DynamicCommandExceptionType {
            MessageComponentSerializer.message().serialize(Component.text("No registry value with identifier \"$it\"!"))
        }

        private val ERROR_INVALID_NAMESPACE = DynamicCommandExceptionType {
            MessageComponentSerializer.message().serialize(Component.text("Invalid namespace \"$it\""))
        }

        private val ERROR_INVALID_KEY = DynamicCommandExceptionType {
            MessageComponentSerializer.message().serialize(Component.text("Invalid key \"$it\""))
        }
    }

    override fun getNativeType(): ArgumentType<NamespacedKey> = ArgumentTypes.namespacedKey()

    override fun parse(reader: StringReader): NamespacedKey {
        val string = reader.remaining.substringBefore(' ')
        reader.cursor += string.length

        return parse(string)
    }

    private fun parse(raw: String, defaultNamespace: String = this.defaultNamespace): NamespacedKey {
        val delim = raw.lastIndexOf(':')

        val namespace = if (delim != -1) { raw.substring(0, delim) } else { defaultNamespace }
        val key = if (delim != -1) { raw.substring(delim + 1) } else { raw }

        // we do no verification and rely entirely on brigadier :clueless:
        return keyOf(namespace, key)
    }

    override fun <S: Any> listSuggestions(
        context: CommandContext<S>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        // this can throw an illegalargumentexception because brigadier doesnt validate this before we get it
        val key = try {
            parse(builder.remainingLowerCase.substringBefore(' '), "")
        } catch (ex: IllegalArgumentException) { return builder.buildFuture() }

        // performance :chart_with_downwards_trend::fire::Fire:fire::shit_inakettle:
        for ((candidate, _) in registry) {
            if (key.namespace != "" && key.namespace != candidate.namespace)
                continue
            if (!(candidate.key.startsWith(key.key) ||
                 (key.namespace == "" && candidate.namespace.startsWith(key.key))))
                continue

            builder.suggest(candidate.toString())
        }

        return builder.buildFuture()
    }
}
