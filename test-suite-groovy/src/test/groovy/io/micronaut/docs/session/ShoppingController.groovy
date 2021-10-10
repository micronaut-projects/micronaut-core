package io.micronaut.docs.session

// tag::imports[]

import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.session.Session
import io.micronaut.session.annotation.SessionValue

import javax.annotation.Nullable
import javax.validation.constraints.NotBlank
// end::imports[]

// tag::class[]
@Controller("/shopping")
class ShoppingController {
    static final String ATTR_CART = "cart" // <1>
// end::class[]

    // tag::view[]
    @Get("/cart")
    @SessionValue("cart") // <1>
    Cart viewCart(@SessionValue @Nullable Cart cart) { // <2>
        if (cart == null) {
            cart = new Cart()
        }
        cart
    }
    // end::view[]

    // tag::add[]
    @Post("/cart/{name}")
    Cart addItem(Session session, @NotBlank String name) { // <2>
        Cart cart = session.get(ATTR_CART, Cart.class).orElseGet({ ->  // <3>
            Cart newCart = new Cart()
            session.put(ATTR_CART, newCart) // <4>
            newCart
        })
        cart.getItems().add(name)
        cart
    }
    // end::add[]

    // tag::clear[]
    @Post("/cart/clear")
    void clearCart(@Nullable Session session) {
        if (session != null) {
            session.remove(ATTR_CART)
        }
    }
    // end::clear[]

// tag::endclass[]
}
// end::endclass[]