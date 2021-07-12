package observable.server

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.minecraft.core.BlockPos

class BlockPosSerializer : KSerializer<BlockPos> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("BlockPos", PrimitiveKind.INT)

    override fun deserialize(decoder: Decoder): BlockPos {
        val (x, y, z) = (1..3).map { decoder.decodeInt() }
        return BlockPos(x, y, z)
    }

    override fun serialize(encoder: Encoder, value: BlockPos) {
        value.apply {
            arrayOf(x, y, z).forEach { encoder.encodeInt(it) }
        }
    }
}