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
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SuppressWarnings({
    "java:S2259", // The tests will show if it's null
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
})
public interface HttpMethodPostTest extends AbstractTck {

    @Test
    default void postBody() {
        runTest("HttpMethodPostTest", (server, client) ->
            assertEquals("Tim:49", Flux.from(client.exchange(HttpRequest.POST("/post/object-body", new Person("Tim", 49)), String.class)).blockFirst().body())
        );
        runBlockingTest("HttpMethodPostTest", (server, client) ->
                assertEquals("Tim:49", client.exchange(HttpRequest.POST("/post/object-body", new Person("Tim", 49)), String.class).body())
        );
    }
}
