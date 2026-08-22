package net.quasarmc.quasar.api.item.commands

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver
import io.papermc.paper.datacomponent.DataComponentTypes
import net.quasarmc.quasar.api.item.commands.arguments.CustomItemStackArgument
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * Give item to player :goog;
 */
object GiveCommand {
    val command: LiteralCommandNode<CommandSourceStack> = Commands.literal("give")
        .then(Commands.argument("player", ArgumentTypes.players())
            .then(Commands.argument("count", IntegerArgumentType.integer(1, 99))
                .then(Commands.argument("item", CustomItemStackArgument())
                    .executes {
                        val item = it.getArgument("item", ItemStack::class.java)
                        item.amount = it.getArgument("count", Int::class.java)

                        run(it, it.getArgument("player", PlayerSelectorArgumentResolver::class.java), item)
                    })))
        .build()

    fun run(context: CommandContext<CommandSourceStack>, selector: PlayerSelectorArgumentResolver, item: ItemStack): Int {
        if (item.amount > item.maxStackSize) {
            context.source.sender.sendRichMessage("<red><b>!!!:</b> Count exceeds max stack size of ${item.maxStackSize}")
            return 0
        }

        val players = selector.resolve(context.source)
        for (player in players) {
            // TODO: Send command feedback to other admins
            player.give(item)
        }

        return Command.SINGLE_SUCCESS
    }
}
