package observable.net

import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.Json.Default.decodeFromString
import kotlinx.serialization.protobuf.ProtoBuf
import me.shedaniel.architectury.networking.NetworkChannel
import me.shedaniel.architectury.networking.NetworkManager
import net.minecraft.resources.ResourceLocation
import observable.Observable
import java.io.Serializable
import java.util.function.Supplier

class BetterChannel(val id: ResourceLocation) {
    var rawChannel = NetworkChannel.create(id)

//    @ExperimentalSerializationApi
    inline fun <reified T> register(noinline consumer: (T, Supplier<NetworkManager.PacketContext>) -> Unit) {
        Observable.LOGGER.info("Registering ${T::class.java}")
        rawChannel.register(T::class.java, { t, buf ->
            buf.writeByteArray(ProtoBuf.encodeToByteArray(t))
        }, { buf ->
            ProtoBuf.decodeFromByteArray<T>(buf.readByteArray())
        }, consumer)
        Observable.LOGGER.info("Registered ${T::class.java}")
    }
}