package io.micronaut.http.client.netty;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.io.buffer.ReadBufferFactory;
import io.micronaut.http.ByteBodyHttpResponse;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.client.RawHttpClient;
import io.micronaut.http.client.RawRequestOptions;
import io.micronaut.http.client.exceptions.ReadTimeoutException;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * The response timeout of a raw exchange pauses while the request body is uploaded: the time of
 * a slow upload does not count, over HTTP/1.1 and HTTP/2, while the wait for a connection does.
 */
class RawResponseTimeoutAfterUploadTest {
    private static final Duration RESPONSE_TIMEOUT = Duration.ofMillis(500);

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void aSlowUploadDoesNotCountTowardsTheResponseTimeout(int version) throws Exception {
        try (ApplicationContext ctx = start(version);
             EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
             RawHttpClient client = ctx.createBean(RawHttpClient.class)) {
            // 10 parts, 100 ms apart: the upload takes twice the response timeout
            try (ByteBodyHttpResponse<?> response = exchange(client, server, slowBody(10), 0)) {
                Assertions.assertEquals(200, response.code());
                Assertions.assertEquals("10", response.byteBody().buffer().get().toString(StandardCharsets.UTF_8));
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void aSlowResponseAfterASlowUploadStillTimesOut(int version) throws Exception {
        try (ApplicationContext ctx = start(version);
             EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
             RawHttpClient client = ctx.createBean(RawHttpClient.class)) {
            Assertions.assertThrows(ReadTimeoutException.class, () -> exchange(client, server, slowBody(3), 3000).close());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void aSlowResponseToAFullBodyStillTimesOut(int version) throws Exception {
        try (ApplicationContext ctx = start(version);
             EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
             RawHttpClient client = ctx.createBean(RawHttpClient.class)) {
            CloseableByteBody body = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE).adapt("xyz".getBytes(StandardCharsets.UTF_8));
            Assertions.assertThrows(ReadTimeoutException.class, () -> exchange(client, server, body, 3000).close());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void aSlowUploadAfterAContinueDoesNotCountTowardsTheResponseTimeout(int version) throws Exception {
        try (ApplicationContext ctx = start(version);
             EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
             RawHttpClient client = ctx.createBean(RawHttpClient.class)) {
            try (ByteBodyHttpResponse<?> response = exchange(client, server, slowBody(10), 0, true)) {
                Assertions.assertEquals(200, response.code());
                Assertions.assertEquals("10", response.byteBody().buffer().get().toString(StandardCharsets.UTF_8));
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void aSlowResponseToAFullBodyAfterAContinueStillTimesOut(int version) throws Exception {
        try (ApplicationContext ctx = start(version);
             EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
             RawHttpClient client = ctx.createBean(RawHttpClient.class)) {
            CloseableByteBody body = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE).adapt("xyz".getBytes(StandardCharsets.UTF_8));
            Assertions.assertThrows(ReadTimeoutException.class, () -> exchange(client, server, body, 3000, true).close());
        }
    }

    @Test
    void theWaitForAConnectionCountsTowardsTheResponseTimeout() throws Exception {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of(
                "spec.name", "RawResponseTimeoutAfterUploadTest",
                "micronaut.http.client.read-timeout", "10s",
                "micronaut.http.client.pool.enabled", true,
                "micronaut.http.client.pool.max-concurrent-http1-connections", 1,
                "micronaut.server.idle-timeout", "5s"));
             EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
             RawHttpClient client = ctx.createBean(RawHttpClient.class)) {
            // the only connection is busy for 3s
            CloseableByteBody first = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE).adapt("a".getBytes(StandardCharsets.UTF_8));
            HttpRequest<?> busy = HttpRequest.create(HttpMethod.POST, server.getURI().resolve("/raw-response-timeout-after-upload/count?delay=3000").toString())
                .contentType(MediaType.TEXT_PLAIN_TYPE);
            java.util.concurrent.CompletableFuture<? extends HttpResponse<?>> held = Mono.from(client.exchange(busy, first, null)).toFuture();
            Thread.sleep(200);
            CloseableByteBody second = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE).adapt("b".getBytes(StandardCharsets.UTF_8));
            long start = System.nanoTime();
            Assertions.assertThrows(ReadTimeoutException.class, () -> exchange(client, server, second, 0).close());
            Assertions.assertTrue(Duration.ofNanos(System.nanoTime() - start).compareTo(Duration.ofSeconds(2)) < 0,
                "The response timeout waited for the connection");
            ((ByteBodyHttpResponse<?>) held.get()).close();
        }
    }

    private static CloseableByteBody slowBody(int parts) {
        return ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE).adapt(
            Flux.range(0, parts)
                .delayElements(Duration.ofMillis(100))
                .map(i -> (ReadBuffer) ReadBufferFactory.getJdkFactory().copyOf("x", StandardCharsets.UTF_8)));
    }

    private static ApplicationContext start(int version) {
        boolean ssl = version == 2;
        return ApplicationContext.run(Map.of(
            "spec.name", "RawResponseTimeoutAfterUploadTest",
            "micronaut.http.client.ssl.insecure-trust-all-certificates", ssl,
            "micronaut.http.client.alpn-modes", ssl ? "h2" : "http/1.1",
            "micronaut.http.client.read-timeout", "10s",
            "micronaut.server.http-version", ssl ? "2.0" : "1.1",
            "micronaut.server.ssl.enabled", ssl,
            "micronaut.server.ssl.build-self-signed", true,
            "micronaut.server.ssl.port", -1,
            "micronaut.server.idle-timeout", "5s"
        ));
    }

    private static ByteBodyHttpResponse<?> exchange(RawHttpClient client, EmbeddedServer server, CloseableByteBody body, long delay) {
        return exchange(client, server, body, delay, false);
    }

    private static ByteBodyHttpResponse<?> exchange(RawHttpClient client, EmbeddedServer server, CloseableByteBody body, long delay, boolean expectContinue) {
        MutableHttpRequest<?> request = HttpRequest.create(HttpMethod.POST, server.getURI().resolve("/raw-response-timeout-after-upload/count?delay=" + delay).toString())
            .contentType(MediaType.TEXT_PLAIN_TYPE);
        if (expectContinue) {
            request.header(HttpHeaders.EXPECT, "100-continue");
        }
        RawRequestOptions options = RawRequestOptions.builder().responseTimeout(RESPONSE_TIMEOUT).build();
        HttpResponse<?> response = Mono.from(client.exchange(request, body, null, options)).block();
        return (ByteBodyHttpResponse<?>) response;
    }

    @Controller("/raw-response-timeout-after-upload")
    @Requires(property = "spec.name", value = "RawResponseTimeoutAfterUploadTest")
    static class CountController {
        @Post(value = "/count", consumes = MediaType.TEXT_PLAIN, produces = MediaType.TEXT_PLAIN)
        Mono<String> count(@QueryValue long delay, @Body String body) {
            return Mono.delay(Duration.ofMillis(delay)).thenReturn(String.valueOf(body.length()));
        }
    }
}
