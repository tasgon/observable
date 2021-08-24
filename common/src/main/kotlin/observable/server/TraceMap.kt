package observable.server

import kotlin.reflect.KClass

class TraceMap(var className: String, var classMethod: String = "null",
               val children: MutableMap<MapKey, TraceMap> = mutableMapOf(), var count: Int = 0,
               var initialDepth: Int = 0) {
    constructor(target: KClass<*>, initialDepth: Int) : this(target.java.name, initialDepth = initialDepth)

    data class MapKey(val className: String, val classMethod: String)

    fun add(stackTrace: List<StackTraceElement>) {
        add(stackTrace
            .asReversed()
            .drop(initialDepth)
            .asSequence()
            .iterator())
    }

    inline fun add(traces: Iterator<StackTraceElement>) {
        if (!traces.hasNext()) return
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