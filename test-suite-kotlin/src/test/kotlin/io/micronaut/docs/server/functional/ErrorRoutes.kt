package io.micronaut.docs.server.functional

// tag::imports[]
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.web.router.builder.HttpRouteBuilder
import io.micronaut.web.router.builder.HttpRoutes
import jakarta.inject.Singleton
import java.util.concurrent.CompletableFuture
// end::imports[]

@Requires(property = "spec.name", value = "ErrorRoutesTest")
// tag::clazz[]
@Singleton
class ErrorRoutes : HttpRoutes {

    /**
     * Thrown when an order is not known.
     */
    class NoSuchOrder(id: Long) : RuntimeException("No order $id")

    override fun routes(routes: HttpRouteBuilder) {
        routes.error(NoSuchOrder::class.java) { request, error -> // <1>
            HttpResponse.notFound(mapOf("error" to error.message))
        }
        routes.status(HttpStatus.NOT_FOUND) { request -> // <2>
            HttpResponse.notFound(mapOf("error" to "Nothing at " + request.path))
        }
        routes.GET("/orders/{id}") { request, pathVariables ->
            throw NoSuchOrder(pathVariables.getLong("id"))
        }
        routes.path("/checkout") { checkout ->
            checkout.error(IllegalArgumentException::class.java) { request, error -> // <3>
                HttpResponse.badRequest(mapOf("invalid" to error.message))
            }
            checkout.errorAsync(IllegalStateException::class.java) { request, error -> // <4>
                CompletableFuture.supplyAsync { HttpResponse.status<Any>(HttpStatus.CONFLICT).body(mapOf("conflict" to error.message)) }
            }
            checkout.POST("/{quantity}") { request, pathVariables ->
                val quantity = pathVariables.getInt("quantity")
                if (quantity <= 0) {
                    throw IllegalArgumentException("quantity must be positive")
                }
                if (quantity > 10) {
                    throw IllegalStateException("not enough stock")
                }
                HttpResponse.ok("ordered $quantity").contentType(MediaType.TEXT_PLAIN_TYPE)
            }
        }
        routes.GET("/pricing/{quantity}") { request, pathVariables -> // <5>
            if (pathVariables.getInt("quantity") <= 0) {
                throw IllegalArgumentException("quantity must be positive")
            }
            HttpResponse.ok("price").contentType(MediaType.TEXT_PLAIN_TYPE)
        }
    }
}
// end::clazz[]
