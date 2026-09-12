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
package io.micronaut.http.server.tck.tests;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.http.cookie.SameSite;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

import static io.micronaut.http.tck.TestScenario.asserts;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every attribute set on a response cookie reaches the {@code Set-Cookie} header, and several cookies produce
 * several headers.
 */
@SuppressWarnings({"java:S5960", "checkstyle:MissingJavadocType", "checkstyle:DesignForExtension"})
public class CookieAttributesTest {
    public static final String SPEC_NAME = "CookieAttributesTest";

    @Test
    void cookieAttributesAreEmitted() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.GET("/cookie-attributes/full"),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request,
                HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .assertResponse(CookieAttributesTest::assertFullCookie)
                    .build()));
    }

    @Test
    void severalCookiesProduceSeveralSetCookieHeaders() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.GET("/cookie-attributes/two"),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request,
                HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .assertResponse(response -> {
                        List<String> setCookies = response.getHeaders().getAll(HttpHeaders.SET_COOKIE);
                        assertEquals(2, setCookies.size(), () -> "expected two Set-Cookie headers: " + setCookies);
                        assertTrue(setCookies.stream().anyMatch(c -> c.startsWith("first=1")), setCookies::toString);
                        assertTrue(setCookies.stream().anyMatch(c -> c.startsWith("second=2")), setCookies::toString);
                    })
                    .build()));
    }

    private static void assertFullCookie(HttpResponse<?> response) {
        List<String> setCookies = response.getHeaders().getAll(HttpHeaders.SET_COOKIE);
        assertEquals(1, setCookies.size(), () -> "expected one Set-Cookie header: " + setCookies);
        String cookie = setCookies.get(0).toLowerCase(Locale.ROOT);
        assertTrue(cookie.startsWith("session=abc"), cookie);
        assertTrue(cookie.contains("path=/app"), cookie);
        assertTrue(cookie.contains("domain=example.com"), cookie);
        assertTrue(cookie.contains("max-age=3600"), cookie);
        assertTrue(cookie.contains("secure"), cookie);
        assertTrue(cookie.contains("httponly"), cookie);
        assertTrue(cookie.contains("samesite=strict"), cookie);
    }

    @Controller("/cookie-attributes")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class CookieAttributesController {

        @Get("/full")
        @Produces(MediaType.TEXT_PLAIN)
        HttpResponse<String> full() {
            return HttpResponse.ok("ok").cookie(Cookie.of("session", "abc")
                .path("/app")
                .domain("example.com")
                .maxAge(Duration.ofHours(1))
                .secure(true)
                .httpOnly(true)
                .sameSite(SameSite.Strict));
        }

        @Get("/two")
        @Produces(MediaType.TEXT_PLAIN)
        HttpResponse<String> two() {
            return HttpResponse.ok("ok")
                .cookie(Cookie.of("first", "1"))
                .cookie(Cookie.of("second", "2"));
        }
    }
}
