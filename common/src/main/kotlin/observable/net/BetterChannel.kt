package observable.net

import dev.architectury.networking.NetworkManager
import dev.architectury.networking.NetworkManager.Side
import dev.architectury.networking.NetworkManager.canPlayerReceive
import dev.architectury.networking.transformers.SplitPacketTransformer
import dev.architectury.platform.Platform
import dev.architectury.utils.Env
import kotlinx.serialization.*
import kotlinx.serialization.protobuf.ProtoBuf
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer
import org.apache.logging.log4j.LogManager
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

@OptIn(ExperimentalSerializationApi::class)
class BetterChannel(
    val id: Identifier,
) {
    companion object {
        val LOGGER = LogManager.getLogger("ObservableNet")
    }

    val s2cLocation: Identifier = id.withSuffix("-s2c")
    val c2sLocation: Identifier = id.withSuffix("-c2s")

    class SerializedPayload(
        val className: String,
        val data: ByteArray,
        val location: Identifier,
    ) : CustomPacketPayload {
        override fun type() = CustomPacketPayload.Type<CustomPacketPayload>(location)
    }

    inline fun <reified T> createPayload(
        data: T,
        side: Side,
    ) = SerializedPayload(
        T::class.java.name,
        ProtoBuf.encodeToByteArray(data),
        if (side ==
            Side.S2C
        ) {
            s2cLocation
        } else {
            c2sLocation
        },
    )

    val handlers = mutableMapOf<String, (ByteArray, NetworkManager.PacketContext) -> Unit>()

    init {
        val codec =
            object : StreamCodec<RegistryFriendlyByteBuf, SerializedPayload> {
                override fun decode(buf: RegistryFriendlyByteBuf): SerializedPayload {
                    val name = buf.readUtf()
                    val bytes = GZIPInputStream(ByteArrayInputStream(buf.readByteArray())).buffered().use { it.readAllBytes() }
                    return SerializedPayload(name, bytes, id)
                }

                override fun encode(
                    buf: RegistryFriendlyByteBuf,
                    payload: SerializedPayload,
                ) {
                    buf.writeUtf(payload.className)
                    val bos = ByteArrayOutputStream()
                    GZIPOutputStream(bos).buffered().use { it.write(payload.data) }
                    buf.writeByteArray(bos.toByteArray())
                }
            }
        if (Platform.getEnvironment() == Env.SERVER) {
            NetworkManager.registerS2CPayloadType(CustomPacketPayload.Type(s2cLocation), codec, listOf(SplitPacketTransformer()))
        } else {
            NetworkManager.registerReceiver(
                Side.S2C,
                CustomPacketPayload.Type(s2cLocation),
                codec,
                listOf(SplitPacketTransformer()),
            ) { value, ctx ->
                handlers[value.className]?.invoke(value.data, ctx)
            }
        }
        NetworkManager.registerReceiver(Side.C2S, CustomPacketPayload.Type(c2sLocation), codec) { value, ctx ->
            ctx.player
            handlers[value.className]?.invoke(value.data, ctx)
        }
    }

    inline fun <reified T> register(noinline consumer: (T, NetworkManager.PacketContext) -> Unit) {
        handlers[T::class.java.name] = { buf, ctx -> consumer(ProtoBuf.decodeFromByteArray(buf), ctx) }
        LOGGER.info("Registered ${T::class.java}")
    }

    inline fun <reified T> sendToPlayers(
        players: Iterable<ServerPlayer>,
        msg: T,
    ) = NetworkManager.sendToPlayers(
        players.filter {
            canPlayerReceive(it, s2cLocation)
        },
        createPayload(msg, Side.S2C),
    )

    inline fun <reified T> sendToPlayer(
        player: ServerPlayer,
        msg: T,
    ) = sendToPlayers(listOf(player), msg)

    inline fun <reified T> sendToPlayersSplit(
        players: Iterable<ServerPlayer>,
        msg: T,
    ) = sendToPlayers(players, msg)

    inline fun <reified T> sendToServer(msg: T) = NetworkManager.sendToServer(createPayload(msg, Side.C2S))
}
