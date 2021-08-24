package observable.server

import kotlinx.serialization.Serializable

@Serializable
object ServerSettings {

    var traceInterval: Long = 10000L;

    var deviation: Long = 100L;

    var sample: Boolean = true;
}