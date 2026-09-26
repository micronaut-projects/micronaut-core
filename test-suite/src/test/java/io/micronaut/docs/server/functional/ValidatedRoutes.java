package io.micronaut.docs.server.functional;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.http.HttpResponse;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Singleton;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@Requires(property = "spec.name", value = "ValidatedRoutesTest")
@Singleton
public class ValidatedRoutes implements HttpRoutes {
    private final ProductService products;

    ValidatedRoutes(ProductService products) {
        this.products = products;
    }

    @Override
    public void routes(HttpRouteBuilder routes) {
        // tag::route[]
        routes.POST("/products", Product.class, (request, pathVariables, product) ->
            HttpResponse.created(products.save(product))); // <2>
        // end::route[]
    }

    @Introspected
    public record Product(@NotBlank String name) {
    }

    @Requires(property = "spec.name", value = "ValidatedRoutesTest")
    // tag::service[]
    @Singleton
    public static class ProductService {
        public Product save(@Valid Product product) { // <1>
            return product;
        }
    }
    // end::service[]
}
