package observable

import observable.server.ProfilingData
import com.mojang.blaze3d.platform.InputConstants
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import dev.architectury.event.events.common.CommandRegistrationEvent
import dev.architectury.event.events.client.ClientLifecycleEvent
import dev.architectury.event.events.client.ClientPlayerEvent
import dev.architectury.event.events.client.ClientTickEvent
import dev.architectury.event.events.common.LifecycleEvent
import dev.architectury.registry.client.keymappings.KeyMappingRegistry
import dev.architectury.utils.GameInstance
import net.minecraft.ChatFormatting
import net.minecraft.client.KeyMapping
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands.argument
import net.minecraft.commands.Commands.literal
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.TextComponent
import net.minecraft.network.chat.TranslatableComponent
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import observable.client.Overlay
import observable.client.ProfileScreen
import observable.net.BetterChannel
import observable.net.C2SPacket
import observable.net.S2CPacket
import observable.server.ContinuousPerfEval
import observable.server.Profiler
import observable.server.ServerSettings
import observable.server.TypeMap
import org.apache.logging.log4j.LogManager
import org.lwjgl.glfw.GLFW
import kotlin.system.exitProcess

object Observable {
    const val MOD_ID = "observable"

    val PROFILE_KEYBIND by lazy { KeyMapping("key.observable.profile",
        InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, "category.observable.keybinds") }

    val CHANNEL = BetterChannel(ResourceLocation("channel/observable"))
    val LOGGER = LogManager.getLogger("Observable")
    val PROFILER: Profiler by lazy { Profiler() }
    var RESULTS: ProfilingData? = null
    val PROFILE_SCREEN by lazy { ProfileScreen() }

    fun hasPermission(player: Player) =
        (GameInstance.getServer()?.playerList?.isOp(player.gameProfile) ?: true)
            || (GameInstance.getServer()?.isSingleplayer ?: false)

    @JvmStatic
    fun init() {
        CHANNEL.register { t: C2SPacket.InitTPSProfile, supplier ->
            val player = supplier.get().player
            if (!hasPermission(player)) {
                LOGGER.info("${player.name.contents} lacks permissions to start profiling")
                return@register
            }
            if (PROFILER.notProcessing) PROFILER.startRunning(t.duration, t.sample, supplier.get())
        }

        CHANNEL.register { t: C2SPacket.RequestTeleport, supplier ->
            val player = supplier.get().player
            if (!hasPermission(player)) {
                LOGGER.info("${player.name.contents} lacks permissions to teleport")
                return@register
            }
            GameInstance.getServer()?.allLevels?.filter {
                it.dimension().location().equals(t.level)
            }?.get(0)?.let { level ->
                LOGGER.info("Receive request from ${(player.name as TextComponent).text} in " +
                        "${player.level.dimension().location()} to go to ${level.dimension().location()}")
                Scheduler.SERVER.enqueue {
                    if (player.level != level) with(player.position()) {
                        (player as ServerPlayer).teleportTo(
                            level, x, y, z,
                            player.rotationVector.x, player.rotationVector.y
                        )
                    }
                    t.pos?.apply {
                        LOGGER.info("Moving to ($x, $y, $z) in ${t.level}")
                        player.moveTo(x.toDouble(), y.toDouble(), z.toDouble())
                    }
                    t.entityId?.let {
                        (level as Level).getEntity(it)?.position()?.apply {
                            LOGGER.info("Moving to ($x, $y, $z) in ${t.level}")
                            player.moveTo(this)
                        } ?: player.displayClientMessage(
                            TranslatableComponent("text.observable.entity_not_found", t.level.toString()), true)
                    }
                }
            }
        }

        CHANNEL.register { t: C2SPacket.RequestAvailability, supplier ->
            (supplier.get().player as? ServerPlayer)?.let {
                CHANNEL.sendToPlayer(
                    it,
                    if (hasPermission(it)) S2CPacket.Availability.Available
                    else S2CPacket.Availability.NoPermissions
                )
            }
        }

        CHANNEL.register { t: S2CPacket.ProfilingStarted, supplier ->
            PROFILE_SCREEN.action = ProfileScreen.Action.TPSProfilerRunning(t.endMillis)
            PROFILE_SCREEN.startBtn?.active = false
        }

        CHANNEL.register { t: S2CPacket.ProfilingCompleted, supplier ->
            PROFILE_SCREEN.action = ProfileScreen.Action.TPSProfilerCompleted
        }

        CHANNEL.register { t: S2CPacket.ProfilerInactive, supplier ->
            PROFILE_SCREEN.action = ProfileScreen.Action.DEFAULT
            PROFILE_SCREEN.startBtn?.active = true
        }

        CHANNEL.register { t: S2CPacket.ProfilingResult, supplier ->
            RESULTS = t.data
            PROFILE_SCREEN.apply {
                action = ProfileScreen.Action.DEFAULT
                startBtn?.active = true
                arrayOf(resultsBtn, overlayBtn).forEach { it.active = true }
            }
            val data = t.data.entities
            LOGGER.info("Received profiling result with ${data.size} entries")
            Overlay.loadSync()
        }
        
        CHANNEL.register { t: S2CPacket.Availability, supplier ->
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

        CHANNEL.register { t: S2CPacket.ConsiderProfiling, supplier ->
            Observable.LOGGER.info("Notifying player")
            val tps = "%.2f".format(t.tps)
            GameInstance.getClient().gui.chat.addMessage(TranslatableComponent("text.observable.suggest", tps,
                TranslatableComponent("text.observable.suggest_action").withStyle(ChatFormatting.UNDERLINE)
                    .withStyle {
                        it.withClickEvent(object : ClickEvent(null, "") {
                            override fun getAction(): Action? {
                                GameInstance.getClient().setScreen(PROFILE_SCREEN)
                                return null
                            }
                        })
                    }))
        }

        LifecycleEvent.SERVER_STARTED.register {
            val thread = Thread.currentThread()
            PROFILER.serverThread = thread
            ContinuousPerfEval.start()
            LOGGER.info("Registered thread ${thread.name}")
        }

        CommandRegistrationEvent.EVENT.register { dispatcher, dedicated ->
            val cmd = literal("observable")
                .requires { it.hasPermission(4) }
                    .executes {
                        it.source.sendSuccess(TextComponent(ServerSettings.toString()), false)
                        1
                    }
                .then(literal("set").let {
                    ServerSettings::class.java.declaredFields.fold(it) { setCmd, field ->
                        val argType = TypeMap[field.type] ?: return@fold setCmd
                        setCmd.then(literal(field.name).then(argument("newVal", argType())
                            .executes { ctx ->
                                try {
                                    field.isAccessible = true
                                    field.set(ServerSettings, ctx.getArgument("newVal", field.type))
                                    1
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    ctx.source.sendFailure(TextComponent("Error setting value\n${e.toString()}"))
                                    0
                                }
                            }))
                    }
                })

            dispatcher.register(cmd)

        }
    }

    @JvmStatic
    fun clientInit() {
        KeyMappingRegistry.register(PROFILE_KEYBIND)

        ClientTickEvent.CLIENT_POST.register {
            if (PROFILE_KEYBIND.consumeClick()) {
                it.setScreen(PROFILE_SCREEN)
            }
        }

        ClientLifecycleEvent.CLIENT_LEVEL_LOAD.register {
            Overlay.loadSync(it)
        }

        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register {
            RESULTS = null
            PROFILE_SCREEN.action = ProfileScreen.Action.UNAVAILABLE
        }
    }
}