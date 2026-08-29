package net.quasarmc.quasar.api.item.commands.arguments

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import io.papermc.paper.command.brigadier.MessageComponentSerializer
import io.papermc.paper.registry.RegistryAccess
import io.papermc.paper.registry.RegistryKey
import net.kyori.adventure.text.Component
import net.minecraft.core.registries.BuiltInRegistries
import net.quasarmc.quasar.api.isAtArgumentEnd
import net.quasarmc.quasar.api.item.CustomItem
import net.quasarmc.quasar.api.item.registries.CustomItemRegistry
import net.quasarmc.quasar.api.keyOf
import net.quasarmc.quasar.api.registration.commands.arguments.BaseCustomNamespacedKeyArgument
import net.quasarmc.quasar.api.toBukkitKey
import net.quasarmc.quasar.core.QuasarPlugin
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import java.util.Stack
import java.util.Vector
import java.util.concurrent.CompletableFuture
import kotlin.reflect.KClass

/**
 * Shared logic for [CustomItemStackArgument] and [MiniCustomItemStackArgument]
 */
abstract class BaseCustomItemStackArgument<TNative : Any>(
    defaultNamespace: String = "quasar.core",
    val includeVanilla: Boolean = true,
    val allowComponents: Boolean = true
) : BaseCustomNamespacedKeyArgument<ItemStack, TNative>(CustomItemRegistry, defaultNamespace) {
    protected val vanillaItemRegistry = RegistryAccess.registryAccess().getRegistry(RegistryKey.ITEM)

    override fun parseArgument(reader: StringReader): ItemStack {
        val (key, keyEnd) = parseIdentifier(reader, defaultNamespace)
        if (!allowComponents && reader.isAtArgumentEnd())
            throw ERROR_DISABLED_COMPONENT_LIST.createWithContext(reader, Unit)

        val item = vanillaItemRegistry.get(key)?.createItemStack()
                ?: CustomItemRegistry.getOrNull(key)?.prototype?.clone()
        if (item == null)
            throw ERROR_NOT_FOUND.createWithContext(reader, key)

        val components = parseComponents(reader)

        return item
    }

    override fun <S: Any> listSuggestions(
        context: CommandContext<S>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        // this can throw an illegalargumentexception because brigadier doesnt validate this before we get it
        val (key, keyEnd) = try {
            parseIdentifier(StringReader(builder.remainingLowerCase.substringBefore(' ')), "")
        } catch (ex: IllegalArgumentException) { return builder.buildFuture() }

        if (!allowComponents &&
            keyEnd != builder.remainingLowerCase.substringBefore(' ').length)
            return builder.buildFuture()

        if (vanillaItemRegistry[key] != null) {
            suggestVanillaComponents(
                builder,
                key,
                vanillaItemRegistry[key]!!.createItemStack(),
                StringReader(builder.remainingLowerCase.substringBefore(' ').substring(keyEnd))
            )
        } else if (CustomItemRegistry.contains(key)) {
            suggestVanillaComponents(
                builder,
                key,
                CustomItemRegistry[key].prototype,
                StringReader(builder.remainingLowerCase.substringBefore(' ').substring(keyEnd))
            )
            suggestCustomComponents(
                builder,
                key,
                CustomItemRegistry[key],
                StringReader(builder.remainingLowerCase.substringBefore(' ').substring(keyEnd))
            )
        }

        suggestRegistry(builder, CustomItemRegistry, key)
        suggestRegistry(builder, vanillaItemRegistry, key)

        return builder.buildFuture()
    }

    protected fun suggestVanillaComponents(builder: SuggestionsBuilder, key: NamespacedKey, item: ItemStack, reader: StringReader) {
        if (reader.isAtArgumentEnd())
            return;
        val parsed = parseComponents(reader)

        // TODO: add read registry keys
        val builderPrefix = key.toString() + "["

        item.persistentDataContainer.get(keyOf("",""), PersistentDataType.INTEGER)

        // NMS START
        for ((typeKey, type) in BuiltInRegistries.DATA_COMPONENT_TYPE.entrySet()) {
            // i don't know what to do with types without a codec
            val codec = type.codec() ?: continue;

            if (shouldCompleteAs(parsed.lastOrNull()?.name ?: keyOf("", ""), typeKey.identifier().toBukkitKey()))
                builder.suggest(builderPrefix + typeKey.identifier().toString() + "=")
            /*
            if (codec == NMSUnit.CODEC && shouldCompleteAs(key, typeKey.identifier().toBukkitKey())) {
                builder.suggest(builderPrefix + typeKey.identifier().toString())
                continue
            }
            */
        }
        // NMS END
    }

    protected fun suggestCustomComponents(builder: SuggestionsBuilder, key: NamespacedKey, item: CustomItem, reader: StringReader) {
        val parsed = parseComponents(reader)

        // we don't suggest starting a component block here because we already do in suggestVanillaComponents
        if (parsed.count() == 0)
            return
    }

    protected fun parseComponents(reader: StringReader): List<ParsedComponent> {
        val components = ArrayList<ParsedComponent>()
        if (reader.isAtArgumentEnd())
            return components

        reader.expect('[')
        while (!reader.isAtArgumentEnd()) {
            val char = reader.peek()
            if (char == ']')
                break

            val start = reader.cursor

            val typeKeyStart = reader.cursor
            val (typeKey, typeKeyEnd) = parseIdentifier(reader, "")
            if (reader.isAtArgumentEnd() || reader.read() != '=') {
                components.add(ParsedComponent(typeKey, Unit, start, reader.cursor, false))
                break
            }

            val data = parseSNBT(reader)
            QuasarPlugin.LOGGER.info(data.toString())

            break
        }

        return components
    }

    protected fun parseSNBT(reader: StringReader): Pair<ParsedNBTTag, Stack<ParsedNBTPathElement>> {
        val fullStart = reader.cursor
        val stack = Stack<ParsedNBTTag>()
        val pathStack = Stack<ParsedNBTPathElement>()

        fun <TValue : ParsedNBTTag> accept(attribute: String, tag: TValue): TValue {
            if (!stack.empty()) {
                when (val top = stack.peek()) {
                    is ParsedNBTCompoundTag -> top.value[attribute] = tag
                    is ParsedNBTListTag -> top.value.addLast(tag)
                    else -> throw Error("Top of stack is not a compound or list tag")
                }
            }

            if (tag is ParsedNBTCompoundTag || tag is ParsedNBTListTag) {
                if (!stack.empty()) {
                    when (val top = stack.peek()) {
                        is ParsedNBTCompoundTag -> pathStack.push(ParsedNBTCompoundPathElement(attribute))
                        is ParsedNBTIndexedTag -> pathStack.push(ParsedNBTIndexPathElement(top.size))
                        else -> throw Error("Top of stack is not a compound or list tag")
                    }
                }

                stack.push(tag)
            }

            return tag
        }

        fun die(value: ParsedNBTTag = stack[0]): Pair<ParsedNBTTag, Stack<ParsedNBTPathElement>> {
            return Pair(stack[0], pathStack)
        }

        fun dieWithAttributeFragment(tag: ParsedNBTAttributeFragment, root: ParsedNBTTag = stack[0]): Pair<ParsedNBTTag, Stack<ParsedNBTPathElement>> {
            pathStack.push(ParsedNBTCompoundPathElement(tag.name))
            return die(root)
        }

        while (reader.canRead()) {
            val nodeStart = reader.cursor

            reader.skipWhitespace()
            val maybeTerminator = reader.peek()
            if (maybeTerminator == '}' || maybeTerminator == ']') {
                when (reader.peek()) {
                    '}' -> if (stack.peek() !is ParsedNBTCompoundTag) throw ERROR_UNEXPECTED_TERMINATOR.createWithContext(reader, '}')
                    ']' -> if (stack.peek() !is ParsedNBTListTag)     throw ERROR_UNEXPECTED_TERMINATOR.createWithContext(reader, ']')
                }

                pathStack.pop()
                val result = stack.pop()
                result.complete = true
                result.end = reader.cursor
                if (stack.empty()) return die(result)
            }

            reader.skipWhitespace()
            val attribute = if (!stack.empty() && stack.peek() !is ParsedNBTListTag) {
                val attribute = parseSNBTAttribute(reader)

                reader.skipWhitespace()
                if (!reader.canRead() || reader.read() != ':') {
                    return dieWithAttributeFragment(accept(attribute, ParsedNBTAttributeFragment(attribute, nodeStart, reader.cursor)))
                }

                attribute
            } else {  }

            reader.skipWhitespace()
            if (!reader.canRead()) {
                return dieWithAttributeFragment(accept(attribute, ParsedNBTAttributeFragment(attribute, nodeStart, reader.cursor)))
            }
            val data = when (reader.peek()) {
                '{' -> {
                    reader.skip()
                    ParsedNBTCompoundTag(HashMap(), reader.cursor - 1, Int.MAX_VALUE, false)
                }
                '[' -> {
                    reader.skip()
                    // * is a meaningless value, it just has to be an invalid type.
                    val type = if (reader.canRead()) { reader.peek() } else { '*' }
                    when (type) {
                        'B' -> TODO("arrays")
                        'I' -> TODO("arrays")
                        'L' -> TODO("arrays")
                        else -> ParsedNBTListTag(Vector(), ParsedNBTTag::class, reader.cursor - 1, Int.MAX_VALUE, false)
                    }
                }
                '"', '\'' -> parseSNBTString(reader)
                in '0'..'9', '.' -> parseSNBTNumber(reader)
                else -> throw ERROR_INVALID_SNBT.createWithContext(reader, reader.peek())
            }

            accept(attribute, data)
        }

        // Check if we're complete (we should have exactly one item in the stack)
        if (stack.size != 1) {
            return die()
        } else return die()
    }

    protected fun parseSNBTAttribute(reader: StringReader): String {
        // Cutting corners, if it becomes a problem I can write an actual parser.
        // Surely Mojang would be consistent in this :godo:
        return when (reader.peek()) {
            '\'', '\"' -> reader.readQuotedString()
            else -> reader.readUnquotedString()
        }
    }

    protected fun parseSNBTString(reader: StringReader): ParsedNBTTag {
        // Cutting corners again :)
        val start = reader.cursor
        val data = reader.readQuotedString()
        return ParsedNBTStringTag(data, start, reader.cursor - 1, true)
    }

    protected fun parseSNBTNumber(reader: StringReader): ParsedNBTTag {
        TODO("finish")
    }

    protected fun shouldCompleteAs(source: NamespacedKey, to: NamespacedKey): Boolean {
        if (source.namespace != "" && source.namespace != to.namespace)
            return false

        return to.key.startsWith(source.key) ||
               (source.namespace == "" && to.namespace.startsWith(source.key))
    }

    companion object {
        val ERROR_DISABLED_COMPONENT_LIST = DynamicCommandExceptionType {
            MessageComponentSerializer.message().serialize(Component.text("!!!: You can not include a component list in this argument!"))
        }

        val ERROR_INVALID_COMPONENT_LIST = DynamicCommandExceptionType {
            MessageComponentSerializer.message().serialize(Component.text("!!!: Invalid component list!"))
        }

        val ERROR_INVALID_SNBT = DynamicCommandExceptionType {
            MessageComponentSerializer.message().serialize(Component.text("!!!: Invalid SNBT! Unexpected character $it"))
        }

        val ERROR_UNEXPECTED_TERMINATOR = DynamicCommandExceptionType {
            MessageComponentSerializer.message().serialize(Component.text("!!!: Unexpected terminator '$it'!"))
        }
    }

    protected data class ParsedComponent(
        val name: NamespacedKey,
        val value: Any,
        var start: Int,
        var end: Int,
        var complete: Boolean
    )

    protected abstract class ParsedNBTTag(
        var start:    Int,
        var end:      Int,
        var complete: Boolean
    )

    protected class ParsedNBTByteTag(
        val value: Byte,
        start:     Int,
        end:       Int,
        complete:  Boolean
    ) : ParsedNBTTag(start, end, complete) {
        override fun toString() = "ParsedNBTByteTag[$value]"
    }

    protected class ParsedNBTShortTag(
        val value: Short,
        start:     Int,
        end:       Int,
        complete:  Boolean
    ) : ParsedNBTTag(start, end, complete) {
        override fun toString() = "ParsedNBTShortTag[$value]"
    }

    protected class ParsedNBTIntTag(
        val value: Int,
        start:     Int,
        end:       Int,
        complete:  Boolean
    ) : ParsedNBTTag(start, end, complete) {
        override fun toString() = "ParsedNBTIntTag[$value]"
    }

    protected class ParsedNBTLongTag(
        val value: Long,
        start:     Int,
        end:       Int,
        complete:  Boolean
    ) : ParsedNBTTag(start, end, complete) {
        override fun toString() = "ParsedNBTLongTag[$value]"
    }

    protected class ParsedNBTFloatTag(
        val value: Float,
        start:     Int,
        end:       Int,
        complete:  Boolean
    ) : ParsedNBTTag(start, end, complete) {
        override fun toString() = "ParsedNBTFloatTag[$value]"
    }

    protected class ParsedNBTDoubleTag(
        val value: Double,
        start:     Int,
        end:       Int,
        complete:  Boolean
    ) : ParsedNBTTag(start, end, complete) {
        override fun toString() = "ParsedNBTDoubleTag[$value]"
    }

    protected class ParsedNBTStringTag(
        val value: String,
        start:     Int,
        end:       Int,
        complete:  Boolean
    ) : ParsedNBTTag(start, end, complete) {
        override fun toString() = "ParsedNBTStringTag[$value]"
    }

    protected class ParsedNBTBooleanTag(
        val value: Boolean,
        start:     Int,
        end:       Int,
        complete:  Boolean
    ) : ParsedNBTTag(start, end, complete) {
        override fun toString() = "ParsedNBTBooleanTag[$value]"
    }

    protected class ParsedNBTCompoundTag(
        var value: MutableMap<String, ParsedNBTTag>,
        start:     Int,
        end:       Int,
        complete:  Boolean
    ) : ParsedNBTTag(start, end, complete) {
        override fun toString() = "ParsedNBTCompoundTag[$value]"
        operator fun get(key: String) = value[key]
    }

    protected abstract class ParsedNBTIndexedTag(
        start:    Int,
        end:      Int,
        complete: Boolean
    ) : ParsedNBTTag(start, end, complete) {
        abstract val size: Int;
        abstract operator fun get(index: Int): ParsedNBTTag
    }

    protected open class ParsedNBTListTag(
        val value: MutableList<ParsedNBTTag>,
        val type:  KClass<out ParsedNBTTag>,
        start:     Int,
        end:       Int,
        complete:  Boolean
    ) : ParsedNBTIndexedTag(start, end, complete) {
        override val size: Int = value.size
        override fun get(index: Int) = value[index]
        override fun toString() = "ParsedNBTListTag[$value]"
    }

    protected class ParsedNBTByteArrayTag(
        value:     MutableList<ParsedNBTTag>,
        start:     Int,
        end:       Int,
        complete:  Boolean
    ) : ParsedNBTListTag(value, ParsedNBTByteArrayTag::class, start, end, complete) {
        override fun toString() = "ParsedNBTByteArrayTag[$value]"
    }

    protected class ParsedNBTIntArrayTag(
        value:     MutableList<ParsedNBTTag>,
        start:     Int,
        end:       Int,
        complete:  Boolean
    ) : ParsedNBTListTag(value, ParsedNBTIntTag::class, start, end, complete) {
        override fun toString() = "ParsedNBTIntArrayTag[$value]"
    }

    protected class ParsedNBTLongArrayTag(
        value:     MutableList<ParsedNBTTag>,
        start:     Int,
        end:       Int,
        complete:  Boolean
    ) : ParsedNBTListTag(value, ParsedNBTLongTag::class, start, end, complete) {
        override fun toString() = "ParsedNBTLongArrayTag[$value]"
    }

    protected abstract class ParsedNBTFragment(
        start:    Int,
        end:      Int
    ) : ParsedNBTTag(start, end, false)

    protected class ParsedNBTAttributeFragment(
        val name: String,
        start:    Int,
        end:      Int,
    ) : ParsedNBTFragment(start, end) {
        override fun toString() = "ParsedNBTAttributeFragment[$name]"
    }

    protected abstract class ParsedNBTPathElement {
        abstract fun execute(tag: ParsedNBTTag): ParsedNBTTag?
    }

    protected class ParsedNBTCompoundPathElement(val attribute: String) : ParsedNBTPathElement() {
        override fun execute(tag: ParsedNBTTag): ParsedNBTTag? {
            if (tag !is ParsedNBTCompoundTag)
                TODO("error for this")
            return tag.value[attribute]
        }

        override fun toString() = "CPath[$attribute]"
    }

    protected class ParsedNBTIndexPathElement(val index: Int) : ParsedNBTPathElement() {
        override fun execute(tag: ParsedNBTTag): ParsedNBTTag? {
            if (tag !is ParsedNBTIndexedTag)
                TODO("error for this")
            return tag[index]
        }

        override fun toString() = "IPath[$index]"
    }

    private enum class ParsedSNBTQuoteStyle {
        UNQUOTED, SINGLE, DOUBLE
    }
}
