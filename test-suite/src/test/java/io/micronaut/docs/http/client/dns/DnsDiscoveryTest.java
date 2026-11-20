package io.micronaut.docs.http.client.dns;

import io.micronaut.context.ApplicationContext;
import io.micronaut.discovery.DiscoveryClient;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DnsDiscoveryTest {
    @Test
    void theClientUsesTheDnsDiscoveryOfTheService() {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of(
            "spec.name", "DnsDiscoveryTest",
            "micronaut.discovery.dns.services.orders.hostname", "orders.shop.svc.cluster.local",
            "micronaut.discovery.dns.services.orders.port", 8080))) {
            assertNotNull(ctx.getBean(OrdersClient.class));
            assertTrue(Mono.from(ctx.getBean(DiscoveryClient.class).getServiceIds()).block().contains("orders"));
        }
    }
}
