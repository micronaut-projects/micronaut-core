package io.micronaut.docs.server.functional

// tag::imports[]
import io.micronaut.context.annotation.Requires
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpResponse
import io.micronaut.web.router.builder.HttpRouteBuilder
import io.micronaut.web.router.builder.HttpRoutes
import io.micronaut.web.router.builder.LocatedHttpRouteBuilder
import io.micronaut.web.router.builder.LocatedRoutes
import jakarta.inject.Singleton
// end::imports[]

@Requires(property = "spec.name", value = "FormLocatorRoutesTest")
// tag::clazz[]
@Singleton
class FormLocatorRoutes : HttpRoutes {

    /**
     * A located target.
     *
     * @param customer The customer of the cart
     */
    data class Cart(val customer: String)

    private val cartRoutes = CartRoutes()

    override fun routes(routes: HttpRouteBuilder) {
        routes.locator("/carts") // <1>
            .form() // <2>
            .locate({ request, pathVariables, form ->
                Cart(form.find("customer", String::class.java).orElse("guest")) // <3>
            }, cartRoutes)
    }

    /**
     * The routes of a located cart.
     */
    class CartRoutes : LocatedRoutes<Cart> {
        override fun targetType(): Argument<Cart> = Argument.of(Cart::class.java)

        override fun routes(cart: LocatedHttpRouteBuilder<Cart>) {
            cart.POST("/items").form().handle { request, pathVariables, located, form -> // <4>
                HttpResponse.ok(located.customer + " added " + form.getString("item"))
            }
            cart.GET("/").handle { request, pathVariables, located -> HttpResponse.ok(located.customer) } // <5>
        }
    }
}
// end::clazz[]
