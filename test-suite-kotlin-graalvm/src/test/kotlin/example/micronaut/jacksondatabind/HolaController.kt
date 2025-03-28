package example.micronaut.jacksondatabind

import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get

@Requires(property = "spec.name", value = "JacksonDatabindDataClassSerialization")
@Controller
class HolaController(private val greetingService: GreetingService) {
    @Get("/hola")
    fun sayHi(): Greeting = greetingService.sayHi()
}
