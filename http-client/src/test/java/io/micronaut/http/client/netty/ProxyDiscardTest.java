package io.micronaut.http.client.netty;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.ByteBodyHttpResponse;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpResponseWrapper;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.ProxyHttpClient;
import io.micronaut.http.netty.NettyMutableHttpResponse;
import io.micronaut.http.netty.stream.DefaultStreamedHttpResponse;
import io.micronaut.runtime.server.EmbeddedServer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * {@link ProxyHttpClient#discard}: a proxied response that is not relayed releases what it holds,
 * on the real proxy path and for responses built from Netty objects, and never throws.
 */
class ProxyDiscardTest {
    private static final int CHUNKS = 64;
    private static final int CHUNK_SIZE = 64 * 1024;

    private static EmbeddedServer server;
    private static HttpClient httpClient;
    private static ProxyHttpClient proxyClient;

    @BeforeAll
    static void start() {
        server = ApplicationContext.run(EmbeddedServer.class, Map.of(
            "spec.name", "ProxyDiscardTest",
            "micronaut.http.client.pool.enabled", true,
            // one connection: a discarded response that is not released keeps it busy
            "micronaut.http.client.pool.max-concurrent-http1-connections", 1,
            "micronaut.http.client.pool.acquire-timeout", "3s",
            // longer than the acquire timeout, so that a leaked connection is not replaced
            "micronaut.http.client.read-timeout", "30s"
        ));
        httpClient = server.getApplicationContext().createBean(HttpClient.class, server.getURI());
        proxyClient = (ProxyHttpClient) httpClient;
    }

    @AfterAll
    static void stop() {
        httpClient.close();
        server.close();
    }

    private static MutableHttpResponse<?> proxy(String path) {
        return Mono.from(proxyClient.proxy(HttpRequest.GET(server.getURI().resolve(path)))).block(Duration.ofSeconds(10));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/proxy-discard/full", "/proxy-discard/stream"})
    void aDiscardedProxiedResponseReleasesItsBytesAndTheConnection(String path) {
        MutableHttpResponse<?> response = proxy(path);
        Assertions.assertEquals(HttpStatus.OK, response.status());
        Assertions.assertInstanceOf(ByteBodyHttpResponse.class, response);

        proxyClient.discard(response);

        // the bytes are closed: nothing can be read from them anymore
        Assertions.assertThrows(IllegalStateException.class, () -> ((ByteBodyHttpResponse<?>) response).byteBody().buffer());
        // the single pooled connection is free again
        Assertions.assertEquals("ok", httpClient.toBlocking().retrieve("/proxy-discard/ok"));
        // discarding again is harmless
        Assertions.assertDoesNotThrow(() -> proxyClient.discard(response));
    }

    @Test
    void aDiscardedProxiedResponseIsReleasedThroughTheAsyncView() throws Exception {
        MutableHttpResponse<?> response = proxyClient.toAsyncProxy()
            .proxy(HttpRequest.GET(server.getURI().resolve("/proxy-discard/stream")))
            .toCompletableFuture().get(10, TimeUnit.SECONDS);

        proxyClient.toAsyncProxy().discard(response);

        Assertions.assertThrows(IllegalStateException.class, () -> ((ByteBodyHttpResponse<?>) response).byteBody().buffer());
        Assertions.assertEquals("ok", httpClient.toBlocking().retrieve("/proxy-discard/ok"));
    }

    @Test
    void aFullNettyResponseReleasesItsBuffer() {
        ByteBuf body = Unpooled.copiedBuffer("full", StandardCharsets.UTF_8);
        NettyMutableHttpResponse<ByteBuf> response = new NettyMutableHttpResponse<>(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, body, ConversionService.SHARED);

        proxyClient.discard(response);

        Assertions.assertEquals(0, body.refCnt());
    }

    @Test
    void aWrappedFullNettyResponseReleasesItsBuffer() {
        ByteBuf body = Unpooled.copiedBuffer("full", StandardCharsets.UTF_8);
        HttpResponse<?> response = new HttpResponseWrapper<>(new NettyMutableHttpResponse<>(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, body, ConversionService.SHARED));

        proxyClient.discard(response);

        Assertions.assertEquals(0, body.refCnt());
    }

    @Test
    void aStreamedNettyResponseReleasesEveryChunk() {
        List<ByteBuf> chunks = List.of(
            Unpooled.copiedBuffer("one", StandardCharsets.UTF_8),
            Unpooled.copiedBuffer("two", StandardCharsets.UTF_8),
            Unpooled.copiedBuffer("three", StandardCharsets.UTF_8)
        );
        Flux<HttpContent> content = Flux.just(
            new DefaultHttpContent(chunks.get(0)),
            new DefaultHttpContent(chunks.get(1)),
            new DefaultLastHttpContent(chunks.get(2))
        );
        NettyStreamedHttpResponse<?> response = new NettyStreamedHttpResponse<>(
            new DefaultStreamedHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, content), ConversionService.SHARED);

        proxyClient.discard(response);

        for (ByteBuf chunk : chunks) {
            Assertions.assertEquals(0, chunk.refCnt());
        }
    }

    @Test
    void aStreamedNettyResponseWhoseStreamFailsDoesNotThrow() {
        ByteBuf first = Unpooled.copiedBuffer("one", StandardCharsets.UTF_8);
        Flux<HttpContent> content = Flux.concat(Flux.just(new DefaultHttpContent(first)), Flux.error(new IllegalStateException("upstream failed")));
        NettyStreamedHttpResponse<?> response = new NettyStreamedHttpResponse<>(
            new DefaultStreamedHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, content), ConversionService.SHARED);

        Assertions.assertDoesNotThrow(() -> proxyClient.discard(response));
        Assertions.assertEquals(0, first.refCnt());
    }

    @Test
    void aReleasedOrForeignResponseDoesNotThrow() {
        ByteBuf released = Unpooled.copiedBuffer("gone", StandardCharsets.UTF_8);
        released.release();
        NettyMutableHttpResponse<ByteBuf> releasedResponse = new NettyMutableHttpResponse<>(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, released, ConversionService.SHARED);

        Assertions.assertDoesNotThrow(() -> proxyClient.discard(releasedResponse));
        Assertions.assertDoesNotThrow(() -> proxyClient.discard(HttpResponse.ok("text")));
        Assertions.assertDoesNotThrow(() -> proxyClient.discard(HttpResponse.noContent()));
        Assertions.assertDoesNotThrow(() -> proxyClient.discard(new NettyMutableHttpResponse<>(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, "not a buffer", ConversionService.SHARED)));
    }

    @Requires(property = "spec.name", value = "ProxyDiscardTest")
    @Controller("/proxy-discard")
    static class DiscardController {
        @Get("/ok")
        String ok() {
            return "ok";
        }

        @Get("/full")
        String full() {
            return "x".repeat(CHUNK_SIZE);
        }

        @Get("/stream")
        @Produces(MediaType.APPLICATION_OCTET_STREAM)
        Flux<byte[]> stream() {
            byte[] chunk = new byte[CHUNK_SIZE];
            return Flux.range(0, CHUNKS).map(i -> chunk);
        }
    }
}
