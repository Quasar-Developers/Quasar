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
import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.registry.RegistryAccess
import net.kyori.adventure.text.Component
import net.quasarmc.quasar.api.isAtArgumentEnd
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
    abstract fun parseArgument(reader: StringReader): TType

    /**
     * Parse an identifier.
     *
     * @param raw              The string to parse
     * @param defaultNamespace The namespace to use if only a key is provided
     */
    protected fun parseIdentifier(reader: StringReader, defaultNamespace: String = this.defaultNamespace): ParseIdentifierResult {
        val namespaceStart = reader.cursor
        while (reader.canRead() && NAMESPACE_ACCEPTABLE.contains(reader.peek())) reader.read();
        val namespace = reader.string.substring(namespaceStart, reader.cursor)

        if (reader.isAtArgumentEnd() || reader.peek() != ':') {
            return ParseIdentifierResult(keyOf(defaultNamespace, namespace), 0)
        }
        reader.read()

        val keyStart = reader.cursor
        while (reader.canRead() && KEY_ACCEPTABLE.contains(reader.peek())) reader.read();
        val key = reader.string.substring(keyStart, reader.cursor)

        return ParseIdentifierResult(keyOf(namespace, key), reader.cursor)
    }
    protected data class ParseIdentifierResult(val identifier: NamespacedKey, val end: Int);

    /**
     * Parse an identifier without allowing extra data at the end.
     *
     * @param raw              The string to parse
     * @param defaultNamespace The namespace to use if only a key is provided
     */
    protected fun parseIdentifierStrict(reader: StringReader, defaultNamespace: String = this.defaultNamespace): NamespacedKey {
        val (identifier, end) = parseIdentifier(reader, defaultNamespace)
        if (!reader.isAtArgumentEnd())
            throw ERROR_INVALID_KEY.create(identifier.key + reader.remaining)

        return identifier
    }

    override fun <S: Any> listSuggestions(
        context: CommandContext<S>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        // this can throw an illegalargumentexception because brigadier doesnt validate this before we get it
        val key = try {
            parseIdentifierStrict(StringReader(builder.remainingLowerCase.substringBefore(' ')), "")
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
        return parseArgument(reader)
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
