package net.quasarmc.quasar.core.administration.commands

import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.annotation.DoNotUse
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands

/**
 * Every `CommandObject` to be registered.
 * Registration occurs in `registerCommands`.
 *
 * You may use this to add/remove commands before registration.
 */
var COMMANDS: MutableList<CommandObject> = mutableListOf(
    // add core commands here
    QuasarCommand,
)

// (Probably use commands on the datapack if we're having)one)
/**
 * You should usually extend this (and add it to `COMMANDS`) when creating new commands.
 */
abstract class CommandObject{
    abstract val command: LiteralCommandNode<CommandSourceStack>

    /**
     * Ran when the command is registered.
     */
    fun onRegister() {}

    companion object {
        /**
         * Used by QuasarPlugin to register commands.
         */
        @DoNotUse
        fun registerCommands(registrar: Commands) {
            for (command in COMMANDS) {
                registrar.register(command.command)
                command.onRegister()
            }
        }
    }
}
