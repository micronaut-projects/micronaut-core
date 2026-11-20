package io.micronaut.docs.server.gateway

import io.micronaut.context.annotation.Requires
import io.micronaut.http.gateway.GatewayObserver
import jakarta.inject.Singleton

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.LongAdder

@Requires(property = "spec.name", value = "GatewayDocsSpec")
// tag::clazz[]
@Singleton
class RouteMetrics implements GatewayObserver {
    private final Map<String, LongAdder> counts = new ConcurrentHashMap<>()

    @Override
    void exchanged(GatewayObserver.Exchange exchange) {
        // e.g. a Micrometer timer tagged with the route id, the status and the outcome
        String key = exchange.routeId() + " " + exchange.outcome() + " " + exchange.status()
        counts.computeIfAbsent(key, { new LongAdder() }).increment()
    }

    long count(String routeId, GatewayObserver.Outcome outcome, int status) {
        counts.get(routeId + " " + outcome + " " + status)?.sum() ?: 0
    }
}
// end::clazz[]
