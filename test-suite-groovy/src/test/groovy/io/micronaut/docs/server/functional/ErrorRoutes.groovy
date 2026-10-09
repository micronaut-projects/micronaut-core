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

@Requires(property = "spec.name", value = "ErrorRoutesSpec")
// tag::clazz[]
@Singleton
class ErrorRoutes implements HttpRoutes {

    /**
     * Thrown when an order is not known.
     */
    static final class NoSuchOrder extends RuntimeException {
        NoSuchOrder(long id) {
            super("No order " + id)
        }
    }

    @Override
    void routes(HttpRouteBuilder routes) {
        routes.error(NoSuchOrder) { request, NoSuchOrder error -> // <1>
            HttpResponse.notFound([error: error.message])
        }
        routes.status(HttpStatus.NOT_FOUND) { request -> // <2>
            HttpResponse.notFound([error: "Nothing at " + request.path])
        }
        routes.GET("/orders/{id}") { request, pathVariables ->
            throw new NoSuchOrder(pathVariables.getLong("id"))
        }
        routes.path("/checkout") { checkout ->
            checkout.error(IllegalArgumentException) { request, IllegalArgumentException error -> // <3>
                HttpResponse.badRequest([invalid: error.message])
            }
            checkout.errorAsync(IllegalStateException) { request, IllegalStateException error -> // <4>
                CompletableFuture.supplyAsync { HttpResponse.status(HttpStatus.CONFLICT).body([conflict: error.message]) }
            }
            checkout.POST("/{quantity}") { request, pathVariables ->
                int quantity = pathVariables.getInt("quantity")
                if (quantity <= 0) {
                    throw new IllegalArgumentException("quantity must be positive")
                }
                if (quantity > 10) {
                    throw new IllegalStateException("not enough stock")
                }
                HttpResponse.ok("ordered " + quantity).contentType(MediaType.TEXT_PLAIN_TYPE)
            }
        }
        routes.GET("/pricing/{quantity}") { request, pathVariables -> // <5>
            if (pathVariables.getInt("quantity") <= 0) {
                throw new IllegalArgumentException("quantity must be positive")
            }
            HttpResponse.ok("price").contentType(MediaType.TEXT_PLAIN_TYPE)
        }
    }
}
// end::clazz[]
