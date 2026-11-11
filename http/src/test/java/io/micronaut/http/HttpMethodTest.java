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
package io.micronaut.http;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class HttpMethodTest {

    /**
     * The idempotent methods of RFC 9110, section 9.2.2, and QUERY, which its specification
     * defines as safe and idempotent.
     */
    private static final Set<HttpMethod> IDEMPOTENT = EnumSet.of(
        HttpMethod.GET,
        HttpMethod.HEAD,
        HttpMethod.PUT,
        HttpMethod.DELETE,
        HttpMethod.OPTIONS,
        HttpMethod.TRACE,
        HttpMethod.QUERY
    );

    @ParameterizedTest
    @EnumSource(HttpMethod.class)
    void isIdempotent(HttpMethod method) {
        assertEquals(IDEMPOTENT.contains(method), method.isIdempotent(), method.name());
    }

    @Test
    void nonIdempotentMethods() {
        assertFalse(HttpMethod.POST.isIdempotent());
        assertFalse(HttpMethod.PATCH.isIdempotent());
        assertFalse(HttpMethod.CONNECT.isIdempotent());
        assertFalse(HttpMethod.CUSTOM.isIdempotent());
    }
}
