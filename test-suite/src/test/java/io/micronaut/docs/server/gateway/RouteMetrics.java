package io.micronaut.docs.server.gateway;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.gateway.GatewayObserver;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

@Requires(property = "spec.name", value = "GatewayDocsTest")
// tag::clazz[]
@Singleton
public class RouteMetrics implements GatewayObserver {
    private final Map<String, LongAdder> counts = new ConcurrentHashMap<>();

    @Override
    public void exchanged(Exchange exchange) {
        // e.g. a Micrometer timer tagged with the route id, the status and the outcome
        String key = exchange.routeId() + " " + exchange.outcome() + " " + exchange.status();
        counts.computeIfAbsent(key, k -> new LongAdder()).increment();
    }

    public long count(String routeId, Outcome outcome, int status) {
        LongAdder count = counts.get(routeId + " " + outcome + " " + status);
        return count == null ? 0 : count.sum();
    }
}
// end::clazz[]
