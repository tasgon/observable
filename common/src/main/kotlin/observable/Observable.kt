package observable

import dev.architectury.event.events.common.CommandRegistrationEvent
import dev.architectury.event.events.common.LifecycleEvent
import net.minecraft.resources.Identifier
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import observable.net.BetterChannel
import observable.net.C2SPacket
import observable.net.S2CPacket
import observable.server.OBSERVABLE_COMMAND
import observable.server.Profiler
import observable.server.ProfilingData
import observable.server.ServerSettings
import observable.util.Constants
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

object Observable {
    const val MOD_ID = "observable"
    val LOGGER: Logger = LogManager.getLogger("Observable")
    val CHANNEL by lazy { BetterChannel(Identifier.fromNamespaceAndPath(MOD_ID, "channel")) }
    val PROFILER by lazy { Profiler() }
    var SERVER_INSTANCE: MinecraftServer? = null
    var RESULTS: ProfilingData? = null

    fun clearResults() {
        RESULTS = null
    }

    fun hasPermission(player: ServerPlayer): Boolean {
        if (ServerSettings.allPlayersAllowed) return true
        if (ServerSettings.allowedPlayers.contains(player.gameProfile.id.toString())) return true
        val server = player.level().server
        if (server.isSingleplayerOwner(player.nameAndId())) return true
        return server.playerList.isOp(player.nameAndId())
    }

    @JvmStatic
    fun init() {
        LOGGER.info("Starting Observable Common-side initialization...")

        Constants.load()
        PROFILER.init()
        observable.server.Remapper.init()

        CHANNEL.register { t: C2SPacket.InitTPSProfile, ctx ->
            val player = ctx.player as? ServerPlayer ?: return@register
            if (!hasPermission(player)) {
                LOGGER.info("${player.name.string} lacks permissions to start profiling")
                return@register
            }
            if (PROFILER.notProcessing) {
                PROFILER.runWithDuration(player, t.duration, t.sample)
            }
            LOGGER.info("${player.gameProfile.name} started profiler for ${t.duration} s")
        }

        CHANNEL.register { _: C2SPacket.RequestAvailability, ctx ->
            (ctx.player as? ServerPlayer)?.let {
                CHANNEL.sendToPlayer(
                    it,
                    if (hasPermission(it)) S2CPacket.Availability.Available else S2CPacket.Availability.NoPermissions,
                )
            }
        }

        LifecycleEvent.SERVER_STARTED.register { server ->
            SERVER_INSTANCE = server
        }

        LifecycleEvent.SERVER_STOPPING.register {
            SERVER_INSTANCE = null
        }

        CommandRegistrationEvent.EVENT.register { dispatcher, _, _ ->
            dispatcher.register(OBSERVABLE_COMMAND)
        }
    }

    @JvmStatic
    fun onInitialize() = init()
}
