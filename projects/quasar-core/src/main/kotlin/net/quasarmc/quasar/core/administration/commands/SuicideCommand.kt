package net.quasarmc.quasar.core.administration.commands

import com.mojang.brigadier.Command
import io.papermc.paper.command.brigadier.Commands
import org.bukkit.entity.Damageable

object SuicideCommand : CommandObject() {
    override val command = Commands.literal("suicide")
        .requires { it.sender.hasPermission("quasar.core.admin.command.suicide") }
        .executes { ctx ->
            val target = ctx.source.entityOrThrow

            if (target is Damageable)
                target.kill()
            else
                target.remove()

            return@executes Command.SINGLE_SUCCESS
        }
        .build()
}
