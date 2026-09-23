/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.http.client.tck.tests;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.ByteBodyHttpResponse;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.RawHttpClient;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * A {@link RawHttpClient} relays exchanges of different users (a gateway or proxy route), so it
 * must not carry cookies from one exchange to the next.
 */
@SuppressWarnings({
    "java:S2259", // The tests will show if it's null
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "java:S1192", // It's more readable without the constant
})
class RawCookieTest {
    static final String SPEC_NAME = "RawCookieTest";

    @Test
    void cookiesSetByTheServerAreNotCarriedBetweenExchanges() throws Exception {
        try (ServerUnderTest server = server();
             RawHttpClient client = server.getApplicationContext().createBean(RawHttpClient.class)) {
            try (ByteBodyHttpResponse<?> response = exchange(client, HttpRequest.GET(server.getURL().get() + "/raw-cookie/set-cookie"))) {
                Assertions.assertEquals("session=user-a; Path=/", response.getHeaders().get(HttpHeaders.SET_COOKIE));
            }
            try (ByteBodyHttpResponse<?> response = exchange(client, HttpRequest.GET(server.getURL().get() + "/raw-cookie/cookie"))) {
                Assertions.assertEquals("none", response.byteBody().buffer().get().toString(StandardCharsets.UTF_8));
            }
        }
    }

    @Test
    void cookiesOfARequestAreNotCarriedBetweenExchanges() throws Exception {
        try (ServerUnderTest server = server();
             RawHttpClient client = server.getApplicationContext().createBean(RawHttpClient.class)) {
            try (ByteBodyHttpResponse<?> response = exchange(client, HttpRequest.GET(server.getURL().get() + "/raw-cookie/cookie").cookie(Cookie.of("session", "user-a")))) {
                Assertions.assertEquals("session=user-a", response.byteBody().buffer().get().toString(StandardCharsets.UTF_8));
            }
            try (ByteBodyHttpResponse<?> response = exchange(client, HttpRequest.GET(server.getURL().get() + "/raw-cookie/cookie"))) {
                Assertions.assertEquals("none", response.byteBody().buffer().get().toString(StandardCharsets.UTF_8));
            }
        }
    }

    private static ServerUnderTest server() {
        return ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME);
    }

    private static ByteBodyHttpResponse<?> exchange(RawHttpClient client, HttpRequest<?> request) {
        return Mono.from(client.exchange(request, null, null))
            .cast(ByteBodyHttpResponse.class)
            .block();
    }

    @Controller("/raw-cookie")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class RawCookieController {
        @Get("/set-cookie")
        HttpResponse<?> setCookie() {
            return HttpResponse.ok().header(HttpHeaders.SET_COOKIE, "session=user-a; Path=/");
        }

        @Get(value = "/cookie", produces = MediaType.TEXT_PLAIN)
        String cookie(HttpRequest<?> request) {
            String cookie = request.getHeaders().get(HttpHeaders.COOKIE);
            return cookie == null ? "none" : cookie;
        }
    }
}
