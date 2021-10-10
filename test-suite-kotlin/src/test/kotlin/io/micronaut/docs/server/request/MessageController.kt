package io.micronaut.docs.server.request

// tag::imports[]
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
// end::imports[]

// tag::class[]
@Controller("/request")
class MessageController {

    @Get("/hello") // <1>
    fun hello(request: HttpRequest<*>): HttpResponse<String> {
        val name = request.parameters
                .getFirst("name")
                .orElse("Nobody") // <2>

        return HttpResponse.ok("Hello $name!!")
                .header("X-My-Header", "Foo") // <3>
    }
}
// end::class[]
