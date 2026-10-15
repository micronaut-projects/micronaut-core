package io.micronaut.docs.http.client.loadbalance

import io.micronaut.context.ApplicationContext
import io.micronaut.http.client.LoadBalancerResolver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono

class PrimaryStrategyTest {

    @Test
    fun theNamedStrategyBeanPicksTheInstance() {
        ApplicationContext.run(mapOf(
            "spec.name" to "PrimaryStrategyTest",
            "micronaut.http.services.foo.urls" to listOf("http://foo1", "http://foo2"),
            "micronaut.http.services.foo.load-balancer-strategy" to "primary"
        )).use { ctx ->
            val loadBalancer = ctx.getBean(LoadBalancerResolver::class.java).resolve("foo").orElseThrow()
            repeat(3) {
                assertEquals("foo1", Mono.from(loadBalancer.select()).block()!!.uri.host)
            }
        }
    }
}
