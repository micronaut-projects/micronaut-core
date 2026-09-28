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

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.MutableHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.body.MessageBodyWriter;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.codec.CodecException;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A handler route declares no body type, so its writer specializes for the class of the body once,
 * not on every response.
 */
class HandlerRouteSpecificWriterTest {

    private static final String SPEC = "HandlerRouteSpecificWriterTest";
    private static final String COUNTED = "application/x-counted";

    @Test
    void theWriterIsSpecializedOncePerBodyClass() {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("spec.name", SPEC, "micronaut.server.port", -1))) {
            EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
            CountingWriter writer = ctx.getBean(CountingWriter.class);
            try (HttpClient client = ctx.createBean(HttpClient.class, server.getURL())) {
                for (int i = 0; i < 5; i++) {
                    assertEquals("counted " + i, client.toBlocking().retrieve(HttpRequest.GET("/counted/" + i)));
                }
            }
            assertEquals(5, writer.writes.get());
            assertEquals(List.of(Counted.class), writer.specializedBodyClasses());
        }
    }

    record Counted(String value) {
    }

    @Factory
    @Requires(property = "spec.name", value = SPEC)
    static class Routes {
        @Singleton
        HttpRoutes routes() {
            return routes -> routes.GET("/counted/{value}", (request, pathVariables) ->
                HttpResponse.ok(new Counted(pathVariables.get("value", String.class, "")))).produces(MediaType.of(COUNTED));
        }
    }

    @Requires(property = "spec.name", value = SPEC)
    @Singleton
    @Produces(COUNTED)
    static class CountingWriter implements MessageBodyWriter<Object> {
        final Queue<Class<?>> specializations = new ConcurrentLinkedQueue<>();
        final AtomicInteger writes = new AtomicInteger();

        List<Class<?>> specializedBodyClasses() {
            return List.copyOf(specializations);
        }

        @Override
        public MessageBodyWriter<Object> createSpecific(Argument<Object> type) {
            specializations.add(type.getType());
            return this;
        }

        @Override
        public void writeTo(Argument<Object> type, MediaType mediaType, Object object, MutableHeaders outgoingHeaders, OutputStream outputStream) throws CodecException {
            writes.incrementAndGet();
            try {
                outputStream.write(("counted " + ((Counted) object).value()).getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new CodecException("write failed", e);
            }
        }
    }
}
