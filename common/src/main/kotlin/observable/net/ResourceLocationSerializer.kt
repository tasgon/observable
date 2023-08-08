package observable.net

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.minecraft.resources.ResourceLocation

class ResourceLocationSerializer : KSerializer<ResourceLocation> {
    private val delegate = String.serializer()
    override val descriptor = delegate.descriptor

    override fun deserialize(decoder: Decoder) = ResourceLocation(delegate.deserialize(decoder))

    override fun serialize(encoder: Encoder, value: ResourceLocation) =
        delegate.serialize(encoder, value.toString())
}
