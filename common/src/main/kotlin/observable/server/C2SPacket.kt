package observable.server

import kotlinx.serialization.Serializable

class C2SPacket {
    @Serializable
    data class InitTPSProfile(val duration: Int)
}