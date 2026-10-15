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
import io.micronaut.http.ByteBodyHttpResponse;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.body.CloseableAvailableByteBody;
import io.micronaut.http.client.RawHttpClient;
import io.micronaut.http.client.RawRequestOptions;
import io.micronaut.http.server.ServerResponseAttributes;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Over h2c, a response with the attribute {@link ServerResponseAttributes#SKIP_COMPRESSION} is
 * written as it is, even to a client that accepts a compressed body.
 */
@MicronautTest
@Property(name = "spec.name", value = "Http2SkipCompressionTest")
@Property(name = "micronaut.server.http-version", value = "2.0")
@Property(name = "micronaut.server.ssl.enabled", value = "false")
@Property(name = "micronaut.http.client.plaintext-mode", value = "h2c_prior_knowledge")
class Http2SkipCompressionTest {
    private static final String TEXT = "text ".repeat(1000);

    @Inject
    EmbeddedServer embeddedServer;

    @Inject
    RawHttpClient client;

    @Test
    void aResponseThatSkipsCompressionIsWrittenAsItIs() throws Exception {
        try (ByteBodyHttpResponse<?> response = exchange("/h2-skip-compression/skip")) {
            assertNull(response.getHeaders().get(HttpHeaders.CONTENT_ENCODING));
            try (CloseableAvailableByteBody body = response.byteBody().buffer().get(10, TimeUnit.SECONDS)) {
                assertEquals(TEXT.length(), body.length());
            }
        }
    }

    @Test
    void anotherResponseIsStillCompressed() throws Exception {
        try (ByteBodyHttpResponse<?> response = exchange("/h2-skip-compression/compressed")) {
            assertEquals("gzip", response.getHeaders().get(HttpHeaders.CONTENT_ENCODING));
        }
    }

    private ByteBodyHttpResponse<?> exchange(String path) throws Exception {
        HttpRequest<?> request = HttpRequest.GET(embeddedServer.getURI().resolve(path))
            .header(HttpHeaders.ACCEPT_ENCODING, "gzip");
        return (ByteBodyHttpResponse<?>) Mono.from(client.exchange(request, null, null, RawRequestOptions.proxy()))
            .toFuture().get(10, TimeUnit.SECONDS);
    }

    @Controller("/h2-skip-compression")
    @Requires(property = "spec.name", value = "Http2SkipCompressionTest")
    static class Routes {
        @Get(value = "/skip", produces = MediaType.TEXT_PLAIN)
        HttpResponse<String> skip() {
            MutableHttpResponse<String> response = HttpResponse.ok(TEXT);
            response.setAttribute(ServerResponseAttributes.SKIP_COMPRESSION, true);
            return response;
        }

        @Get(value = "/compressed", produces = MediaType.TEXT_PLAIN)
        String compressed() {
            return TEXT;
        }
    }
}
