package io.micronaut.http.server.netty.http2;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.io.buffer.ReadBufferFactory;
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
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * A bidirectional-streaming, gRPC-shaped exchange over h2c: the route starts its response as soon
 * as it has the request headers and answers each request message while the request stream is
 * still open. The response trailers, sent after the last response message, depend on the request
 * trailers. This is the HTTP/2 behaviour a gRPC server needs for client- and bidirectional-streaming
 * calls.
 */
@MicronautTest
@Property(name = "spec.name", value = "Http2FullDuplexTest")
@Property(name = "micronaut.server.http-version", value = "2.0")
@Property(name = "micronaut.server.ssl.enabled", value = "false")
@Property(name = "micronaut.http.client.plaintext-mode", value = "h2c_prior_knowledge")
public class Http2FullDuplexTest {
    private static final long TIMEOUT_SECONDS = 10;

    @Inject
    EmbeddedServer embeddedServer;

    @Inject
    RawHttpClient client;

    @Test
    void eachRequestMessageIsAnsweredWhileTheRequestStreamIsOpen() throws Exception {
        ByteBodyFactory factory = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);
        Sinks.Many<ReadBuffer> requestMessages = Sinks.many().unicast().onBackpressureBuffer();
        CompletableFuture<HttpHeaders> requestTrailers = new CompletableFuture<>();
        CloseableByteBody body = factory.withTrailers(factory.adapt(requestMessages.asFlux()), requestTrailers);
        HttpRequest<?> request = HttpRequest.POST(embeddedServer.getURI().resolve("/h2-duplex"), null)
            .contentType("application/grpc")
            .header(HttpHeaders.TE, "trailers");

        CompletableFuture<HttpResponse<?>> pending = Mono.<HttpResponse<?>>from(client.exchange(request, body, null, RawRequestOptions.proxy())).toFuture();
        requestMessages.tryEmitNext(message("one"));

        try (ByteBodyHttpResponse<?> response = (ByteBodyHttpResponse<?>) pending.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            Assertions.assertEquals(200, response.code());
            Assertions.assertEquals("application/grpc", response.getHeaders().get(HttpHeaders.CONTENT_TYPE));
            CompletableFuture<HttpHeaders> responseTrailers = response.byteBody().trailers().toCompletableFuture();
            BlockingQueue<String> responseMessages = new LinkedBlockingQueue<>();
            CompletableFuture<Void> responseEnded = new CompletableFuture<>();
            response.byteBody().toByteArrayPublisher().subscribe(new Subscriber<>() {
                @Override
                public void onSubscribe(Subscription s) {
                    s.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(byte[] bytes) {
                    responseMessages.add(new String(bytes, StandardCharsets.UTF_8));
                }

                @Override
                public void onError(Throwable t) {
                    responseEnded.completeExceptionally(t);
                }

                @Override
                public void onComplete() {
                    responseEnded.complete(null);
                }
            });

            // the first answer arrives before the client sends anything else
            Assertions.assertEquals("echo:one", responseMessages.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            requestMessages.tryEmitNext(message("two"));
            Assertions.assertEquals("echo:two", responseMessages.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            Assertions.assertFalse(responseEnded.isDone(), "The response ended before the request");

            requestTrailers.complete(headers(Map.of("x-count", "2")));
            requestMessages.tryEmitComplete();

            responseEnded.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            HttpHeaders trailers = responseTrailers.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            Assertions.assertEquals("0", trailers.get("grpc-status"));
            Assertions.assertEquals("2", trailers.get("x-count"));
            Assertions.assertTrue(responseMessages.isEmpty(), "Unexpected response messages: " + responseMessages);
        }
    }

    /**
     * A gRPC error is reported in a trailers-only response: headers carry the status and the
     * stream ends without data.
     */
    @Test
    void anErrorIsReportedInATrailersOnlyResponse() throws Exception {
        ByteBodyFactory factory = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);
        HttpRequest<?> request = HttpRequest.POST(embeddedServer.getURI().resolve("/h2-duplex/unimplemented"), null)
            .contentType("application/grpc")
            .header(HttpHeaders.TE, "trailers");
        CloseableByteBody body = factory.copyOf("ignored", StandardCharsets.UTF_8);
        try (ByteBodyHttpResponse<?> response = (ByteBodyHttpResponse<?>) Mono.from(client.exchange(request, body, null, RawRequestOptions.proxy())).toFuture().get(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            Assertions.assertEquals(200, response.code());
            Assertions.assertEquals("12", response.getHeaders().get("grpc-status"));
            Assertions.assertEquals("unknown method", response.getHeaders().get("grpc-message"));
            Assertions.assertEquals(0, response.byteBody().buffer().get(TIMEOUT_SECONDS, TimeUnit.SECONDS).toByteArray().length);
        }
    }

    private static ReadBuffer message(String text) {
        return ReadBufferFactory.getJdkFactory().copyOf(text, StandardCharsets.UTF_8);
    }

    private static HttpHeaders headers(Map<String, String> values) {
        return new SimpleHttpHeaders(values, ConversionService.SHARED);
    }

    @Controller("/h2-duplex")
    @Requires(property = "spec.name", value = "Http2FullDuplexTest")
    static class BidiStreamingRoute {
        private static final ByteBodyFactory BODY_FACTORY = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);

        @Post
        @Consumes(MediaType.ALL)
        @Produces(MediaType.ALL)
        HttpResponse<?> bidi(ServerHttpRequest<?> request) {
            // claim the request body without buffering it: the response starts right away and
            // each request message is answered as it arrives
            CloseableByteBody requestBody = request.byteBody().move();
            CompletableFuture<HttpHeaders> requestTrailers = requestBody.trailers().toCompletableFuture();
            Flux<ReadBuffer> answers = Flux.from(requestBody.toByteArrayPublisher())
                .map(bytes -> ReadBufferFactory.getJdkFactory().copyOf("echo:" + new String(bytes, StandardCharsets.UTF_8), StandardCharsets.UTF_8))
                .doFinally(signal -> requestBody.close());
            CompletableFuture<HttpHeaders> responseTrailers = requestTrailers.thenApply(t ->
                headers(Map.of("grpc-status", "0", "x-count", String.valueOf(t.get("x-count")))));
            return ByteBodyHttpResponseWrapper.wrap(
                HttpResponse.ok().contentType("application/grpc"),
                BODY_FACTORY.withTrailers(BODY_FACTORY.adapt(answers), responseTrailers));
        }

        @Post("/unimplemented")
        @Consumes(MediaType.ALL)
        @Produces(MediaType.ALL)
        HttpResponse<?> unimplemented(ServerHttpRequest<?> request) {
            request.byteBody().move().close();
            return HttpResponse.ok()
                .contentType("application/grpc")
                .header("grpc-status", "12")
                .header("grpc-message", "unknown method");
        }
    }
}
