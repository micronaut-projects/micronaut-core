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
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.web.router.builder.DirectRouteBuilder;
import io.micronaut.web.router.builder.HttpDirectRoutes;
import io.micronaut.web.router.builder.RouteCondition;
import io.micronaut.web.router.builder.ValueMatcher;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The responses given as values to direct routes, prepared once with their headers encoded as
 * they were declared, e.g. in mixed case, answer HTTP/2 streams, whose header names are lower
 * case.
 */
@MicronautTest
@Property(name = "spec.name", value = "DirectRouteHttp2PreparedTest")
@Property(name = "micronaut.server.http-version", value = "2.0")
@Property(name = "micronaut.server.ssl.enabled", value = "false")
@Property(name = "micronaut.http.client.plaintext-mode", value = "h2c_prior_knowledge")
@Property(name = "micronaut.http.client.http-version", value = "2.0")
class DirectRouteHttp2PreparedTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Test
    void aPreparedResponseWithMixedCaseHeadersAnswersAnHttp2Stream() {
        for (int i = 0; i < 3; i++) {
            HttpResponse<String> response = client.toBlocking().exchange(HttpRequest.GET("/h2-prepared/version"), String.class);
            assertEquals(HttpStatus.OK, response.getStatus());
            assertEquals("current", response.body());
            assertEquals("\"v1\"", response.getHeaders().get(HttpHeaders.ETAG));
            assertEquals("yes", response.getHeaders().get("X-Mixed-Case"));
            assertEquals("7", response.getHeaders().get(HttpHeaders.CONTENT_LENGTH));
            assertNotNull(response.getHeaders().get(HttpHeaders.DATE));
        }
        HttpResponse<String> notModified = client.toBlocking().exchange(HttpRequest.GET("/h2-prepared/version")
            .header(HttpHeaders.IF_NONE_MATCH, "W/\"v1\""), String.class);
        assertEquals(HttpStatus.NOT_MODIFIED, notModified.getStatus());
        assertEquals("\"v1\"", notModified.getHeaders().get(HttpHeaders.ETAG));
    }

    @Singleton
    @Requires(property = "spec.name", value = "DirectRouteHttp2PreparedTest")
    static class Routes implements HttpDirectRoutes {
        @Override
        public void routes(DirectRouteBuilder routes) {
            routes.GET("/h2-prepared/version")
                .where(RouteCondition.header(HttpHeaders.IF_NONE_MATCH, ValueMatcher.entityTag("\"v1\"")))
                .order(-1)
                .respond(HttpResponse.notModified().header("ETag", "\"v1\""));
            routes.GET("/h2-prepared/version", HttpResponse.ok("current")
                .contentType(MediaType.TEXT_PLAIN_TYPE)
                .header("ETag", "\"v1\"")
                .header("X-Mixed-Case", "yes"));
        }
    }
}
