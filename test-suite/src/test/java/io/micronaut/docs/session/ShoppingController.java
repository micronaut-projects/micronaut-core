/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.docs.session;

// tag::imports[]

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.session.Session;
import io.micronaut.session.annotation.SessionValue;

import edu.umd.cs.findbugs.annotations.Nullable;
import javax.validation.constraints.NotBlank;
// end::imports[]


// tag::class[]
@Controller("/shopping")
public class ShoppingController {
    private static final String ATTR_CART = "cart"; // <1>
// end::class[]

    // tag::view[]
    @Get("/cart")
    @SessionValue(ATTR_CART) // <1>
    Cart viewCart(@SessionValue @Nullable Cart cart) { // <2>
        if (cart == null) {
            cart = new Cart();
        }
        return cart;
    }
    // end::view[]

    // tag::add[]
    @Post("/cart/{name}")
    Cart addItem(Session session, @NotBlank String name) { // <2>
        Cart cart = session.get(ATTR_CART, Cart.class).orElseGet(() -> { // <3>
            Cart newCart = new Cart();
            session.put(ATTR_CART, newCart); // <4>
            return newCart;
        });
        cart.getItems().add(name);
        return cart;
    }
    // end::add[]

    // tag::clear[]
    @Post("/cart/clear")
    void clearCart(@Nullable Session session) {
        if (session != null) {
            session.remove(ATTR_CART);
        }
    }
    // end::clear[]

// tag::endclass[]
}
// end::endclass[]