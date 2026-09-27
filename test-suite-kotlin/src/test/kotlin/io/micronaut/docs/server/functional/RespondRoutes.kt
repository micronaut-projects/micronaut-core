package io.micronaut.docs.server.functional

// tag::imports[]
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.PathVariables
import io.micronaut.web.router.builder.HttpRouteBuilder
import io.micronaut.web.router.builder.HttpRoutes
import jakarta.inject.Singleton
import java.net.URI
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Function
import java.util.function.Supplier
// end::imports[]

@Requires(property = "spec.name", value = "RespondRoutesTest")
// tag::clazz[]
@Singleton
class RespondRoutes : HttpRoutes {

    private val visits = AtomicInteger()

    override fun routes(routes: HttpRouteBuilder) {
        routes.respond("/ping", HttpResponse.ok("pong").contentType(MediaType.TEXT_PLAIN_TYPE)) // <1>
            .after { _, response -> response.header("Cache-Control", "max-age=60") } // <2>
        routes.respond("/old-ping", HttpResponse.permanentRedirect<Any>(URI.create("/ping"))) // <3>
        routes.respond("/visits", Supplier {
            HttpResponse.ok("visit " + visits.incrementAndGet()).contentType(MediaType.TEXT_PLAIN_TYPE)
        }) // <4>
        routes.respond("/greetings/{name}", Function { pathVariables: PathVariables ->
            HttpResponse.ok("Hello " + pathVariables.getString("name")).contentType(MediaType.TEXT_PLAIN_TYPE)
        }) // <5>
        routes.respond(HttpMethod.POST, "/legacy/webhook", HttpResponse.status<Any>(HttpStatus.GONE)) // <6>
    }
}
// end::clazz[]
