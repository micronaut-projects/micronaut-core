package io.micronaut.docs.server.intro

import io.micronaut.context.annotation.Property
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@Property(name = "spec.name", value = "HelloControllerSpec")
@MicronautTest
class HelloControllerNestedSpec {

    @Nested
    inner class NestedHelloSpec {

        @Inject
        @field:Client("/")
        lateinit var client: HttpClient

        @Test
        fun testHelloWorldResponse() {
            assertEquals("Hello World", client.toBlocking().retrieve("/hello"))
        }
    }
}
