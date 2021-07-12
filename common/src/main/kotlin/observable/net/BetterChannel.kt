package observable.net

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import me.shedaniel.architectury.networking.NetworkChannel
import me.shedaniel.architectury.networking.NetworkManager
import net.minecraft.resources.ResourceLocation
import java.io.Serializable
import java.util.function.Supplier

class BetterChannel(val id: ResourceLocation) {
    var rawChannel = NetworkChannel.create(id)

    @ExperimentalSerializationApi
    inline fun <reified T: Serializable> register(noinline consumer: (T, Supplier<NetworkManager.PacketContext>) -> Unit) {
        rawChannel.register(T::class.java, { t, buf ->
            buf.writeByteArray(ProtoBuf.encodeToByteArray(t))
        }, { buf ->
            ProtoBuf.decodeFromByteArray<T>(buf.readByteArray())
        }, consumer)
    }
}