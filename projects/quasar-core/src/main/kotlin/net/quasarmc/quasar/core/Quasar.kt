package net.quasarmc.quasar.core

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.minimessage.MiniMessage
import net.quasarmc.quasar.api.registration.RegistrationManager
import net.quasarmc.quasar.api.registration.events.RegistryRegistrationEvent
import net.quasarmc.quasar.api.registration.registries.CustomRegistryRegistry
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin
import io.papermc.paper.plugin.bootstrap.BootstrapContext
import io.papermc.paper.plugin.bootstrap.PluginBootstrap
import io.papermc.paper.plugin.bootstrap.PluginProviderContext
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import net.kyori.adventure.text.ComponentLike
import net.minecraft.commands.CommandSource
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.permissions.Permission
import net.minecraft.server.permissions.PermissionSet
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import net.quasarmc.quasar.api.addon.AddonManager
import net.quasarmc.quasar.api.addon.Addon
import net.quasarmc.quasar.api.addon.registries.AddonRegistry
import net.quasarmc.quasar.api.item.CustomItemManager
import net.quasarmc.quasar.core.administration.commands.CommandObject
import net.quasarmc.quasar.api.item.commands.GiveCommand
import net.quasarmc.quasar.api.item.registries.CustomItemBehaviorTypeRegistry
import net.quasarmc.quasar.api.item.registries.CustomItemRegistry
import net.quasarmc.quasar.api.toNMSKey
import net.quasarmc.quasar.core.administration.commands.QuasarCommand
import net.quasarmc.quasar.core.administration.fakeop.FakeOperatorPermissionsManager
import net.quasarmc.quasar.core.registration.CoreCustomItems
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.command.CommandSender
import org.bukkit.craftbukkit.entity.CraftPlayer
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.event.server.PluginEnableEvent

@Suppress("UnstableApiUsage")
class QuasarPluginBootstrap : PluginBootstrap {
    /**
     * The core addon for Quasar.
     */
    val core = QuasarCoreAddon();

    override fun bootstrap(context: BootstrapContext) {
        // add our datapack
        context.lifecycleManager.registerEventHandler(LifecycleEvents.DATAPACK_DISCOVERY.newHandler {
            it.registrar().discoverPack(this.javaClass.getResource("/datapack")!!.toURI(), "data");
        })

        // register the core addon
        AddonManager.register(core)
    }

    override fun createPlugin(context: PluginProviderContext): JavaPlugin {
        return QuasarPlugin(core)
    }
}

class QuasarPlugin(
    val addon: QuasarCoreAddon
) : JavaPlugin() {
    companion object {
        lateinit var plugin: QuasarPlugin;

        val LOGGER get() = plugin.logger;
    };

    override fun onLoad() {
        plugin = this;

        addon.attachPlugin(this)
    }

    override fun onEnable() {
        LOGGER.info("================== Q U A S A R ==================")
        LOGGER.info("Quasar version ${QuasarBuildMetadata.version}")
        LOGGER.info("Compiled from git commit ${QuasarBuildMetadata.commit}")
        LOGGER.info(QuasarBuildMetadata.sourceURL)
        LOGGER.info("=================================================")

        CustomItemBehaviorTypeRegistry.registerEventHandlers(this)
        CustomItemRegistry.registerEventHandlers(this) // depends on customitembehaviortyperegistry

        server.pluginManager.registerEvents(AddonManager, this)
        server.pluginManager.registerEvents(CoreCustomItems.loader, this)
        server.pluginManager.registerEvents(CustomItemManager, this)
        server.pluginManager.registerEvents(FakeOperatorPermissionsManager, this)
        server.pluginManager.registerEvents(object : Listener {
            // TODO: This should be handled by the core addon.
            @EventHandler
            fun onRegisterRegistries(ev: RegistryRegistrationEvent) {
                ev.register("quasar", "root", CustomRegistryRegistry);
                ev.register("quasar", "addons", AddonRegistry)
                ev.register("quasar", "items", CustomItemRegistry)
                ev.register("quasar", "item_behavior_types", CustomItemBehaviorTypeRegistry)
            }
        }, this)

        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) {
            val registry = it.registrar();

            CommandObject.registerCommands(registry)

            // TODO(XWH): command refactor
            registry.register(Commands.literal("sudo")
                .then(Commands.argument("player", ArgumentTypes.player())
                    .then(Commands.literal("say").then(
                        Commands.argument("text", StringArgumentType.greedyString())
                            .executes { ctx ->
                                val resolver = ctx.getArgument("player", PlayerSelectorArgumentResolver::class.java)
                                val player = resolver.resolve(ctx.source)[0];

                                player.chat(StringArgumentType.getString(ctx, "text"))

                                return@executes Command.SINGLE_SUCCESS
                            }))
                    .then(Commands.literal("execute").redirect(registry.dispatcher.root) { ctx ->
                        val resolver = ctx.getArgument("player", PlayerSelectorArgumentResolver::class.java)
                        val player = resolver.resolve(ctx.source)[0];

                        val source = object : CommandSource {
                            override fun sendSystemMessage(msg: net.minecraft.network.chat.Component)
                                = (player as CraftPlayer).handle.sendSystemMessage(msg)

                            override fun acceptsSuccess(): Boolean = true
                            override fun acceptsFailure(): Boolean = true
                            override fun shouldInformAdmins(): Boolean = false

                            override fun getBukkitSender(p0: net.minecraft.commands.CommandSourceStack): CommandSender
                                = player
                        }

                        net.minecraft.commands.CommandSourceStack(
                            source,
                            player.location.let { p -> Vec3(p.x, p.y, p.z) },
                            player.location.rotation.let { p -> Vec2(p.pitch(), p.yaw()) },
                            (player as CraftPlayer).handle.level(),
                            player.handle.permissions(),
                            MinecraftServer.getServer(),
                            player.handle.livingEntity
                        )
                    }))
                .build())
        }
    }

    /**
     * There's no easy way to know when all addons have been enabled and have attached listeners to Quasar's events,
     * as there's no startup event that runs after onEnable but before things we care about, like world loading.
     *
     * This *should* be called when all addons are ready to do things like setting up registries.
     *
     * The method gets called from [AddonManager.onPluginEnable]
     */
    internal fun finalizeAPIStartup() {
        RegistrationManager.reload()

        // TODO: Make this an event???
        // This control flow is shit.
        AddonManager.finalizeInitialization();
    }
}

/**
 * Core addon for Quasar, containing all built-in content.
 */
class QuasarCoreAddon : Addon<QuasarPlugin>() {
    override val identifier  = NamespacedKey("quasar", "quasar.core")
    override val name        = "Quasar Core"
    override val description = "Quasar core content"
    override val author      = "Quasar Contributors"
    override val version     = QuasarBuildMetadata.version
    override val sourceURL   = QuasarBuildMetadata.sourceURL
}
