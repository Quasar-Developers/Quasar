package net.quasarmc.quasar.core.administration.commands

import com.mojang.brigadier.Command
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.minimessage.MiniMessage
import net.quasarmc.quasar.api.addon.registries.AddonRegistry
import net.quasarmc.quasar.api.registration.RegistrationManager
import net.quasarmc.quasar.api.registration.commands.arguments.CustomNamespacedKeyArgument
import net.quasarmc.quasar.api.registration.registries.CustomRegistryRegistry
import net.quasarmc.quasar.core.QuasarBuildMetadata
import org.bukkit.NamespacedKey

/**
 * Quasar administration command.
 *
 * Registered in QuasarPlugin
 */
object QuasarCommand {
    val command: LiteralCommandNode<CommandSourceStack> = Commands.literal("quasar")
        .then(Commands.literal("version").executes(QuasarCommand::version))
        .then(Commands.literal("addon")
            .then(Commands.literal("list").executes(QuasarCommand::addonList))
            .then(Commands.literal("show")
                .then(Commands.argument("identifier", CustomNamespacedKeyArgument(AddonRegistry, "quasar"))
                    .executes(QuasarCommand::addonShow))))
        .then(Commands.literal("registry")
            .then(Commands.literal("reload").executes(QuasarCommand::registryReload))
            .then(Commands.literal("dump")
                .then(Commands.argument("identifier", CustomNamespacedKeyArgument(CustomRegistryRegistry, "quasar"))
                    .executes(QuasarCommand::registryDump)))
            .then(Commands.literal("show")
                .then(Commands.argument("identifier", CustomNamespacedKeyArgument(CustomRegistryRegistry, "quasar"))
                    .executes(QuasarCommand::registryShow))))
        .build()

    private fun version(context: CommandContext<CommandSourceStack>): Int {
        context.source.sender.sendRichMessage("""
            <#38e5e4>Quasar</#38e5e4> ${QuasarBuildMetadata.version} <c:${
                if (QuasarBuildMetadata.commit.endsWith("-dirty")) { "red" } else { "green" }
            }>${QuasarBuildMetadata.commit}</c>
            Quasar is licensed under the <green><click:open_url:'https://github.com/Quasar-Developers/Quasar/blob/master/LICENSE.md'>[GNU Affero General Public License 3.0]</click></green>, you are free to modify and distribute it.
            You can access the plugin source code <green><click:open_url:'${QuasarBuildMetadata.sourceURL}'>[here]</click></green>.
        """.trimIndent() +
            if (QuasarBuildMetadata.sourceURL != "https://github.com/Quasar-Developers/Quasar/") {
                "\nYou can access the original source code <green><click:open_url:'https://github.com/Quasar-Developers/Quasar/'>[here]</click></green>."
            } else { "" }
        )

        return Command.SINGLE_SUCCESS
    }

    private fun addonList(context: CommandContext<CommandSourceStack>): Int {
        var message = "Loaded addons:"

        val iterator = AddonRegistry.iterator()
        while (iterator.hasNext()) {
            val (_, addon) = iterator.next()

            message += "\n"
            message += if (iterator.hasNext()) { "├" } else { "└" }
            message += " <green><hover:show_text:'${addon.identifier}'><click:run_command:'/quasar addon show ${addon.identifier}'>[${addon.name}]</click></hover></green>"
            message += " ${addon.version}"
            message += " by ${addon.author}"
        }

        context.source.sender.sendRichMessage(message)
        return Command.SINGLE_SUCCESS
    }

    private fun addonShow(context: CommandContext<CommandSourceStack>): Int {
        val addon = AddonRegistry[context.getArgument("identifier", NamespacedKey::class.java)]

        context.source.sender.sendRichMessage("""
            ${addon.name} <green><click:open_url:'${addon.sourceURL}'>[Source Code]</click></green>
            ├ Addon ID ${addon.identifier}
            ├ By ${addon.author}
            └ Version ${addon.version}
            -----------------------------------------------------
            ${addon.description}
            -----------------------------------------------------
        """.trimIndent())

        return Command.SINGLE_SUCCESS
    }

    private fun registryReload(context: CommandContext<CommandSourceStack>): Int {
        context.source.sender.sendMessage("Reloading!")
        RegistrationManager.reload()
        context.source.sender.sendRichMessage("Done. <c:green><click:run_command:'/quasar registry dump quasar:root'>[Dump root registry]</click></c>")

        return Command.SINGLE_SUCCESS
    }

    private fun registryDump(context: CommandContext<CommandSourceStack>): Int {
        val registryIdentifier = context.getArgument("identifier", NamespacedKey::class.java)
        val registry = CustomRegistryRegistry[registryIdentifier]

        var message = "Contents of <c:green><click:run_command:'/quasar registry show ${registryIdentifier}'>[${registryIdentifier}]</click></c>:"

        val iterator = registry.iterator()
        while (iterator.hasNext()) {
            val (identifier, value) = iterator.next()

            message += "\n"
            message += if (iterator.hasNext()) { "├" } else { "└" }
            message += " $identifier = <hover:show_text:'${value!!.javaClass.name}'>${value.javaClass.simpleName}</hover>"
        }

        context.source.sender.sendRichMessage(message)
        return Command.SINGLE_SUCCESS
    }

    private fun registryShow(context: CommandContext<CommandSourceStack>): Int {
        val registryIdentifier = context.getArgument("identifier", NamespacedKey::class.java)
        val registry = CustomRegistryRegistry[registryIdentifier]

        context.source.sender.sendRichMessage("""
            Registry $registryIdentifier : <hover:show_text:'${registry.javaClass.name}'>${registry.javaClass.simpleName}</hover> <c:green><click:run_command:'/quasar registry dump ${registryIdentifier}'>[Dump]</click></c>
            └ ${registry.count()} items.
        """.trimIndent())

        return Command.SINGLE_SUCCESS
    }
}
