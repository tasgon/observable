@file:UseSerializers(BlockPosSerializer::class, ResourceLocationSerializer::class)

package observable.net

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

class C2SPacket {
    @Serializable data class InitTPSProfile(val duration: Int, val sample: Boolean)

    @Serializable object RequestAvailability
}
