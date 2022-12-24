package observable.net

import dev.architectury.networking.NetworkChannel
import dev.architectury.networking.NetworkManager
import kotlinx.serialization.*
import kotlinx.serialization.protobuf.ProtoBuf
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import org.apache.logging.log4j.LogManager
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.lang.Exception
import java.util.*
import java.util.function.Supplier
import java.util.zip.*
import kotlin.collections.HashMap
import kotlin.reflect.KType
import kotlin.reflect.typeOf

class BetterChannel(id: ResourceLocation) {
    companion object {
        val LOGGER = LogManager.getLogger("ObservableNet")
    }
    var rawChannel = NetworkChannel.create(id)

    @Serializable
    data class PartialPacketBegin(val id: Long, val type: String)

    @Serializable
    class PartialPacketData(val id: Long, val data: ByteArray, val index: Int, val length: Int)

    class PartialPacketAssembler(val type: KType) {
        companion object {
            val MAP = HashMap<Long, PartialPacketAssembler>()
            val KNOWN_TYPES = HashMap<String, KType>()
            val ACTIONS = HashMap<KType, (Any, Supplier<NetworkManager.PacketContext>) -> Unit>()
            val PACKET_SIZE = 1000000

            @OptIn(ExperimentalStdlibApi::class)
            inline fun <reified T> register(noinline consumer: (T, Supplier<NetworkManager.PacketContext>) -> Unit) {
                val type = typeOf<T>()
                KNOWN_TYPES[T::class.java.name] = type
                ACTIONS[type] = { obj, ctx ->
                    var data: T? = null
                    try {
                        data = obj as T
                    } catch (e: Exception) {
                        LOGGER.warn("Error casting partial packet data to type ${T::class}!")
                        e.printStackTrace()
                    }
                    data?.let { consumer(it, ctx) }
                }
            }

            fun enqueue(packet: PartialPacketData, supplier: Supplier<NetworkManager.PacketContext>) {
                MAP[packet.id]?.let {
                    it.packets[packet.index] = packet.data
                    if (packet.length == it.packets.size) {
                        LOGGER.info("Assembling packet ${it.type}")
                        it.assemble()?.let { pkt ->
                            ACTIONS[it.type]?.invoke(pkt, supplier)
                        }
                        MAP.remove(packet.id)
                    }
                }
            }
        }
        var packets = mutableMapOf<Int, ByteArray>()

        @OptIn(ExperimentalSerializationApi::class)
        fun assemble(): Any? {
            val bs = ByteArrayOutputStream()
            packets.toList().sortedBy { it.first }.forEach {
                bs.write(it.second)
            }
            bs.close()
            return try {
                ProtoBuf.decodeFromByteArray(serializer(type), bs.toByteArray())
            } catch (e: Exception) {
                LOGGER.warn("Error decoding packet of type $type!")
                e.printStackTrace()
                null
            }
        }
    }

    init {
        this.register { t: PartialPacketBegin, supplier ->
            LOGGER.info("Received starting packet ${t.id} for ${t.type}")
            val type = PartialPacketAssembler.KNOWN_TYPES[t.type]
            if (type != null) {
                PartialPacketAssembler.MAP[t.id] = PartialPacketAssembler(type)
            } else {
                LOGGER.warn("Could not find mapping for ${t.type}")
            }
        }
        this.register { t: PartialPacketData, supplier ->
            LOGGER.info("Received packet ${t.index + 1}/${t.length} of id ${t.id}")
            PartialPacketAssembler.enqueue(t, supplier)
        }
    }

    /**
     * Provide a function that will attempt to call another function with packet data and fail gracefully if not able.
     *
     * @param action The function to call
     */
    inline fun <reified T> attempt(crossinline action: (FriendlyByteBuf) -> T): (FriendlyByteBuf) -> T? = {
        try {
            action(it)
        } catch (e: Exception) {
            LOGGER.warn("Error decoding packet!")
            e.printStackTrace()
            null
        }
    }

    inline fun <reified T> validate(noinline consumer: (T, Supplier<NetworkManager.PacketContext>) -> Unit) =
        { t: T?, v: Supplier<NetworkManager.PacketContext> ->
            if (t != null) consumer(t, v)
        }

    @OptIn(ExperimentalSerializationApi::class)
    inline fun <reified T> register(noinline consumer: (T, Supplier<NetworkManager.PacketContext>) -> Unit) {
        LOGGER.info("Registering ${T::class.java}")
        PartialPacketAssembler.register(consumer)
        rawChannel.register(T::class.java, { t, buf ->
            buf.writeByteArray(ProtoBuf.encodeToByteArray(t))
        }, attempt { ProtoBuf.decodeFromByteArray<T>(it.readByteArray()) }, validate(consumer))
        LOGGER.info("Registered ${T::class.java}")
    }

    @OptIn(ExperimentalSerializationApi::class)
    inline fun <reified T> registerCompressed(noinline consumer: (T, Supplier<NetworkManager.PacketContext>) -> Unit) {
        LOGGER.info("Registering ${T::class.java}")
        rawChannel.register(
            T::class.java,
            { t, buf ->
                val bs = ByteArrayOutputStream()
                val deflater = Deflater()
                deflater.setLevel(Deflater.BEST_COMPRESSION)
                val deflaterStream = DeflaterOutputStream(bs, deflater)
                deflaterStream.write(ProtoBuf.encodeToByteArray(t))
                deflaterStream.close()
                buf.writeByteArray(bs.toByteArray())
            },
            attempt {
                val istream = InflaterInputStream(ByteArrayInputStream(it.readByteArray()))
                ProtoBuf.decodeFromByteArray<T>(istream.readBytes())
            },
            validate(consumer),
        )
        LOGGER.info("Registered ${T::class.java}")
    }

    fun <T> sendToPlayers(players: List<ServerPlayer>, msg: T) = rawChannel.sendToPlayers(players, msg)
    fun <T> sendToPlayer(player: ServerPlayer, msg: T) = rawChannel.sendToPlayer(player, msg)

    @OptIn(ExperimentalStdlibApi::class)
    inline fun <reified T> sendToPlayersSplit(players: List<ServerPlayer>, msg: T) {
        val data = ProtoBuf.encodeToByteArray(msg)
        val bs = ByteArrayInputStream(data)
        val id = UUID.randomUUID().leastSignificantBits
        rawChannel.sendToPlayers(players, PartialPacketBegin(id, T::class.java.name))
        val size = bs.available() / PartialPacketAssembler.PACKET_SIZE +
            (bs.available() % PartialPacketAssembler.PACKET_SIZE).coerceAtMost(1)
        var idx = 0
        while (bs.available() > 0) {
            val arr = ByteArray(bs.available().coerceAtMost(PartialPacketAssembler.PACKET_SIZE))
            bs.read(arr)
            rawChannel.sendToPlayers(players, PartialPacketData(id, arr, idx, size))
            idx++
        }
    }

    fun <T> sendToServer(msg: T) = rawChannel.sendToServer(msg)
}
