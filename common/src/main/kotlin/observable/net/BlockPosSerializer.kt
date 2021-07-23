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
    @kotlinx.serialization.Serializable
    private data class SerializableBlockPos(val x: Int, val y: Int, val z: Int) {
        constructor(pos: BlockPos) : this(pos.x, pos.y, pos.z)
        val pos get() = BlockPos(x, y, z)
    }

    private val delegate = SerializableBlockPos.serializer()
    override val descriptor = delegate.descriptor
    override fun deserialize(decoder: Decoder) =
        delegate.deserialize(decoder).pos

    override fun serialize(encoder: Encoder, value: BlockPos) =
        delegate.serialize(encoder, SerializableBlockPos(value))
}