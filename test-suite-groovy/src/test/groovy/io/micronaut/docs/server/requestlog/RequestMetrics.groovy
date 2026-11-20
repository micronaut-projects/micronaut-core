package io.micronaut.docs.server.requestlog

// tag::imports[]
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.http.context.event.HttpRequestTerminatedEvent
import jakarta.inject.Singleton

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.LongAdder
// end::imports[]

@Requires(property = "spec.name", value = "RequestMetricsSpec")
// tag::clazz[]
@Singleton
class RequestMetrics implements ApplicationEventListener<HttpRequestTerminatedEvent> {
    final Map<String, LongAdder> nanosByRoute = new ConcurrentHashMap<>()

    @Override
    void onApplicationEvent(HttpRequestTerminatedEvent event) {
        event.requestLog.ifPresent { log -> // <1>
            String route = log.routeTemplate().orElse("UNMATCHED") // <2>
            String key = "$route ${log.status().orElse(0)}${log.completed ? '' : ' incomplete'}" // <3>
            log.totalDurationNanos().ifPresent { long nanos -> // <4>
                nanosByRoute.computeIfAbsent(key) { new LongAdder() }.add(nanos)
            }
        }
    }
}
// end::clazz[]
