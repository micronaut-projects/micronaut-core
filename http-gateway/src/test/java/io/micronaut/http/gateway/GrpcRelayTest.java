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
package io.micronaut.http.gateway;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.http.ByteBodyHttpResponse;
import io.micronaut.http.ByteBodyHttpResponseWrapper;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.ServerHttpRequest;
import io.micronaut.http.simple.SimpleHttpHeaders;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.client.DefaultHttpClientConfiguration;
import io.micronaut.http.client.HttpVersionSelection;
import io.micronaut.http.client.RawHttpClient;
import io.micronaut.http.client.RawRequestOptions;
import io.micronaut.runtime.server.EmbeddedServer;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * gRPC through a gateway route, with the gRPC framing over h2c on both sides: the messages of a
 * streaming request and response, {@code TE: trailers}, and the {@code grpc-status} in the
 * trailers after the data (RS-19).
 */
class GrpcRelayTest {
    static final String SPEC = "GrpcRelayTest";
    static final ByteBodyFactory FACTORY = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);

    static EmbeddedServer grpcUpstream;
    static EmbeddedServer gateway;

    @BeforeAll
    static void start() {
        grpcUpstream = ApplicationContext.run(EmbeddedServer.class, Map.of(
            "spec.name", SPEC + "-upstream", "micronaut.server.port", -1, "micronaut.server.http-version", "2.0"));
        gateway = ApplicationContext.run(EmbeddedServer.class, Map.of(
            "spec.name", SPEC, "micronaut.server.port", -1, "micronaut.server.http-version", "2.0",
            "micronaut.server.idle-timeout", "5s",
            "test.grpc", grpcUpstream.getURI().toString()));
    }

    @AfterAll
    static void stop() {
        if (gateway != null) {
            gateway.getApplicationContext().close();
        }
        if (grpcUpstream != null) {
            grpcUpstream.getApplicationContext().close();
        }
    }

    static byte[] frames(String... messages) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (String message : messages) {
            byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
            out.write(0);
            out.writeBytes(ByteBuffer.allocate(4).putInt(bytes.length).array());
            out.writeBytes(bytes);
        }
        return out.toByteArray();
    }

    static List<String> messages(byte[] framed) {
        List<String> messages = new ArrayList<>();
        ByteBuffer buffer = ByteBuffer.wrap(framed);
        while (buffer.remaining() >= 5) {
            buffer.get();
            byte[] message = new byte[buffer.getInt()];
            buffer.get(message);
            messages.add(new String(message, StandardCharsets.UTF_8));
        }
        return messages;
    }

    private static RawHttpClient h2cClient(ApplicationContext ctx) throws Exception {
        DefaultHttpClientConfiguration configuration = new DefaultHttpClientConfiguration();
        configuration.setPlaintextMode(HttpVersionSelection.PlaintextMode.H2C_PRIOR_KNOWLEDGE);
        configuration.setAlpnModes(List.of(HttpVersionSelection.ALPN_HTTP_2));
        return RawHttpClient.create(java.net.URI.create("http://localhost:" + gateway.getPort()), configuration);
    }

    @Test
    void rs19AStreamingCallAndItsTrailersAreRelayed() throws Exception {
        try (RawHttpClient client = h2cClient(gateway.getApplicationContext())) {
            HttpRequest<?> request = HttpRequest.create(HttpMethod.POST, "http://localhost:" + gateway.getPort() + "/grpc.Greeter/SayHello")
                .contentType("application/grpc")
                .header("TE", "trailers");
            HttpResponse<?> response = Mono.from(client.exchange(request, FACTORY.adapt(frames("alice", "bob")), null, RawRequestOptions.getDefault()))
                .block(Duration.ofSeconds(10));
            try (ByteBodyHttpResponse<?> body = (ByteBodyHttpResponse<?>) response) {
                assertEquals(200, body.code());
                assertEquals("application/grpc", body.getHeaders().get(HttpHeaders.CONTENT_TYPE));
                byte[] bytes = body.byteBody().buffer().get(10, TimeUnit.SECONDS).toByteArray();
                assertEquals(List.of("hello alice", "hello bob", "te=trailers"), messages(bytes));
                io.micronaut.http.HttpHeaders trailers = body.byteBody().trailers().toCompletableFuture().get(10, TimeUnit.SECONDS);
                assertEquals("0", trailers.get("grpc-status"));
                assertEquals("done", trailers.get("grpc-message"));
            }
        }
    }

    @Test
    void rs19AnErrorStatusInTheTrailersIsRelayed() throws Exception {
        try (RawHttpClient client = h2cClient(gateway.getApplicationContext())) {
            HttpRequest<?> request = HttpRequest.create(HttpMethod.POST, "http://localhost:" + gateway.getPort() + "/grpc.Greeter/SayHello")
                .contentType("application/grpc")
                .header("TE", "trailers");
            HttpResponse<?> response = Mono.from(client.exchange(request, FACTORY.adapt(frames("fail")), null, RawRequestOptions.getDefault()))
                .block(Duration.ofSeconds(10));
            try (ByteBodyHttpResponse<?> body = (ByteBodyHttpResponse<?>) response) {
                assertEquals(200, body.code());
                body.byteBody().buffer().get(10, TimeUnit.SECONDS).close();
                assertEquals("5", body.byteBody().trailers().toCompletableFuture().get(10, TimeUnit.SECONDS).get("grpc-status"));
            }
        }
    }

    /**
     * A gRPC service over h2c: every request message gets a reply message, and a last message
     * reports the TE of the request; the status is in the trailers.
     */
    @Controller
    @Requires(property = "spec.name", value = SPEC + "-upstream")
    static class Greeter {
        @Post("/grpc.Greeter/SayHello")
        @Consumes("application/grpc")
        @Produces("application/grpc")
        CompletableFuture<ByteBodyHttpResponse<?>> sayHello(ServerHttpRequest<?> request) {
            String te = request.getHeaders().get("te");
            return request.byteBody().buffer().thenApply(bytes -> {
                List<String> messages;
                try (bytes) {
                    messages = messages(bytes.toByteArray());
                }
                boolean fail = messages.contains("fail");
                List<String> replies = new ArrayList<>();
                if (!fail) {
                    for (String message : messages) {
                        replies.add("hello " + message);
                    }
                    replies.add("te=" + te);
                }
                io.micronaut.http.HttpHeaders trailers = new SimpleHttpHeaders(
                    fail ? Map.of("grpc-status", "5", "grpc-message", "not found") : Map.of("grpc-status", "0", "grpc-message", "done"),
                    ConversionService.SHARED);
                return ByteBodyHttpResponseWrapper.wrap(
                    HttpResponse.ok().contentType(MediaType.of("application/grpc")),
                    FACTORY.withTrailers(FACTORY.adapt(frames(replies.toArray(String[]::new))), CompletableFuture.completedFuture(trailers)));
            });
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    static class Routes implements GatewayRoutes {
        private final String grpc;

        Routes(@Value("${test.grpc}") String grpc) {
            this.grpc = grpc;
        }

        @Override
        public void routes(GatewayRouteBuilder gateway) {
            gateway.route("grpc").path("/grpc.Greeter/**")
                .httpVersion(HttpVersionSelection.forLegacyVersion(io.micronaut.http.HttpVersion.HTTP_2_0))
                .uri(grpc);
        }
    }
}
