package io.micronaut.docs.session

// tag::imports[]

import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.session.Session
import io.micronaut.session.annotation.SessionValue
import javax.validation.constraints.NotBlank

// end::imports[]

/**
 * @author graemerocher
 * @since 1.0
 */
// tag::class[]
@Controller("/shopping")
class ShoppingController {
    // end::class[]

    // tag::view[]
    @Get("/cart")
    @SessionValue(ATTR_CART) // <1>
    internal fun viewCart(@SessionValue cart: Cart?): Cart { // <2>
        var cart = cart
        if (cart == null) {
            cart = Cart()
        }
        return cart
    }
    // end::view[]

    // tag::add[]
    @Post("/cart/{name}")
    internal fun addItem(session: Session, @NotBlank name: String): Cart { // <2>
        val cart = session.get(ATTR_CART, Cart::class.java).orElseGet({
            // <3>
            val newCart = Cart()
            session.put(ATTR_CART, newCart) // <4>
            newCart
        })
        cart.items.add(name)
        return cart
    }
    // end::add[]

    // tag::clear[]
    @Post("/cart/clear")
    internal fun clearCart(session: Session?) {
        if (session != null) {
            session!!.remove(ATTR_CART)
        }
    }

    companion object {
        private const val ATTR_CART = "cart" // <1>
    }
    // end::clear[]
}
