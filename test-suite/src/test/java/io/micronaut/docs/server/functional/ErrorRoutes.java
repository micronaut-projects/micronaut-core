package io.micronaut.docs.server.functional;

// tag::imports[]
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
// end::imports[]

@Requires(property = "spec.name", value = "ErrorRoutesTest")
// tag::clazz[]
@Singleton
public class ErrorRoutes implements HttpRoutes {

    /**
     * Thrown when an order is not known.
     */
    public static final class NoSuchOrder extends RuntimeException {
        public NoSuchOrder(long id) {
            super("No order " + id);
        }
    }

    @Override
    public void routes(HttpRouteBuilder routes) {
        routes.error(NoSuchOrder.class, (request, error) -> // <1>
            HttpResponse.notFound(Map.of("error", error.getMessage())));
        routes.status(HttpStatus.NOT_FOUND, request -> // <2>
            HttpResponse.notFound(Map.of("error", "Nothing at " + request.getPath())));

        routes.GET("/orders/{id}", (request, pathVariables) -> {
            throw new NoSuchOrder(pathVariables.getLong("id"));
        });

        routes.path("/checkout", checkout -> {
            checkout.error(IllegalArgumentException.class, (request, error) -> // <3>
                HttpResponse.badRequest(Map.of("invalid", error.getMessage())));
            checkout.errorAsync(IllegalStateException.class, (request, error) -> // <4>
                CompletableFuture.supplyAsync(() -> HttpResponse.status(HttpStatus.CONFLICT).body(Map.of("conflict", error.getMessage()))));
            checkout.POST("/{quantity}", (request, pathVariables) -> {
                int quantity = pathVariables.getInt("quantity");
                if (quantity <= 0) {
                    throw new IllegalArgumentException("quantity must be positive");
                }
                if (quantity > 10) {
                    throw new IllegalStateException("not enough stock");
                }
                return HttpResponse.ok("ordered " + quantity).contentType(MediaType.TEXT_PLAIN_TYPE);
            });
        });

        routes.GET("/pricing/{quantity}", (request, pathVariables) -> { // <5>
            if (pathVariables.getInt("quantity") <= 0) {
                throw new IllegalArgumentException("quantity must be positive");
            }
            return HttpResponse.ok("price").contentType(MediaType.TEXT_PLAIN_TYPE);
        });
    }
}
// end::clazz[]
