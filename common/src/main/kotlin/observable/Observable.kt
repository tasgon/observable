package observable

import ProfilingData
import com.mojang.blaze3d.platform.InputConstants
import me.shedaniel.architectury.event.events.client.ClientLifecycleEvent
import me.shedaniel.architectury.event.events.client.ClientTickEvent
import me.shedaniel.architectury.registry.KeyBindings
import me.shedaniel.architectury.utils.GameInstance
import net.minecraft.client.KeyMapping
import net.minecraft.network.chat.TextComponent
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import observable.client.Overlay
import observable.client.ProfileScreen
import observable.net.BetterChannel
import observable.net.C2SPacket
import observable.net.S2CPacket
import observable.server.Profiler
import org.apache.logging.log4j.LogManager
import org.lwjgl.glfw.GLFW

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
            if (PROFILER.notProcessing) PROFILER.startRunning(t.duration, supplier.get())
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
                if (player.level != level) with(player.position()) {
                    (player as ServerPlayer).teleportTo(level, x, y, z,
                        player.rotationVector.x, player.rotationVector.y)
                }
                t.pos?.apply {
                    LOGGER.info("Moving to ($x, $y, $z) in ${t.level}")
                    player.moveTo(x.toDouble(), y.toDouble(), z.toDouble())
                }
                t.entityId?.let {
                    (level as Level).getEntity(it)?.position()?.apply {
                        LOGGER.info("Moving to ($x, $y, $z) in ${t.level}")
                        player.moveTo(this)
                    }
                }
            }
        }

        CHANNEL.register { t: S2CPacket.ProfilingStarted, supplier ->
            PROFILE_SCREEN.action = ProfileScreen.Action.TPSProfilerRunning(t.endNanos)
        }

        CHANNEL.register { t: S2CPacket.ProfilingResult, supplier ->
            RESULTS = t.data
            PROFILE_SCREEN.apply {
                action = ProfileScreen.Action.DEFAULT
                arrayOf(resultsBtn, overlayBtn).forEach { it.active = true }
            }
            val data = t.data.entities
            LOGGER.info("Received profiling result with ${data.size} entries")
            synchronized(Overlay) {
                Overlay.load()
            }
        }
    }

    @JvmStatic
    fun clientInit() {
        KeyBindings.registerKeyBinding(PROFILE_KEYBIND)

        ClientTickEvent.CLIENT_POST.register {
            if (PROFILE_KEYBIND.consumeClick()) {
                it.setScreen(PROFILE_SCREEN)
            }
        }

        ClientLifecycleEvent.CLIENT_WORLD_LOAD.register {
            PROFILE_SCREEN.action = ProfileScreen.Action.DEFAULT
            synchronized(Overlay) {
                Overlay.load()
            }
        }
    }
}