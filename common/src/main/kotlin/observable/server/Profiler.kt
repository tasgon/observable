package observable.server

import net.minecraft.world.entity.Entity

class Profiler {
    data class EntityTimingData(var time: Long, var ticks: Int)

    var timingsMap = HashMap<Int, EntityTimingData>()

    fun process(entity: Entity, time: Long) {
        var timingInfo = timingsMap.getOrDefault(entity.id, EntityTimingData(0, 0))
        timingInfo.time += time
        timingInfo.ticks++
    }
}