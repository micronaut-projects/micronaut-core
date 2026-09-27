package io.micronaut.docs.server.functional

import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Introspected
import io.micronaut.http.HttpResponse
import io.micronaut.web.router.builder.HttpRouteBuilder
import io.micronaut.web.router.builder.HttpRoutes
import jakarta.inject.Singleton
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank

@Requires(property = "spec.name", value = "ValidatedRoutesSpec")
@Singleton
class ValidatedRoutes implements HttpRoutes {

    private final ProductService products

    ValidatedRoutes(ProductService products) {
        this.products = products
    }

    @Override
    void routes(HttpRouteBuilder routes) {
        // tag::route[]
        routes.POST("/products", Product) { request, pathVariables, Product product ->
            HttpResponse.created(products.save(product)) // <2>
        }
        // end::route[]
    }

    @Introspected
    static record Product(@NotBlank String name) {
    }

    @Requires(property = "spec.name", value = "ValidatedRoutesSpec")
    // tag::service[]
    @Singleton
    static class ProductService {
        Product save(@Valid Product product) { // <1>
            return product
        }
    }
    // end::service[]
}
