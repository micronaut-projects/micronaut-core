package io.micronaut.docs.server.gateway;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.gateway.GatewayFilter;
import io.micronaut.http.gateway.ProxyExchange;
import io.micronaut.http.gateway.config.GatewayFilterFactory;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.util.List;

// tag::clazz[]
public record AuditFilter(String team) implements GatewayFilter {

    @Override
    public void filterRequest(ProxyExchange exchange, MutableHttpRequest<?> request) { // <1>
        request.getHeaders().set("X-Audit-Team", team);
    }

    @Override
    public void filterResponse(ProxyExchange exchange, MutableHttpResponse<?> response) { // <2>
        response.getHeaders().set("X-Audited-Route", exchange.routeId());
    }

    @Factory
    @Requires(property = "spec.name", value = "GatewayDocsTest")
    static class AuditFilterFactory {
        @Singleton
        @Named("Audit")
        GatewayFilterFactory audit() {
            return GatewayFilterFactory.of(List.of("team"), args -> new AuditFilter(args.get("team"))); // <3>
        }
    }
}
// end::clazz[]
