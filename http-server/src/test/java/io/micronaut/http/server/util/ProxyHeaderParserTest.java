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
package io.micronaut.http.server.util;

import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ProxyHeaderParserTest {

    @Test
    void unbalancedQuoteInForwardedValueDoesNotThrow() {
        // a value that is a single double quote used to make trimQuotes call substring(1, 0)
        ProxyHeaderParser parser = assertDoesNotThrow(() -> new ProxyHeaderParser(forwarded("for=\"")));
        assertEquals(List.of("\""), parser.getFor());
    }

    @Test
    void unbalancedLeadingQuoteIsNotTruncated() {
        // "https opens a quoted-string that is never closed; the last character must be kept
        ProxyHeaderParser parser = new ProxyHeaderParser(forwarded("proto=\"https"));
        assertEquals("\"https", parser.getScheme());
    }

    @Test
    void balancedQuotedValueIsStillUnwrapped() {
        ProxyHeaderParser parser = new ProxyHeaderParser(forwarded("for=\"192.0.2.60:4711\""));
        assertEquals(List.of("192.0.2.60:4711"), parser.getFor());
    }

    private static HttpRequest<?> forwarded(String forwarded) {
        MutableHttpRequest<Object> request = HttpRequest.GET("/");
        request.header(HttpHeaders.FORWARDED, forwarded);
        return request;
    }
}
