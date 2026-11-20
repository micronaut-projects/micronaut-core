package io.micronaut.docs.server.requestlog

// tag::imports[]
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.http.context.event.HttpRequestTerminatedEvent
import jakarta.inject.Singleton
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.LongAdder
// end::imports[]

@Requires(property = "spec.name", value = "RequestMetricsTest")
// tag::clazz[]
@Singleton
class RequestMetrics : ApplicationEventListener<HttpRequestTerminatedEvent> {
    val nanosByRoute: MutableMap<String, LongAdder> = ConcurrentHashMap()

    override fun onApplicationEvent(event: HttpRequestTerminatedEvent) {
        event.requestLog.ifPresent { log -> // <1>
            val route = log.routeTemplate().orElse("UNMATCHED") // <2>
            val key = "$route ${log.status().orElse(0)}" + if (log.isCompleted) "" else " incomplete" // <3>
            log.totalDurationNanos().ifPresent { nanos -> // <4>
                nanosByRoute.computeIfAbsent(key) { LongAdder() }.add(nanos)
            }
        }
    }
}
// end::clazz[]
