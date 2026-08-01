package io.micronaut.context.propagation.client

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.propagation.MutablePropagatedContext
import io.micronaut.core.propagation.PropagatedContextElement
import io.micronaut.http.annotation.ClientFilter
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.annotation.ServerFilter
import io.micronaut.http.annotation.ServerFilter.MATCH_ALL_PATTERN
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Singleton
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.jvm.optionals.getOrNull

/**
 * Regression for https://github.com/micronaut-projects/micronaut-core/issues/12851
 *
 * With io.micrometer:context-propagation (Reactor automatic context propagation), sequential
 * suspend HTTP client calls must still see PropagatedContext in client filters.
 */
class ClientFilterPropagatedContextSpec {

    @Test
    fun clientFiltersSeeServerPropagatedContextWithMicrometerContextPropagation() {
        ApplicationContext.run(
            EmbeddedServer::class.java,
            mapOf(
                "spec.name" to "ClientFilterPropagatedContextSpec",
                "micronaut.propagation" to "thread-local",
                // explicit: micrometer context-propagation is on the test classpath and enables this by default
                "reactor.enable-automatic-context-propagation" to "true"
            )
        ).use { server ->
            val clientFilter = server.applicationContext.getBean(RecordingClientFilter::class.java)
            HttpClient.create(server.url).use { httpClient ->
                // Exercise multiple sequential client calls from a suspend controller
                val response = httpClient.toBlocking().retrieve("/")
                assertTrue(response.startsWith("OK"), "controller should return OK, was: $response")
            }

            assertTrue(clientFilter.contextValues.isNotEmpty(), "client filter should have been invoked")
            assertTrue(
                clientFilter.contextValues.all { it == "test-value" },
                "expected all client filter invocations to see test-value, got: ${clientFilter.contextValues}"
            )
        }
    }

    @Requires(property = "spec.name", value = "ClientFilterPropagatedContextSpec")
    @Controller
    open class TestController(
        private val client: TestClient
    ) {
        @Get("/")
        open suspend fun index(): String {
            repeat(10) {
                client.ping()
            }
            return "OK"
        }
    }

    @Requires(property = "spec.name", value = "ClientFilterPropagatedContextSpec")
    @Client("/")
    interface TestClient {
        @Get("/ping")
        suspend fun ping(): String
    }

    @Requires(property = "spec.name", value = "ClientFilterPropagatedContextSpec")
    @Controller
    open class PingController {
        @Get("/ping")
        open fun ping(): String = "pong"
    }

    @Requires(property = "spec.name", value = "ClientFilterPropagatedContextSpec")
    @ServerFilter(MATCH_ALL_PATTERN)
    open class TestServerFilter {
        @RequestFilter
        open fun filter(context: MutablePropagatedContext) {
            context.add(TestContextElement("test-value"))
        }
    }

    @Requires(property = "spec.name", value = "ClientFilterPropagatedContextSpec")
    @ClientFilter("/ping")
    @Singleton
    open class RecordingClientFilter {
        val contextValues = mutableListOf<String?>()

        @RequestFilter
        open fun filter(context: MutablePropagatedContext) {
            val value = context.context
                ?.find(TestContextElement::class.java)
                ?.getOrNull()
                ?.value
            contextValues.add(value)
        }
    }

    data class TestContextElement(val value: String) : PropagatedContextElement
}
