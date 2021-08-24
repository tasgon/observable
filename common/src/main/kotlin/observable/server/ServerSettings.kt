package observable.server

import kotlinx.serialization.Serializable

@Serializable
object ServerSettings {
    var traceInterval: Long = 3L;

    var deviation: Long = 1L;

    var sample: Boolean = true;
}