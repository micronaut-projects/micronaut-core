/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.docs.session

// tag::imports[]

import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.session.Session
import io.micronaut.session.annotation.SessionValue

// end::imports[]

// tag::class[]
@Controller("/shopping")
class ShoppingController {
    // end::class[]

    companion object {
        private const val ATTR_CART = "cart" // <1>
    }

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
    internal fun addItem(session: Session, name: String): Cart { // <2>
        require(name.isNotBlank()) { "Name cannot be blank" }
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
        session?.remove(ATTR_CART)
    }
    // end::clear[]

// tag::endclass[]
}
// end::endclass[]