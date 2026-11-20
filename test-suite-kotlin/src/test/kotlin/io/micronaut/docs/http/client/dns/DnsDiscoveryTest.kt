package io.micronaut.docs.http.client.dns

import io.micronaut.context.ApplicationContext
import io.micronaut.discovery.DiscoveryClient
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono

class DnsDiscoveryTest {
    @Test
    fun theClientUsesTheDnsDiscoveryOfTheService() {
        ApplicationContext.run(mapOf(
            "spec.name" to "DnsDiscoveryTest",
            "micronaut.discovery.dns.services.orders.hostname" to "orders.shop.svc.cluster.local",
            "micronaut.discovery.dns.services.orders.port" to 8080
        )).use { ctx ->
            assertNotNull(ctx.getBean(OrdersClient::class.java))
            assertTrue(Mono.from(ctx.getBean(DiscoveryClient::class.java).serviceIds).block()!!.contains("orders"))
        }
    }
}
