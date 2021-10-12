package io.micronaut.scheduling.instrument

import io.micronaut.context.annotation.Property
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.junit.jupiter.api.Test
import jakarta.inject.Inject
import reactor.core.publisher.Flux
import kotlin.test.assertTrue

@MicronautTest
@Property(name = "tracing.zipkin.enabled", value = "true")
class MultipleInvocationInstrumenterSpec {

    @Inject
    @field:Client("/")
    lateinit var client : HttpClient;

    @Test
    fun testMultipleInvocationInstrumenter() {
        val map: List<*> = Flux.from(client
                .retrieve(
                        HttpRequest.GET<Any>("/tryout/100"),
                        MutableList::class.java
                )).blockFirst()

        assertTrue(map.isNotEmpty())
    }
}
