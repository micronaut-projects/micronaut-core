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
package io.micronaut.http.server.netty;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.http.ByteBodyHttpResponseWrapper;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

/**
 * A streamed response that is not a Netty body, completed on a thread that is neither an event
 * loop nor created by the test, like a response of the JDK HTTP client relayed by a route: the
 * JDK client completes its responses on the common fork join pool. The streaming buffer of the
 * connection is created on its event loop: the leak detector of the test fails resources that
 * are created on a thread that does not inherit the scope of the test.
 */
@MicronautTest
@Property(name = "spec.name", value = "ForeignThreadStreamedResponseTest")
class ForeignThreadStreamedResponseTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Test
    void aStreamedResponseCompletedOnAForeignThreadIsWritten() {
        HttpResponse<String> response = client.toBlocking().exchange(HttpRequest.GET("/foreign-thread-streamed"), String.class);
        Assertions.assertEquals(200, response.code());
        Assertions.assertEquals("foo bar", response.body());
    }

    @Controller("/foreign-thread-streamed")
    @Requires(property = "spec.name", value = "ForeignThreadStreamedResponseTest")
    static class StreamedController {
        private static final ByteBodyFactory BODIES = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);

        @Get(produces = MediaType.TEXT_PLAIN)
        CompletableFuture<HttpResponse<?>> streamed() {
            CloseableByteBody body = BODIES.adapt(Flux.just(copyOf("foo "), copyOf("bar")));
            CompletableFuture<HttpResponse<?>> response = new CompletableFuture<>();
            // a thread that does not inherit the inheritable thread locals, like the threads of
            // the common fork join pool
            new Thread(null, () -> response.complete(ByteBodyHttpResponseWrapper.wrap(HttpResponse.ok().contentType(MediaType.TEXT_PLAIN_TYPE), body)),
                "foreign-thread-streamed", 0, false).start();
            return response;
        }

        private static ReadBuffer copyOf(String s) {
            return BODIES.readBufferFactory().copyOf(s, StandardCharsets.UTF_8);
        }
    }
}
