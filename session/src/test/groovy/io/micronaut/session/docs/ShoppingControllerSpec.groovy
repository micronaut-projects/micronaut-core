/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.session.docs

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * @author graemerocher
 * @since 1.0
 */
class ShoppingControllerSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)
    @Shared @AutoCleanup HttpClient httpClient = embeddedServer
                                                        .getApplicationContext()
                                                        .createBean(HttpClient, embeddedServer.getURL())

    void "test session value used on return value"() {

        // tag::view[]
        when: "The shopping cart is retrieved"
        HttpResponse<Cart> response = httpClient.exchange(HttpRequest.GET('/shopping/cart'), Cart) // <1>
                                                .blockFirst()
        Cart cart = response.body()

        then: "The shopping cart is present as well as a session id header"
        response.header(HttpHeaders.AUTHORIZATION_INFO) != null // <2>
        cart != null
        cart.items.isEmpty()
        // end::view[]

        when: "an item is added to the cart using the session id"

        // tag::add[]
        String sessionId = response.header(HttpHeaders.AUTHORIZATION_INFO) // <1>

        response = httpClient.exchange(
                HttpRequest.POST('/shopping/cart/Apple', "")
                        .header(HttpHeaders.AUTHORIZATION_INFO, sessionId), Cart) // <2>
                .blockFirst()
        cart = response.body()
        // end::add[]

        then: "The cart is returned with the added items"
        cart != null
        cart.items.size() == 1

        when: "The session id is used to retrieve the cart"
        response = httpClient.exchange(HttpRequest.GET('/shopping/cart')
                                                  .header(HttpHeaders.AUTHORIZATION_INFO, sessionId), Cart)
                                                  .blockFirst()
        cart = response.body()

        then: "Then the same cart is returned"
        response.header(HttpHeaders.AUTHORIZATION_INFO)
        cart != null
        cart.items.size() == 1
        cart.items[0] == "Apple"
    }
}
