package observable.net

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.Entity

class EntitySerializer : KSerializer<Entity?> {
    private val delegate = Int.serializer().nullable
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun deserialize(decoder: Decoder): Entity? {
        return delegate.deserialize(decoder)?.let {
            Minecraft.getInstance().level?.getEntity(it)
        }
    }

    override fun serialize(encoder: Encoder, value: Entity?) {
        delegate.serialize(encoder, value?.id)
    }
}