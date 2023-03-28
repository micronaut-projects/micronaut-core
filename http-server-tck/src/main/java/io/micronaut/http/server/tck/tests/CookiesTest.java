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
package io.micronaut.http.server.tck.tests;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.CookieValue;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static io.micronaut.http.tck.TestScenario.asserts;

@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class CookiesTest {
    public static final String SPEC_NAME = "CookiesTest";

    @Test
    void testCookieBind() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.GET("/cookies-test/bind")
                .cookie(Cookie.of("one", "foo"))
                .cookie(Cookie.of("two", "bar")),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("{\"one\":\"foo\",\"two\":\"bar\"}")
                .build()));
    }

    @Test
    void testGetCookiesMethod() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.GET("/cookies-test/all")
                .cookie(Cookie.of("one", "foo"))
                .cookie(Cookie.of("two", "bar")),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("{\"one\":\"foo\",\"two\":\"bar\"}")
                .build()));
    }

    @Test
    void testNoCookie() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.GET("/cookies-test/all"),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("{}")
                .build()));
    }

    @Controller("/cookies-test")
    @Requires(property = "spec.name", value = SPEC_NAME)
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
