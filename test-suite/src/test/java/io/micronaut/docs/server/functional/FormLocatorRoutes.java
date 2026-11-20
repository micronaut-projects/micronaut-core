package io.micronaut.docs.server.functional;

// tag::imports[]
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpResponse;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import io.micronaut.web.router.builder.LocatedHttpRouteBuilder;
import io.micronaut.web.router.builder.LocatedRoutes;
import jakarta.inject.Singleton;
// end::imports[]

@Requires(property = "spec.name", value = "FormLocatorRoutesTest")
// tag::clazz[]
@Singleton
public class FormLocatorRoutes implements HttpRoutes {

    /**
     * A located target.
     *
     * @param customer The customer of the cart
     */
    public record Cart(String customer) {
    }

    private final CartRoutes cartRoutes = new CartRoutes();

    @Override
    public void routes(HttpRouteBuilder routes) {
        routes.locator("/carts") // <1>
            .form() // <2>
            .locate((request, pathVariables, form) ->
                new Cart(form.find("customer", String.class).orElse("guest")), cartRoutes); // <3>
    }

    /**
     * The routes of a located cart.
     */
    static final class CartRoutes implements LocatedRoutes<Cart> {

        @Override
        public Argument<Cart> targetType() {
            return Argument.of(Cart.class);
        }

        @Override
        public void routes(LocatedHttpRouteBuilder<Cart> cart) {
            cart.POST("/items").form().handle((request, pathVariables, located, form) -> // <4>
                HttpResponse.ok(located.customer() + " added " + form.getString("item")));
            cart.GET("/").handle((request, pathVariables, located) -> HttpResponse.ok(located.customer())); // <5>
        }
    }
}
// end::clazz[]
