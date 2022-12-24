package observable.server

import net.minecraft.server.level.ServerLevel
import kotlin.reflect.KClass

val SERVER_LEVEL_CLASS = ServerLevel::class.java.name

class TraceMap(
    var className: String = "null",
    var methodName: String = "null",
    val children: MutableMap<MapKey, TraceMap> = mutableMapOf(),
    var count: Int = 0,
) {
    constructor(target: KClass<*>) :
        this(target.java.name)

    data class MapKey(val className: String, val classMethod: String)

    fun add(stackTrace: List<StackTraceElement>) {
        val traces = stackTrace
            .asReversed()
            .iterator()
        while (traces.hasNext()) {
            val name = traces.next().className
            if (SERVER_LEVEL_CLASS == name) {
                add(traces)
                return
            }
        }
    }

    inline fun add(traces: Iterator<StackTraceElement>) {
        if (!traces.hasNext()) return
        count += 1
        val tr = traces.next()
        className = tr.className
        methodName = tr.methodName
        var target = this
        while (traces.hasNext()) {
            val el = traces.next()
            methodName = el.methodName
            val key = MapKey(el.className, el.methodName)
            val traceMap = target.children.getOrPut(key) { TraceMap(el.className, el.methodName) }
            traceMap.count += 1
            target = traceMap
        }
    }
}
