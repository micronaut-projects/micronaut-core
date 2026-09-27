package io.micronaut.docs.server.functional

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Introspected
import io.micronaut.http.HttpResponse
import io.micronaut.web.router.builder.HttpRouteBuilder
import io.micronaut.web.router.builder.HttpRoutes
import jakarta.inject.Singleton
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank

@Requires(property = "spec.name", value = "ValidatedRoutesTest")
@Singleton
class ValidatedRoutes(private val products: ProductService) : HttpRoutes {

    override fun routes(routes: HttpRouteBuilder) {
        // tag::route[]
        routes.POST("/products", Product::class.java) { request, pathVariables, product ->
            HttpResponse.created(products.save(product)) // <2>
        }
        // end::route[]
    }

    @Introspected
    data class Product(@JsonProperty("name") @field:NotBlank val name: String)

    @Requires(property = "spec.name", value = "ValidatedRoutesTest")
    // tag::service[]
    @Singleton
    open class ProductService {
        open fun save(@Valid product: Product): Product { // <1>
            return product
        }
    }
    // end::service[]
}
