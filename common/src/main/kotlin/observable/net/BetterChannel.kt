package observable.net

import kotlinx.serialization.*
import kotlinx.serialization.protobuf.ProtoBuf
import me.shedaniel.architectury.networking.NetworkChannel
import me.shedaniel.architectury.networking.NetworkManager
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import observable.Observable
import java.util.function.Supplier

class BetterChannel(val id: ResourceLocation) {
    var rawChannel = NetworkChannel.create(id)

    inline fun <reified T> register(noinline consumer: (T, Supplier<NetworkManager.PacketContext>) -> Unit) {
        Observable.LOGGER.info("Registering ${T::class.java}")
        rawChannel.register(T::class.java, { t, buf ->
            buf.writeByteArray(ProtoBuf.encodeToByteArray(t))
        }, { buf ->
            ProtoBuf.decodeFromByteArray<T>(buf.readByteArray())
        }, consumer)
        Observable.LOGGER.info("Registered ${T::class.java}")
    }

    fun <T> sendToPlayers(players: List<ServerPlayer>, msg: T) = rawChannel.sendToPlayers(players, msg)
    fun <T> sendToPlayer(player: ServerPlayer, msg: T) = rawChannel.sendToPlayer(player, msg)

    fun <T> sendToServer(msg: T) = rawChannel.sendToServer(msg)
}