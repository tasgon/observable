package observable.net

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.minecraft.client.Minecraft
import net.minecraft.world.level.block.entity.BlockEntity

class BlockEntitySerializer : KSerializer<BlockEntity?> {
    private val delegate = BlockPosSerializer().nullable
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun deserialize(decoder: Decoder): BlockEntity? {
        return delegate.deserialize(decoder)?.let {
            Minecraft.getInstance().level?.getBlockEntity(it)
        }
    }

    override fun serialize(encoder: Encoder, value: BlockEntity?) {
        delegate.serialize(encoder, value?.blockPos)
    }
}