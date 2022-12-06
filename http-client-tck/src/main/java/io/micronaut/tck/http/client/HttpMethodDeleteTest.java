/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.tck.http.client;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SuppressWarnings({
    "java:S2259", // The tests will show if it's null
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
})
public interface HttpMethodDeleteTest extends AbstractTck {

    @Test
    default void blockingDeleteMethodMapping() {
        runBlockingTest("HttpMethodDeleteTest", (server, client) -> {
            assertDoesNotThrow(() -> client.exchange(HttpRequest.DELETE("/delete")));
            assertEquals(HttpStatus.NO_CONTENT, client.exchange(HttpRequest.DELETE("/delete")).getStatus());
        });
    }

    @Test
    default void deleteMethodMapping() {
        runTest("HttpMethodDeleteTest", (server, client) ->
            assertEquals(HttpStatus.NO_CONTENT, Flux.from(client.exchange(HttpRequest.DELETE("/delete"))).blockFirst().getStatus())
        );
    }

    @Test
    default void blockingDeleteMethodMappingWithStringResponse() {
        runBlockingTest("HttpMethodDeleteTest", (server, client) ->
            assertEquals("ok", client.exchange(HttpRequest.DELETE("/delete/string-response"), String.class).body())
        );
    }

    @Test
    default void deleteMethodClientMappingWithStringResponse() {
        runTest("HttpMethodDeleteTest", (server, client) -> {
            HttpMethodDeleteClient httpClient = server.getApplicationContext().getBean(HttpMethodDeleteClient.class);
            assertEquals("ok", httpClient.response());
        });
    }

    @Test
    default void deleteMethodMappingWithStringResponse() {
        runTest("HttpMethodDeleteTest", (server, client) ->
            assertEquals("ok", Flux.from(client.exchange(HttpRequest.DELETE("/delete/string-response"), String.class)).blockFirst().body())
        );
    }

    @Test
    default void blockingDeleteMethodMappingWithObjectResponse() {
        runBlockingTest("HttpMethodDeleteTest", (server, client) -> {
            assertEquals(new HttpMethodDeleteTestController.Person("Tim", 49), client.exchange(HttpRequest.DELETE("/delete/object-response"), HttpMethodDeleteTestController.Person.class).body());
            assertEquals("{\"name\":\"Tim\",\"age\":49}", client.exchange(HttpRequest.DELETE("/delete/object-response"), String.class).body());
        });
    }

    @Test
    default void deleteMethodMappingWithObjectResponse() {
        runTest("HttpMethodDeleteTest", (server, client) -> {
            assertEquals(new HttpMethodDeleteTestController.Person("Tim", 49), Flux.from(client.exchange(HttpRequest.DELETE("/delete/object-response"), HttpMethodDeleteTestController.Person.class)).blockFirst().body());
            assertEquals("{\"name\":\"Tim\",\"age\":49}", Flux.from(client.exchange(HttpRequest.DELETE("/delete/object-response"), String.class)).blockFirst().body());
        });
    }
}
