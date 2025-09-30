package example.micronaut.jacksondatabind

import io.micronaut.context.annotation.Property
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux

@Property(name = "spec.name", value = "JacksonDatabindDataClassSerialization")
@MicronautTest
class DataClassSerializationTest {

    @Inject
    lateinit var client: GreetingClient

    @Test
    fun testHelloGet() {
        Assertions.assertEquals(
                "Hola Mundo",
                Flux.from(client.hello()).blockFirst()!!.message
        )
    }
}

@Property(name = "spec.name", value = "JacksonDatabindDataClassSerialization")
@Client("/")
interface GreetingClient {

    @Get("/hola")
    fun hello() : Publisher<Greeting>
}
