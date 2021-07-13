package observable.net

import kotlinx.serialization.Serializable
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.block.entity.BlockEntity
import observable.client.ProfileScreen

class C2SPacket {
    @Serializable
    data class InitTPSProfile(val duration: Int)

    @Serializable
    data class RequestTeleport(val id: Int?, @Serializable(with = BlockPosSerializer::class) val pos: BlockPos?) {
        constructor(obj: Any) : this((obj as? Entity)?.id, (obj as? BlockEntity)?.blockPos)
    }
}