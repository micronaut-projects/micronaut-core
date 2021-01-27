package io.micronaut.docs.server.intro

// tag::imports[]
import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import javax.inject.Inject
// end::imports[]

/**
 * @author graemerocher
 * @since 1.0
 */
@Property(name = "spec.name", value = "HelloControllerSpec")
// tag::class[]
@MicronautTest // <1>
class HelloClientSpec {

    @Inject
    lateinit var client: HelloClient // <2>

    @Test
    fun testHelloWorldResponse() {
        assertEquals("Hello World", client.hello().blockingGet())// <3>
    }
}
// end::class[]
