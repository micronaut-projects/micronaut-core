package io.micronaut.http.server.netty.http2;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.http.ByteBodyHttpResponse;
import io.micronaut.http.ByteBodyHttpResponseWrapper;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.ServerHttpRequest;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.client.RawHttpClient;
import io.micronaut.http.client.RawRequestOptions;
import io.micronaut.http.simple.SimpleHttpHeaders;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * A gRPC-shaped exchange over h2c: the trailers of the request end its stream, and the route
 * answers with a body whose trailers end the response stream.
 */
@MicronautTest
@Property(name = "spec.name", value = "Http2TrailersTest")
@Property(name = "micronaut.server.http-version", value = "2.0")
@Property(name = "micronaut.server.ssl.enabled", value = "false")
@Property(name = "micronaut.http.client.plaintext-mode", value = "h2c_prior_knowledge")
public class Http2TrailersTest {
    private static final long TIMEOUT_SECONDS = 10;

    @Inject
    EmbeddedServer embeddedServer;

    @Inject
    RawHttpClient client;

    @Inject
    GrpcLikeRoute route;

    @Test
    void trailersEndTheRequestAndTheResponse() throws Exception {
        exchange("hello");
    }

    @Test
    void trailersEndARequestWithoutData() throws Exception {
        exchange("");
    }

    /**
     * The trailers of a body end the stream even when the length of the body is known: here the
     * route inspects one half of {@link CloseableByteBody#split()} of the request body and then
     * responds with the other, complete, half.
     */
    @Test
    void trailersEndAnEchoedBodyWithAKnownLength() throws Exception {
        ByteBodyFactory factory = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);
        // the trailers follow once the route has the request, so that the request is streamed
        CompletableFuture<HttpHeaders> requestTrailers = new CompletableFuture<>();
        CloseableByteBody body = factory.withTrailers(factory.copyOf("hello", StandardCharsets.UTF_8), requestTrailers);
        HttpRequest<?> request = HttpRequest.POST(embeddedServer.getURI().resolve("/h2-trailers/echo"), null)
            .contentType("application/grpc")
            .header(HttpHeaders.TE, "trailers");
        CountDownLatch inspecting = new CountDownLatch(1);
        route.inspecting = inspecting;
        CompletableFuture<HttpResponse<?>> pending = Mono.<HttpResponse<?>>from(client.exchange(request, body, null, RawRequestOptions.proxy())).toFuture();
        Assertions.assertTrue(inspecting.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "The route did not get the request");
        requestTrailers.complete(headers("x-checksum", "abc"));
        try (ByteBodyHttpResponse<?> response = (ByteBodyHttpResponse<?>) pending.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            Assertions.assertEquals(200, response.code());
            CompletableFuture<HttpHeaders> trailers = response.byteBody().trailers().toCompletableFuture();
            Assertions.assertEquals("hello", response.byteBody().buffer().get(TIMEOUT_SECONDS, TimeUnit.SECONDS).toString(StandardCharsets.UTF_8));
            Assertions.assertEquals("abc", trailers.get(TIMEOUT_SECONDS, TimeUnit.SECONDS).get("x-checksum"));
        }
    }

    private void exchange(String data) throws Exception {
        ByteBodyFactory factory = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);
        CloseableByteBody body = factory.withTrailers(
            factory.copyOf(data, StandardCharsets.UTF_8),
            CompletableFuture.completedFuture(headers("x-checksum", "abc")));
        HttpRequest<?> request = HttpRequest.POST(embeddedServer.getURI().resolve("/h2-trailers"), null)
            .contentType("application/grpc")
            .header(HttpHeaders.TE, "trailers");
        try (ByteBodyHttpResponse<?> response = (ByteBodyHttpResponse<?>) Mono.from(client.exchange(request, body, null, RawRequestOptions.proxy())).toFuture().get(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            Assertions.assertEquals(200, response.code());
            Assertions.assertEquals("application/grpc", response.getHeaders().get(HttpHeaders.CONTENT_TYPE));
            CompletableFuture<HttpHeaders> trailers = response.byteBody().trailers().toCompletableFuture();
            Assertions.assertEquals(data + ", x-checksum=abc", response.byteBody().buffer().get(TIMEOUT_SECONDS, TimeUnit.SECONDS).toString(StandardCharsets.UTF_8));
            HttpHeaders received = trailers.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            Assertions.assertEquals("0", received.get("grpc-status"));
            Assertions.assertEquals("fine", received.get("grpc-message"));
        }
    }

    private static HttpHeaders headers(String name, String value) {
        return new SimpleHttpHeaders(Map.of(name, value), ConversionService.SHARED);
    }

    @Controller("/h2-trailers")
    @Requires(property = "spec.name", value = "Http2TrailersTest")
    static class GrpcLikeRoute {
        private static final ByteBodyFactory BODY_FACTORY = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);
        /**
         * Counted down when the echo route starts to inspect the request.
         */
        volatile CountDownLatch inspecting = new CountDownLatch(0);

        @Post
        @Consumes(MediaType.ALL)
        @Produces(MediaType.ALL)
        Mono<HttpResponse<?>> exchange(ServerHttpRequest<?> request) {
            CompletableFuture<HttpHeaders> requestTrailers = request.byteBody().trailers().toCompletableFuture();
            return Mono.fromFuture(request.byteBody().buffer())
                .map(body -> body.toString(StandardCharsets.UTF_8))
                .flatMap(body -> Mono.fromFuture(requestTrailers).map(t -> body + ", x-checksum=" + t.get("x-checksum")))
                .map(text -> {
                    HttpHeaders trailers = new SimpleHttpHeaders(Map.of("grpc-status", "0", "grpc-message", "fine"), ConversionService.SHARED);
                    return ByteBodyHttpResponseWrapper.wrap(
                        HttpResponse.ok().contentType("application/grpc"),
                        BODY_FACTORY.withTrailers(BODY_FACTORY.copyOf(text, StandardCharsets.UTF_8), CompletableFuture.completedFuture(trailers)));
                });
        }

        @Post("/echo")
        @Consumes(MediaType.ALL)
        @Produces(MediaType.ALL)
        Mono<HttpResponse<?>> echo(ServerHttpRequest<?> request) {
            // one half is read fully: the other half is then complete, with a known length
            CloseableByteBody inspected = request.byteBody().split(ByteBody.SplitBackpressureMode.FASTEST);
            inspecting.countDown();
            return Mono.fromFuture(inspected.buffer())
                .map(bytes -> {
                    bytes.close();
                    return ByteBodyHttpResponseWrapper.wrap(HttpResponse.ok().contentType("application/grpc"), request.byteBody().move());
                });
        }
    }
}
