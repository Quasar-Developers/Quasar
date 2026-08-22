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
import org.bukkit.Registry
import java.util.concurrent.CompletableFuture
import kotlin.collections.iterator

/**
 * Base class for namespaced key arguments. Implement parseArgument to get a string, use parseIdentifier to parse
 * the identifier and get the end of the identifier part.
 */
open abstract class BaseCustomNamespacedKeyArgument<TType : Any, TNative : Any>(
    val registry: ICustomRegistry<*>,
    val defaultNamespace: String = "quasar.core"
) : CustomArgumentType<TType, TNative> {
    /**
     * Parse the raw argument.
     */
    abstract fun parseArgument(raw: String): TType

    /**
     * Parse an identifier.
     *
     * @param raw              The string to parse
     * @param defaultNamespace The namespace to use if only a key is provided
     */
    protected fun parseIdentifier(raw: String, defaultNamespace: String = this.defaultNamespace): ParseIdentifierResult {
        val delim = raw.indexOf(':')
        val end = raw.substring(delim + 1).indexOfFirst { !KEY_ACCEPTABLE.contains(it.lowercaseChar()) }
                                                     .let { if (it == -1) { raw.length } else { it } }

        val namespace = if (delim != -1) { raw.substring(0, delim) } else { defaultNamespace }
        val key = if (delim != -1) { raw.substring(delim + 1, end) } else { raw.substring(0, end) }

        // we do no verification and rely entirely on brigadier :clueless:
        return ParseIdentifierResult(keyOf(namespace, key), end)
    }
    protected data class ParseIdentifierResult(val identifier: NamespacedKey, val end: Int);

    /**
     * Parse an identifier without allowing extra data at the end.
     *
     * @param raw              The string to parse
     * @param defaultNamespace The namespace to use if only a key is provided
     */
    protected fun parseIdentifierStrict(raw: String, defaultNamespace: String = this.defaultNamespace): NamespacedKey {
        val (identifier, end) = parseIdentifier(raw, defaultNamespace)
        if (end != raw.length)
            throw ERROR_INVALID_KEY.create(identifier.key + raw.substring(end))

        return identifier
    }

    override fun <S: Any> listSuggestions(
        context: CommandContext<S>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        // this can throw an illegalargumentexception because brigadier doesnt validate this before we get it
        val key = try {
            parseIdentifierStrict(builder.remainingLowerCase.substringBefore(' '), "")
        } catch (ex: IllegalArgumentException) { return builder.buildFuture() }

        suggestRegistry(builder, registry, key)

        return builder.buildFuture()
    }

    /**
     * Add items in a registry to the list of tab completions
     *
     * @param builder  The suggestions builder to use
     * @param registry The registry to scan
     * @param key      The identifier to suggest completions for
     */
    protected fun suggestRegistry(builder: SuggestionsBuilder, registry: ICustomRegistry<*>, key: NamespacedKey) {
        suggestFromKeyIterator(builder, registry.keyIterator(), key)
    }

    /**
     * Add items in a registry to the list of tab completions
     *
     * @param builder  The suggestions builder to use
     * @param registry The registry to scan
     * @param key      The identifier to suggest completions for
     */
    protected fun suggestRegistry(builder: SuggestionsBuilder, registry: Registry<*>, key: NamespacedKey) {
        suggestFromKeyIterator(builder, registry.keyStream().iterator(), key)
    }

    /**
     * Suggest values returned by a key iterator if they match the incomplete key
     *
     * @param builder  The suggestions builder to use
     * @param iterator The iterator to scan
     * @param key      The identifier to suggest completions for
     */
    protected fun suggestFromKeyIterator(builder: SuggestionsBuilder, iterator: Iterator<NamespacedKey>, key: NamespacedKey) {
        // performance :chart_with_downwards_trend::fire::Fire:fire::shit_inakettle:
        for (candidate in iterator) {
            if (key.namespace != "" && key.namespace != candidate.namespace)
                continue
            if (!(candidate.key.startsWith(key.key) ||
                        (key.namespace == "" && candidate.namespace.startsWith(key.key))))
                continue

            builder.suggest(candidate.toString())
        }
    }

    override fun parse(reader: StringReader): TType {
        val string = reader.remaining.substringBefore(' ')
        reader.cursor += string.length

        return parseArgument(string)
    }

    companion object {
        val ERROR_NOT_FOUND = DynamicCommandExceptionType {
            MessageComponentSerializer.message().serialize(Component.text("No registry value with identifier \"$it\"!"))
        }

        val ERROR_INVALID_NAMESPACE = DynamicCommandExceptionType {
            MessageComponentSerializer.message().serialize(Component.text("Invalid namespace \"$it\""))
        }

        val ERROR_INVALID_KEY = DynamicCommandExceptionType {
            MessageComponentSerializer.message().serialize(Component.text("Invalid key \"$it\""))
        }

        val NAMESPACE_ACCEPTABLE = "0123456789abcdefghijklmnopqrstuvwxyz_-.".toCharArray()
        val KEY_ACCEPTABLE = NAMESPACE_ACCEPTABLE.union(listOf('/')).toCharArray()
    }
}
