package io.micronaut.http.client.netty;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.io.buffer.ReadBufferFactory;
import io.micronaut.http.ByteBodyHttpResponse;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * The response timeout of a raw exchange starts once the request is sent: the time of a slow
 * upload does not count, over HTTP/1.1 and HTTP/2.
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
        HttpRequest<?> request = HttpRequest.create(HttpMethod.POST, server.getURI().resolve("/raw-response-timeout-after-upload/count?delay=" + delay).toString())
            .contentType(MediaType.TEXT_PLAIN_TYPE);
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
