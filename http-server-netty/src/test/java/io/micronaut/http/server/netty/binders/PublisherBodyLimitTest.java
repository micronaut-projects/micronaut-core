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
package io.micronaut.http.server.netty.binders;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PublisherBodyLimitTest {
    private static final int MAX_REQUEST_SIZE = 1024 * 1024;

    @ParameterizedTest
    @ValueSource(strings = {
        "/publisher-limit/bytes-mono", "/publisher-limit/bytes-flux",
        "/publisher-limit/buffer-mono", "/publisher-limit/buffer-flux",
        "/publisher-limit/string-mono", "/publisher-limit/string-flux"
    })
    void aBodyPublisherOverTheLimitIsAnsweredWith413(String path) throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of(
            "spec.name", "PublisherBodyLimitTest",
            "micronaut.server.max-request-size", MAX_REQUEST_SIZE
        ));
             EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start()) {
            HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(server.getURL() + path))
                    .header("Content-Type", "text/plain")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(new byte[MAX_REQUEST_SIZE + 200_000])).build(),
                HttpResponse.BodyHandlers.ofString());
            assertEquals(413, response.statusCode(), response.body());
        }
    }

    @Controller("/publisher-limit")
    @Requires(property = "spec.name", value = "PublisherBodyLimitTest")
    static class PublisherLimitController {
        @Post(value = "/bytes-mono", consumes = MediaType.ALL, produces = MediaType.TEXT_PLAIN)
        Mono<String> bytesMono(@Body Publisher<byte[]> body) {
            return Flux.from(body).map(b -> b.length).reduce(0, Integer::sum).map(String::valueOf);
        }

        @Post(value = "/bytes-flux", consumes = MediaType.ALL, produces = MediaType.TEXT_PLAIN)
        Flux<String> bytesFlux(@Body Publisher<byte[]> body) {
            return Flux.from(body).map(b -> b.length).reduce(0, Integer::sum).map(String::valueOf).flux();
        }

        @Post(value = "/buffer-mono", consumes = MediaType.ALL, produces = MediaType.TEXT_PLAIN)
        Mono<String> bufferMono(@Body Publisher<ByteBuffer> body) {
            return Flux.from(body).map(ByteBuffer::remaining).reduce(0, Integer::sum).map(String::valueOf);
        }

        @Post(value = "/buffer-flux", consumes = MediaType.ALL, produces = MediaType.TEXT_PLAIN)
        Flux<String> bufferFlux(@Body Publisher<ByteBuffer> body) {
            return Flux.from(body).map(ByteBuffer::remaining).reduce(0, Integer::sum).map(String::valueOf).flux();
        }

        @Post(value = "/string-mono", consumes = MediaType.ALL, produces = MediaType.TEXT_PLAIN)
        Mono<String> stringMono(@Body Publisher<String> body) {
            return Flux.from(body).map(String::length).reduce(0, Integer::sum).map(String::valueOf);
        }

        @Post(value = "/string-flux", consumes = MediaType.ALL, produces = MediaType.TEXT_PLAIN)
        Flux<String> stringFlux(@Body Publisher<String> body) {
            return Flux.from(body).map(String::length).reduce(0, Integer::sum).map(String::valueOf).flux();
        }
    }
}
