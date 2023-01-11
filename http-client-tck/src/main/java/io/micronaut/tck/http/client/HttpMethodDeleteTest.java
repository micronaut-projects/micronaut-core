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

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Status;
import io.micronaut.http.client.annotation.Client;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SuppressWarnings({
    "java:S2259", // The tests will show if it's null
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension",
})
public interface HttpMethodDeleteTest extends AbstractTck {

    String HTTP_METHOD_DELETE_TEST = "HttpMethodDeleteTest";

    @Test
    default void deleteMethodMapping() {
        runTest(HTTP_METHOD_DELETE_TEST, (server, client) ->
            assertEquals(HttpStatus.NO_CONTENT, Flux.from(client.exchange(HttpRequest.DELETE("/delete"))).blockFirst().getStatus())
        );
        runBlockingTest(HTTP_METHOD_DELETE_TEST, (server, client) -> {
            assertDoesNotThrow(() -> client.exchange(HttpRequest.DELETE("/delete")));
            assertEquals(HttpStatus.NO_CONTENT, client.exchange(HttpRequest.DELETE("/delete")).getStatus());
        });
    }

    @Test
    default void deleteMethodClientMappingWithStringResponse() {
        runTest(HTTP_METHOD_DELETE_TEST, (server, client) -> {
            HttpMethodDeleteClient httpClient = server.getApplicationContext().getBean(HttpMethodDeleteClient.class);
            assertEquals("ok", httpClient.response());
        });
    }

    @Test
    default void deleteMethodMappingWithStringResponse() {
        runTest(HTTP_METHOD_DELETE_TEST, (server, client) ->
            assertEquals("ok", Flux.from(client.exchange(HttpRequest.DELETE("/delete/string-response"), String.class)).blockFirst().body())
        );
        runBlockingTest(HTTP_METHOD_DELETE_TEST, (server, client) ->
            assertEquals("ok", client.exchange(HttpRequest.DELETE("/delete/string-response"), String.class).body())
        );
    }

    @Test
    default void deleteMethodMappingWithObjectResponse() {
        runTest(HTTP_METHOD_DELETE_TEST, (server, client) -> {
            assertEquals(new Person("Tim", 49), Flux.from(client.exchange(HttpRequest.DELETE("/delete/object-response"), Person.class)).blockFirst().body());
            assertEquals("{\"name\":\"Tim\",\"age\":49}", Flux.from(client.exchange(HttpRequest.DELETE("/delete/object-response"), String.class)).blockFirst().body());
        });
        runBlockingTest(HTTP_METHOD_DELETE_TEST, (server, client) -> {
            assertEquals(new Person("Tim", 49), client.exchange(HttpRequest.DELETE("/delete/object-response"), Person.class).body());
            assertEquals("{\"name\":\"Tim\",\"age\":49}", client.exchange(HttpRequest.DELETE("/delete/object-response"), String.class).body());
        });
    }

    @Requires(property = "spec.name", value = HTTP_METHOD_DELETE_TEST)
    @Controller("/delete")
    class HttpMethodDeleteTestController {

        @Delete
        @Status(HttpStatus.NO_CONTENT)
        void index() {
            // no-op
        }

        @Delete("/string-response")
        String response() {
            return "ok";
        }

        @Delete("/object-response")
        Person person() {
            return new Person("Tim", 49);
        }
    }

    @Requires(property = "spec.name", value = HTTP_METHOD_DELETE_TEST)
    @Client("/delete")
    interface HttpMethodDeleteClient {

        HttpResponse<Void> index();

        @Delete("/string-response")
        String response();

        @Delete("/object-response")
        Person person();
    }
}
