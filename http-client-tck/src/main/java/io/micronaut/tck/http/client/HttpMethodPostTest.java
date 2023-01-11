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
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SuppressWarnings({
    "java:S2259", // The tests will show if it's null
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension",
})
public interface HttpMethodPostTest extends AbstractTck {

    String HTTP_METHOD_POST_TEST = "HttpMethodPostTest";

    @Test
    default void postBody() {
        runTest(HTTP_METHOD_POST_TEST, (server, client) ->
            assertEquals("Tim:49", Flux.from(client.exchange(HttpRequest.POST("/post/object-body", new Person("Tim", 49)), String.class)).blockFirst().body())
        );
        runBlockingTest(HTTP_METHOD_POST_TEST, (server, client) ->
                assertEquals("Tim:49", client.exchange(HttpRequest.POST("/post/object-body", new Person("Tim", 49)), String.class).body())
        );
    }

    @Requires(property = "spec.name", value = HTTP_METHOD_POST_TEST)
    @Controller("/post")
    class HttpMethodPostTestController {

        @Post()
        String response() {
            return "ok";
        }

        @Post("/object-body")
        String person(Person person) {
            return person.getName() + ":" + person.getAge();
        }
    }
}
