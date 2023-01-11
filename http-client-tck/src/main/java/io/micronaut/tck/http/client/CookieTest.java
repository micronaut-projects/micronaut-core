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
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.CookieValue;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.cookie.Cookie;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SuppressWarnings({
    "java:S2259", // The tests will show if it's null
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
})
public interface CookieTest extends AbstractTck {

    String COOKIE_TEST = "CookieTest";

    @Test
    default void cookieBinding() {
        runTest(COOKIE_TEST, (server, client) -> {
            var exchange = Flux.from(client.exchange(HttpRequest.GET("/cookies-test/bind")
                .cookie(Cookie.of("one", "foo"))
                .cookie(Cookie.of("two", "bar")), String.class)).blockFirst();
            assertEquals(HttpStatus.OK, exchange.getStatus());
            assertEquals("{\"one\":\"foo\",\"two\":\"bar\"}", exchange.body());
        });
        runBlockingTest(COOKIE_TEST, (server, client) -> {
            var exchange = client.exchange(HttpRequest.GET("/cookies-test/bind")
                .cookie(Cookie.of("one", "foo"))
                .cookie(Cookie.of("two", "bar")), String.class);
            assertEquals(HttpStatus.OK, exchange.getStatus());
            assertEquals("{\"one\":\"foo\",\"two\":\"bar\"}", exchange.body());
        });
    }

    @Test
    default void getCookiesFromRequest() {
        runTest(COOKIE_TEST, (server, client) -> {
            var exchange = Flux.from(client.exchange(HttpRequest.GET("/cookies-test/all")
                .cookie(Cookie.of("one", "foo"))
                .cookie(Cookie.of("two", "bar")), String.class)).blockFirst();
            assertEquals(HttpStatus.OK, exchange.getStatus());
            assertEquals("{\"one\":\"foo\",\"two\":\"bar\"}", exchange.body());
        });
        runBlockingTest(COOKIE_TEST, (server, client) -> {
            var exchange = client.exchange(HttpRequest.GET("/cookies-test/all")
                .cookie(Cookie.of("one", "foo"))
                .cookie(Cookie.of("two", "bar")), String.class);
            assertEquals(HttpStatus.OK, exchange.getStatus());
            assertEquals("{\"one\":\"foo\",\"two\":\"bar\"}", exchange.body());
        });
    }

    @Test
    default void testNoCookies() {
        runTest(COOKIE_TEST, (server, client) -> {
            var exchange = Flux.from(client.exchange("/cookies-test/all", String.class)).blockFirst();
            assertEquals(HttpStatus.OK, exchange.getStatus());
            assertEquals("{}", exchange.body());
        });
        runBlockingTest(COOKIE_TEST, (server, client) -> {
            var exchange = client.exchange("/cookies-test/all", String.class);
            assertEquals(HttpStatus.OK, exchange.getStatus());
            assertEquals("{}", exchange.body());
        });
    }

    @Controller("/cookies-test")
    @Requires(property = "spec.name", value = COOKIE_TEST)
    static class CookieController {

        @Get(uri = "/all")
        Map<String, String> all(HttpRequest request) {
            Map<String, String> map = new HashMap<>();
            for (String cookieName : request.getCookies().names()) {
                map.put(cookieName, request.getCookies().get(cookieName).getValue());
            }
            return map;
        }

        @Get(uri = "/bind")
        Map<String, String> all(@CookieValue String one, @CookieValue String two) {
            return CollectionUtils.mapOf(
                "one", one,
                "two", two
            );
        }
    }
}
