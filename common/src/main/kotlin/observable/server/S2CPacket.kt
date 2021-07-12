package observable.server

import ProfilingData
import kotlinx.serialization.Serializable

class S2CPacket {
    @Serializable
    data class ProfilingResult(val data: ProfilingData) {
    }
}