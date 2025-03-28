package example.micronaut.jacksondatabind

import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import jakarta.inject.Singleton
import reactor.core.publisher.Mono

@Requires(property = "spec.name", value = "JacksonDatabindDataClassSerialization")
@Singleton
class GreetingService {
    fun sayHi(): Greeting = Greeting("Hola Mundo")
}
