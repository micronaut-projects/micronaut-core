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
package io.micronaut.http.server.netty.http2;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.web.router.builder.DirectRouteBuilder;
import io.micronaut.web.router.builder.HttpDirectRoutes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The {@code Content-Length} of a {@code HEAD} response of a direct route over h2c: the one a
 * {@code HEAD} route declares, and none for a status without a body, which the HTTP/2 handler
 * does not remove itself.
 */
@MicronautTest
@Property(name = "spec.name", value = "DirectRouteHttp2HeadTest")
@Property(name = "micronaut.server.http-version", value = "2.0")
@Property(name = "micronaut.server.ssl.enabled", value = "false")
@Property(name = "micronaut.http.client.plaintext-mode", value = "h2c_prior_knowledge")
@Property(name = "micronaut.http.client.http-version", value = "2.0")
class DirectRouteHttp2HeadTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Test
    void aHeadRouteDeclaresTheLengthOfTheGetResponse() {
        HttpResponse<String> response = client.toBlocking().exchange(HttpRequest.HEAD("/h2-head/length"), String.class);
        assertEquals(HttpStatus.OK, response.getStatus());
        assertEquals("42", response.getHeaders().get(HttpHeaders.CONTENT_LENGTH));
    }

    @Test
    void aStatusWithoutABodyHasNoLength() {
        HttpResponse<String> response = client.toBlocking().exchange(HttpRequest.HEAD("/h2-head/no-content"), String.class);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatus());
        assertNull(response.getHeaders().get(HttpHeaders.CONTENT_LENGTH));
    }

    @Singleton
    @Requires(property = "spec.name", value = "DirectRouteHttp2HeadTest")
    static class Routes implements HttpDirectRoutes {
        @Override
        public void routes(DirectRouteBuilder routes) {
            routes.HEAD("/h2-head/length", HttpResponse.ok().header(HttpHeaders.CONTENT_LENGTH, "42"));
            routes.GET("/h2-head/no-content", HttpResponse.noContent());
        }
    }
}
