package io.micronaut.docs.server.gateway

import io.micronaut.context.annotation.Requires
import io.micronaut.http.gateway.GatewayObserver
import jakarta.inject.Singleton
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.LongAdder

@Requires(property = "spec.name", value = "GatewayDocsTest")
// tag::clazz[]
@Singleton
class RouteMetrics : GatewayObserver {
    private val counts = ConcurrentHashMap<String, LongAdder>()

    override fun exchanged(exchange: GatewayObserver.Exchange) {
        // e.g. a Micrometer timer tagged with the route id, the status and the outcome
        val key = "${exchange.routeId} ${exchange.outcome} ${exchange.status}"
        counts.computeIfAbsent(key) { LongAdder() }.increment()
    }

    fun count(routeId: String, outcome: GatewayObserver.Outcome, status: Int): Long =
        counts["$routeId $outcome $status"]?.sum() ?: 0
}
// end::clazz[]
