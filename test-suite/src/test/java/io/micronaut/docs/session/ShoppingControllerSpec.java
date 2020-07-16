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

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;


public class ShoppingControllerSpec {

    private static EmbeddedServer server;
    private static RxHttpClient client;

    @BeforeClass
    public static void setupServer() {
        server = ApplicationContext.run(EmbeddedServer.class);
        client = server
                .getApplicationContext()
                .createBean(RxHttpClient.class, server.getURL());
    }

    @AfterClass
    public static void stopServer() {
        if(server != null) {
            server.stop();
        }
        if(client != null) {
            client.stop();
        }
    }

    @Test
    public void testSessionValueUsedOnReturnValue() {
        // tag::view[]
        HttpResponse<Cart> response = client.exchange(HttpRequest.GET("/shopping/cart"), Cart.class) // <1>
                                                .blockingFirst();
        Cart cart = response.body();

        assertNotNull(response.header(HttpHeaders.AUTHORIZATION_INFO)); // <2>
        assertNotNull(cart);
        assertTrue(cart.getItems().isEmpty());
        // end::view[]

        // tag::add[]
        String sessionId = response.header(HttpHeaders.AUTHORIZATION_INFO); // <1>

        response = client.exchange(
                HttpRequest.POST("/shopping/cart/Apple", "")
                        .header(HttpHeaders.AUTHORIZATION_INFO, sessionId), Cart.class) // <2>
                .blockingFirst();
        cart = response.body();
        // end::add[]

        assertNotNull(cart);
        assertEquals(1, cart.getItems().size());

        response = client.exchange(HttpRequest.GET("/shopping/cart")
                                                  .header(HttpHeaders.AUTHORIZATION_INFO, sessionId), Cart.class)
                                                  .blockingFirst();
        cart = response.body();

        response.header(HttpHeaders.AUTHORIZATION_INFO);
        assertNotNull(cart);
        assertEquals(1, cart.getItems().size());
        assertEquals("Apple", cart.getItems().get(0));
    }
}
