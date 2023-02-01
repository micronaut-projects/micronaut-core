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
import io.micronaut.http.BasicAuth;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SuppressWarnings({
    "java:S2259", // The tests will show if it's null
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension",
})
public interface AuthTest extends AbstractTck {

    String AUTH_TEST = "AuthTest";

    @Test
    default void basicAuth() {
        runTest(AUTH_TEST, (server, client) -> {
            var exchange = Flux.from(client.exchange(HttpRequest.GET("/auth-test").basicAuth("Tim", "Yates"), String.class)).blockFirst();
            assertEquals(HttpStatus.OK, exchange.getStatus());
            assertEquals("Tim:Yates", exchange.body());
        });
        runBlockingTest(AUTH_TEST, (server, client) -> {
            var exchange = client.exchange(HttpRequest.GET("/auth-test").basicAuth("Tim", "Yates"), String.class);
            assertEquals(HttpStatus.OK, exchange.getStatus());
            assertEquals("Tim:Yates", exchange.body());
        });
    }

    @Controller("/auth-test")
    @Requires(property = "spec.name", value = AUTH_TEST)
    class AuthController {

        @Get
        String get(BasicAuth auth) {
            return auth.getUsername() + ":" + auth.getPassword();
        }
    }
}
