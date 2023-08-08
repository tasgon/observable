package observable

import com.mojang.blaze3d.platform.InputConstants
import com.mojang.brigadier.arguments.IntegerArgumentType.getInteger
import com.mojang.brigadier.arguments.IntegerArgumentType.integer
import com.mojang.brigadier.context.CommandContext
import dev.architectury.event.events.client.ClientLifecycleEvent
import dev.architectury.event.events.client.ClientPlayerEvent
import dev.architectury.event.events.client.ClientTickEvent
import dev.architectury.event.events.common.CommandRegistrationEvent
import dev.architectury.event.events.common.LifecycleEvent
import dev.architectury.registry.client.keymappings.KeyMappingRegistry
import dev.architectury.utils.GameInstance
import net.minecraft.ChatFormatting
import net.minecraft.client.KeyMapping
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands.argument
import net.minecraft.commands.Commands.literal
import net.minecraft.commands.arguments.DimensionArgument.dimension
import net.minecraft.commands.arguments.DimensionArgument.getDimension
import net.minecraft.commands.arguments.GameProfileArgument.gameProfile
import net.minecraft.commands.arguments.GameProfileArgument.getGameProfiles
import net.minecraft.commands.arguments.coordinates.Vec3Argument.getVec3
import net.minecraft.commands.arguments.coordinates.Vec3Argument.vec3
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import observable.client.Overlay
import observable.client.ProfileExporter
import observable.client.ProfileScreen
import observable.net.BetterChannel
import observable.net.C2SPacket
import observable.net.S2CPacket
import observable.server.Profiler
import observable.server.ProfilingData
import observable.server.ServerSettings
import observable.server.TypeMap
import observable.util.MOD_URL_COMPONENT
import observable.util.Marker
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.lwjgl.glfw.GLFW

object Observable {
    const val MOD_ID = "observable"

    val PROFILE_KEYBIND by lazy {
        KeyMapping(
            "key.observable.profile",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "category.observable.keybinds"
        )
    }

    private val CLIENT_CHAT get() = GameInstance.getClient().gui.chat

    val CHANNEL = BetterChannel(ResourceLocation("channel/observable"))
    val LOGGER: Logger = LogManager.getLogger("Observable")
    val PROFILER: Profiler by lazy { Profiler() }
    var RESULTS: ProfilingData? = null
    val PROFILE_SCREEN by lazy { ProfileScreen() }

    fun hasPermission(player: Player): Boolean {
        if (ServerSettings.allPlayersAllowed) return true
        if (ServerSettings.allowedPlayers.contains(player.gameProfile.id.toString())) return true
        if (GameInstance.getServer()?.playerList?.isOp(player.gameProfile) != false) return true
        return GameInstance.getServer()?.isSingleplayer ?: false
    }

    @JvmStatic
    fun init() {
        CHANNEL.register { t: C2SPacket.InitTPSProfile, supplier ->
            val player = supplier.get().player
            if (!hasPermission(player)) {
                LOGGER.info("${player.name.contents} lacks permissions to start profiling")
                return@register
            }
            if (PROFILER.notProcessing) {
                PROFILER.runWithDuration(player as? ServerPlayer, t.duration, t.sample)
            }
            LOGGER.info("${player.gameProfile.name} started profiler for ${t.duration} s")
        }

        CHANNEL.register { _: C2SPacket.RequestAvailability, supplier ->
            (supplier.get().player as? ServerPlayer)?.let {
                CHANNEL.sendToPlayer(
                    it,
                    if (hasPermission(it)) {
                        S2CPacket.Availability.Available
                    } else {
                        S2CPacket.Availability.NoPermissions
                    }
                )
            }
        }

        CHANNEL.register { t: S2CPacket.ProfilingStarted, _ ->
            PROFILE_SCREEN.action = ProfileScreen.Action.TPSProfilerRunning(t.endMillis)
            PROFILE_SCREEN.startBtn?.active = false
        }

        CHANNEL.register { _: S2CPacket.ProfilingCompleted, _ ->
            PROFILE_SCREEN.action = ProfileScreen.Action.TPSProfilerCompleted
        }

        CHANNEL.register { _: S2CPacket.ProfilerInactive, _ ->
            PROFILE_SCREEN.action = ProfileScreen.Action.DEFAULT
            PROFILE_SCREEN.startBtn?.active = true
        }

        CHANNEL.register { t: S2CPacket.ProfilingResult, _ ->
            RESULTS = t.data
            PROFILE_SCREEN.apply {
                action = ProfileScreen.Action.DEFAULT
                startBtn?.active = true
                arrayOf(editField, overlayBtn).forEach { it.active = true }
            }
            Overlay.loadSync()

            if (t.link != null) {
                val linkText = Component.literal(t.link).withStyle(ChatFormatting.UNDERLINE).withStyle {
                    it.withClickEvent(ClickEvent(ClickEvent.Action.OPEN_URL, t.link))
                }
                val msg = Component.translatable("text.observable.profile_uploaded", linkText)
                CLIENT_CHAT.addMessage(msg)
            } else {
                CLIENT_CHAT.addMessage(Component.translatable("text.observable.upload_failed"))
                CLIENT_CHAT.addMessage(
                    Component.translatable("text.observable.profile_saved", ProfileExporter.export(t.data))
                )
                CLIENT_CHAT.addMessage(Component.translatable("text.observable.after_save", MOD_URL_COMPONENT))
            }
        }

        CHANNEL.register { t: S2CPacket.Availability, _ ->
            when (t) {
                S2CPacket.Availability.Available -> {
                    PROFILE_SCREEN.action = ProfileScreen.Action.DEFAULT
                    PROFILE_SCREEN.startBtn?.active = true
                }
                S2CPacket.Availability.NoPermissions -> {
                    PROFILE_SCREEN.action = ProfileScreen.Action.NO_PERMISSIONS
                    PROFILE_SCREEN.startBtn?.active = false
                }
            }
        }

        LifecycleEvent.SERVER_STARTED.register {
            val thread = Thread.currentThread()
            PROFILER.serverThread = thread
            LOGGER.info("Registered thread ${thread.name}")
        }

        CommandRegistrationEvent.EVENT.register { dispatcher, _, _ ->
            val cmd =
                literal("observable")
                    .requires { it.hasPermission(4) }
                    .executes {
                        it.source.sendSuccess(Component.literal(ServerSettings.toString()), false)
                        1
                    }
                    .then(
                        literal("run")
                            .then(
                                argument("duration", integer()).executes { ctx ->
                                    val duration = getInteger(ctx, "duration")
                                    PROFILER.runWithDuration(ctx.source.player, duration, false)
                                    ctx.source.sendSuccess(
                                        Component.translatable("text.observable.profile_started", duration),
                                        false
                                    )
                                    1
                                }
                            )
                    )
                    .then(
                        literal("allow")
                            .then(
                                argument("player", gameProfile()).executes { ctx ->
                                    getGameProfiles(ctx, "player").forEach { player ->
                                        ServerSettings.allowedPlayers.add(player.id.toString())
                                        GameInstance.getServer()?.playerList?.getPlayer(player.id)?.let {
                                            CHANNEL.sendToPlayer(it, S2CPacket.Availability.Available)
                                        }
                                    }
                                    ServerSettings.sync()
                                    1
                                }
                            )
                    )
                    .then(
                        literal("deny")
                            .then(
                                argument("player", gameProfile()).executes { ctx ->
                                    getGameProfiles(ctx, "player").forEach { player ->
                                        ServerSettings.allowedPlayers.remove(player.id.toString())
                                        GameInstance.getServer()?.playerList?.getPlayer(player.id)?.let {
                                            CHANNEL.sendToPlayer(it, S2CPacket.Availability.NoPermissions)
                                        }
                                    }
                                    ServerSettings.sync()
                                    1
                                }
                            )
                    )
                    .then(
                        literal("set").let {
                            ServerSettings::class.java.declaredFields.fold(it) { setCmd, field ->
                                val argType = TypeMap[field.type] ?: return@fold setCmd
                                setCmd.then(
                                    literal(field.name)
                                        .then(
                                            argument("newVal", argType()).executes { ctx ->
                                                try {
                                                    field.isAccessible = true
                                                    field.set(
                                                        ServerSettings,
                                                        ctx.getArgument("newVal", field.type)
                                                    )
                                                    ServerSettings.sync()
                                                    1
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                    ctx.source.sendFailure(
                                                        Component.literal("Error setting value\n$e")
                                                    )
                                                    0
                                                }
                                            }
                                        )
                                )
                            }
                        }
                    )
                    .then(
                        literal("tp")
                            .then(
                                argument("dim", dimension())
                                    .then(
                                        literal("entity")
                                            .then(
                                                argument("id", integer()).executes { ctx ->
                                                    val level = getDimension(ctx, "dim")
                                                    val id = getInteger(ctx, "id")
                                                    val pos =
                                                        level.getEntity(id)?.position()
                                                            ?: run {
                                                                ctx.source.sendFailure(
                                                                    Component.translatable(
                                                                        "text.observable.entity_not_found"
                                                                    )
                                                                )
                                                                return@executes 0
                                                            }
                                                    teleport(ctx, pos)
                                                    1
                                                }
                                            )
                                    )
                                    .then(
                                        literal("position")
                                            .then(
                                                argument("pos", vec3()).executes { ctx ->
                                                    teleport(ctx, getVec3(ctx, "pos"))
                                                    1
                                                }
                                            )
                                    )
                            )
                    )

            dispatcher.register(cmd)
        }
    }

    fun teleport(ctx: CommandContext<CommandSourceStack>, pos: Vec3) {
        val player = ctx.source.playerOrException
        val level = getDimension(ctx, "dim")

        player.teleportTo(pos.x, pos.y, pos.z)
        if (level == player.level) {
            player.connection.teleport(pos.x, pos.y, pos.z, 0F, 0F, setOf())
        } else {
            player.teleportTo(level, pos.x, pos.y, pos.z, 0F, 0F)
        }
        LOGGER.info("Moved ${player.gameProfile.name} to (${pos.x}, ${pos.y}, ${pos.z}) in $level")
    }

    @JvmStatic
    fun clientInit() {
        KeyMappingRegistry.register(PROFILE_KEYBIND)

        ClientTickEvent.CLIENT_POST.register {
            if (PROFILE_KEYBIND.consumeClick()) {
                it.setScreen(PROFILE_SCREEN)
            }
        }

        ClientLifecycleEvent.CLIENT_LEVEL_LOAD.register { level ->
            Overlay.loadSync(level)
            Marker("observable_announce").mark {
                CLIENT_CHAT.addMessage(
                    Component.translatable(
                        "text.observable.announce",
                        MOD_URL_COMPONENT
                    )
                )
            }
        }

        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register {
            RESULTS = null
            PROFILE_SCREEN.action = ProfileScreen.Action.UNAVAILABLE
        }
    }
}
