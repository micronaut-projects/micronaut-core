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
package io.micronaut.http.simple;

import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.cookie.Cookie;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A {@link SimpleHttpRequest} keeps its cookies in a single {@code Cookie} header, a cookie set
 * again replacing the earlier one (issue #11819).
 */
class SimpleHttpRequestCookieHeaderTest {

    @Test
    void cookiesAddedOneByOneShareOneHeader() {
        MutableHttpRequest<?> request = new SimpleHttpRequest<>(HttpMethod.GET, "/", null);
        request.cookie(Cookie.of("a", "1"));
        request.cookie(Cookie.of("b", "2"));
        request.cookie(Cookie.of("a", "3"));

        assertEquals(List.of("a=3; b=2"), request.getHeaders().getAll(HttpHeaders.COOKIE));
    }

    @Test
    void cookiesAddedTogetherShareOneHeader() {
        Set<Cookie> cookies = new LinkedHashSet<>();
        cookies.add(Cookie.of("a", "1"));
        cookies.add(Cookie.of("b", "2"));
        MutableHttpRequest<?> request = new SimpleHttpRequest<>(HttpMethod.GET, "/", null);
        request.cookies(cookies);
        request.cookies(Set.of(Cookie.of("a", "3")));

        assertEquals(List.of("a=3; b=2"), request.getHeaders().getAll(HttpHeaders.COOKIE));
    }

    @Test
    void noCookieNoHeader() {
        MutableHttpRequest<?> request = new SimpleHttpRequest<>(HttpMethod.GET, "/", null);
        request.cookies(Set.of());

        assertTrue(request.getHeaders().getAll(HttpHeaders.COOKIE).isEmpty());
    }
}
