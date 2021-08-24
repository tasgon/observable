package observable.server

import kotlin.reflect.KClass

class TraceMap(var className: String, var classMethod: String = "null",
               val children: MutableMap<MapKey, TraceMap> = mutableMapOf(), var count: Int = 0) {
    constructor(target: KClass<*>) : this(target.java.name)

    data class MapKey(val className: String, val classMethod: String)

    inline fun add(stackTrace: List<StackTraceElement>) {
        add(stackTrace
            .asSequence()
            .dropWhile { it.className != className}
            .iterator())
    }

    inline fun add(traces: Iterator<StackTraceElement>) {
        count += 1
        classMethod = traces.next().classMethod
        var target = this
        while (traces.hasNext()) {
            val el = traces.next()
            classMethod = el.classMethod
            val key = MapKey(el.className, el.classMethod)
            val traceMap = target.children.getOrPut(key) { TraceMap(el.className, el.classMethod) }
            traceMap.count += 1
            target = traceMap
        }
    }
}