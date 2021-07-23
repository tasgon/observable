@file:UseSerializers(BlockPosSerializer::class, ResourceLocationSerializer::class)

package observable.net

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import observable.client.ProfileScreen

class C2SPacket {
    @Serializable
    data class InitTPSProfile(val duration: Int)

    @Serializable
    data class RequestTeleport(val level: ResourceLocation, val entityId: Int?, val pos: BlockPos?) {
        constructor(level: ResourceLocation, entityId: Int) : this(level, entityId, null)
        constructor(level: ResourceLocation, pos: BlockPos) : this(level, null, pos)
    }
}