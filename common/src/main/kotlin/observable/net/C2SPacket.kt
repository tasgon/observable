@file:UseSerializers(BlockPosSerializer::class, ResourceLocationSerializer::class)

package observable.net

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation

class C2SPacket {
    @Serializable
    data class InitTPSProfile(val duration: Int, val sample: Boolean)

    @Serializable
    data class RequestTeleport(val level: ResourceLocation, val entityId: Int?, val pos: BlockPos?) {
        constructor(level: ResourceLocation, entityId: Int) : this(level, entityId, null)
        constructor(level: ResourceLocation, pos: BlockPos) : this(level, null, pos)
    }

    @Serializable
    object RequestAvailability
}
