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
package io.micronaut.docs.http.client.raw;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.io.socket.SocketUtils;
import io.micronaut.http.ByteBodyHttpResponse;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableAvailableByteBody;
import io.micronaut.http.client.AsyncRawHttpClient;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@MicronautTest
@io.micronaut.context.annotation.Property(name = "spec.name", value = "RawRetryTest")
class RawRetryTest {
    @Inject
    EmbeddedServer server;

    @Test
    void theBodyGoesToTheFallbackWhenThePrimaryIsDown() throws Exception {
        try (AsyncRawHttpClient primary = AsyncRawHttpClient.create(URI.create("http://127.0.0.1:" + SocketUtils.findAvailableTcpPort()));
             AsyncRawHttpClient fallback = AsyncRawHttpClient.create(server.getURI())) {
            ByteBodyFactory factory = ByteBodyFactory.createDefault(io.micronaut.core.io.buffer.ByteArrayBufferFactory.INSTANCE);
            HttpResponse<?> response = new RawRetry(primary, fallback)
                .exchange(HttpRequest.create(HttpMethod.POST, "/raw-retry/echo").contentType(MediaType.TEXT_PLAIN_TYPE), factory.copyOf("hello", StandardCharsets.UTF_8))
                .toCompletableFuture().get(10, TimeUnit.SECONDS);
            Assertions.assertEquals(200, response.code());
            try (ByteBodyHttpResponse<?> raw = (ByteBodyHttpResponse<?>) response;
                 CloseableAvailableByteBody bytes = raw.byteBody().buffer().get(10, TimeUnit.SECONDS)) {
                Assertions.assertEquals("hello", bytes.toString(StandardCharsets.UTF_8));
            }
        }
    }

    @Requires(property = "spec.name", value = "RawRetryTest")
    @Controller("/raw-retry")
    static class EchoController {
        @Post(uri = "/echo", processes = "text/plain")
        String echo(@Body String body) {
            return body;
        }
    }
}
