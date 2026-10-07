package observable.net

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.minecraft.resources.Identifier

class ResourceLocationSerializer : KSerializer<Identifier> {
    private val delegate = String.serializer()
    override val descriptor = delegate.descriptor

    override fun deserialize(decoder: Decoder) = Identifier.parse(delegate.deserialize(decoder))

    override fun serialize(encoder: Encoder, value: Identifier) =
        delegate.serialize(encoder, value.toString())
}
