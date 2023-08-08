package observable.server

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.context.CommandContext
import dev.architectury.utils.GameInstance
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.DimensionArgument
import net.minecraft.commands.arguments.GameProfileArgument
import net.minecraft.commands.arguments.coordinates.Vec3Argument
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.Vec3
import observable.Observable
import observable.net.S2CPacket

val OBSERVABLE_COMMAND
    get() =
        Commands.literal("observable")
            .requires { it.hasPermission(4) }
            .executes {
                it.source.sendSuccess(Component.literal(ServerSettings.toString()), false)
                1
            }
            .then(
                Commands.literal("run")
                    .then(
                        Commands.argument("duration", IntegerArgumentType.integer()).executes { ctx ->
                            val duration = IntegerArgumentType.getInteger(ctx, "duration")
                            Observable.PROFILER.runWithDuration(ctx.source.player, duration, false)
                            ctx.source.sendSuccess(
                                Component.translatable("text.observable.profile_started", duration),
                                false
                            )
                            1
                        }
                    )
            )
            .then(
                Commands.literal("allow")
                    .then(
                        Commands.argument("player", GameProfileArgument.gameProfile()).executes { ctx ->
                            GameProfileArgument.getGameProfiles(ctx, "player").forEach { player ->
                                ServerSettings.allowedPlayers.add(player.id.toString())
                                GameInstance.getServer()?.playerList?.getPlayer(player.id)?.let {
                                    Observable.CHANNEL.sendToPlayer(it, S2CPacket.Availability.Available)
                                }
                            }
                            ServerSettings.sync()
                            1
                        }
                    )
            )
            .then(
                Commands.literal("deny")
                    .then(
                        Commands.argument("player", GameProfileArgument.gameProfile()).executes { ctx ->
                            GameProfileArgument.getGameProfiles(ctx, "player").forEach { player ->
                                ServerSettings.allowedPlayers.remove(player.id.toString())
                                GameInstance.getServer()?.playerList?.getPlayer(player.id)?.let {
                                    Observable.CHANNEL.sendToPlayer(it, S2CPacket.Availability.NoPermissions)
                                }
                            }
                            ServerSettings.sync()
                            1
                        }
                    )
            )
            .then(
                Commands.literal("set").let {
                    ServerSettings::class.java.declaredFields.fold(it) { setCmd, field ->
                        val argType = TypeMap[field.type] ?: return@fold setCmd
                        setCmd.then(
                            Commands.literal(field.name)
                                .then(
                                    Commands.argument("newVal", argType()).executes { ctx ->
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
                Commands.literal("tp")
                    .then(
                        Commands.argument("dim", DimensionArgument.dimension())
                            .then(
                                Commands.literal("entity")
                                    .then(
                                        Commands.argument("id", IntegerArgumentType.integer()).executes { ctx ->
                                            val level = DimensionArgument.getDimension(ctx, "dim")
                                            val id = IntegerArgumentType.getInteger(ctx, "id")
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
                                Commands.literal("position")
                                    .then(
                                        Commands.argument("pos", Vec3Argument.vec3()).executes { ctx ->
                                            teleport(ctx, Vec3Argument.getVec3(ctx, "pos"))
                                            1
                                        }
                                    )
                            )
                    )
            )

fun teleport(ctx: CommandContext<CommandSourceStack>, pos: Vec3) {
    val player = ctx.source.playerOrException
    val level = DimensionArgument.getDimension(ctx, "dim")

    player.teleportTo(pos.x, pos.y, pos.z)
    if (level == player.level) {
        player.connection.teleport(pos.x, pos.y, pos.z, 0F, 0F, setOf())
    } else {
        player.teleportTo(level, pos.x, pos.y, pos.z, 0F, 0F)
    }
    Observable.LOGGER.info("Moved ${player.gameProfile.name} to (${pos.x}, ${pos.y}, ${pos.z}) in $level")
}
