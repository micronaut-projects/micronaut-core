package io.micronaut.docs.server.functional

// tag::imports[]
import io.micronaut.context.annotation.Requires
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpResponse
import io.micronaut.http.form.FormData
import io.micronaut.web.router.builder.HttpRouteBuilder
import io.micronaut.web.router.builder.HttpRoutes
import io.micronaut.web.router.builder.LocatedHttpRouteBuilder
import io.micronaut.web.router.builder.LocatedRoutes
import jakarta.inject.Singleton
// end::imports[]

@Requires(property = "spec.name", value = "FormLocatorRoutesSpec")
// tag::clazz[]
@Singleton
class FormLocatorRoutes implements HttpRoutes {

    /**
     * A located target.
     *
     * @param customer The customer of the cart
     */
    static record Cart(String customer) {
    }

    private final CartRoutes cartRoutes = new CartRoutes()

    @Override
    void routes(HttpRouteBuilder routes) {
        routes.locator("/carts") // <1>
            .form() // <2>
            .locate({ request, pathVariables, FormData form ->
                new Cart(form.find("customer", String).orElse("guest")) // <3>
            }, cartRoutes)
    }

    /**
     * The routes of a located cart.
     */
    static final class CartRoutes implements LocatedRoutes<Cart> {
        @Override
        Argument<Cart> targetType() {
            return Argument.of(Cart)
        }

        @Override
        void routes(LocatedHttpRouteBuilder<Cart> cart) {
            cart.POST("/items").form().handle { request, pathVariables, Cart located, FormData form -> // <4>
                HttpResponse.ok(located.customer() + " added " + form.getString("item"))
            }
            cart.GET("/").handle { request, pathVariables, Cart located -> HttpResponse.ok(located.customer()) } // <5>
        }
    }
}
// end::clazz[]
