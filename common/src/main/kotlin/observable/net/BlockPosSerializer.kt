package observable.net

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.minecraft.core.BlockPos

class BlockPosSerializer : KSerializer<BlockPos> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("BlockPos", PrimitiveKind.LONG)
    override fun deserialize(decoder: Decoder) = BlockPos.of(decoder.decodeLong())
    override fun serialize(encoder: Encoder, value: BlockPos) = encoder.encodeLong(value.asLong())
}