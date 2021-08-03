package observable.net

import kotlinx.serialization.*
import kotlinx.serialization.protobuf.ProtoBuf
import me.shedaniel.architectury.networking.NetworkChannel
import me.shedaniel.architectury.networking.NetworkManager
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import observable.Observable
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.lang.Exception
import java.util.function.Supplier
import java.util.zip.*

class BetterChannel(val id: ResourceLocation) {
    var rawChannel = NetworkChannel.create(id)

    inline fun <reified T> validate(noinline consumer: (T, Supplier<NetworkManager.PacketContext>) -> Unit) =
        { t: T?, v: Supplier<NetworkManager.PacketContext> ->
            if (t != null) consumer(t, v)
        }

    inline fun <reified T> register(noinline consumer: (T, Supplier<NetworkManager.PacketContext>) -> Unit) {
        Observable.LOGGER.info("Registering ${T::class.java}")
        rawChannel.register(T::class.java, { t, buf ->
            buf.writeByteArray(ProtoBuf.encodeToByteArray(t))
        }, { buf ->
            try {
                ProtoBuf.decodeFromByteArray<T>(buf.readByteArray())
            } catch (e: Exception) {
                Observable.LOGGER.warn("Error decoding packet!")
                e.printStackTrace()
                null
            }
        }, validate(consumer))
        Observable.LOGGER.info("Registered ${T::class.java}")
    }

    inline fun <reified T> registerCompressed(noinline consumer: (T, Supplier<NetworkManager.PacketContext>) -> Unit) {
        Observable.LOGGER.info("Registering ${T::class.java}")
        rawChannel.register(T::class.java, { t, buf ->
            val bs = ByteArrayOutputStream()
            val deflater = Deflater()
            deflater.setLevel(Deflater.BEST_COMPRESSION)
            val deflaterStream = DeflaterOutputStream(bs, deflater)
            deflaterStream.write(ProtoBuf.encodeToByteArray(t))
            deflaterStream.close()
            buf.writeByteArray(bs.toByteArray())
        }, { buf ->
            try {
                val istream = InflaterInputStream(ByteArrayInputStream(buf.readByteArray()))
                ProtoBuf.decodeFromByteArray<T>(istream.readAllBytes())
            } catch (e: Exception) {
                Observable.LOGGER.warn("Error decoding packet!")
                e.printStackTrace()
                null
            }
        }, validate(consumer))
        Observable.LOGGER.info("Registered ${T::class.java}")
    }

    fun <T> sendToPlayers(players: List<ServerPlayer>, msg: T) = rawChannel.sendToPlayers(players, msg)
    fun <T> sendToPlayer(player: ServerPlayer, msg: T) = rawChannel.sendToPlayer(player, msg)

    fun <T> sendToServer(msg: T) = rawChannel.sendToServer(msg)
}