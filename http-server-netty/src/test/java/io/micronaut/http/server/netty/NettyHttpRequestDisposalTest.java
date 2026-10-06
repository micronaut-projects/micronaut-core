package io.micronaut.http.server.netty;

import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.netty.body.NettyByteBodyFactory;
import io.micronaut.http.server.HttpServerConfiguration;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Disposal resources of a {@link NettyHttpRequest} are released once, all of them even when one
 * fails, and a resource added after the request was released is released immediately.
 */
class NettyHttpRequestDisposalTest {
    private final EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());

    @AfterEach
    void close() {
        channel.finishAndReleaseAll();
    }

    private NettyHttpRequest<Object> request() {
        return new NettyHttpRequest<>(
            new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/foo"),
            NettyByteBodyFactory.empty(),
            channel.pipeline().firstContext(),
            ConversionService.SHARED,
            new HttpServerConfiguration()
        );
    }

    @Test
    void resourceAddedAfterReleaseIsReleasedImmediately() {
        NettyHttpRequest<Object> request = request();
        request.release();

        List<String> released = new ArrayList<>();
        request.addDisposalResource(() -> released.add("late"));

        assertEquals(List.of("late"), released);
    }

    @Test
    void failingResourceDoesNotSkipTheOthers() {
        NettyHttpRequest<Object> request = request();
        List<String> released = new ArrayList<>();
        IllegalStateException first = new IllegalStateException("first");
        IllegalStateException second = new IllegalStateException("second");
        request.addDisposalResource(() -> released.add("a"));
        request.addDisposalResource(() -> {
            released.add("b");
            throw first;
        });
        request.addDisposalResource(() -> {
            released.add("c");
            throw second;
        });
        request.addDisposalResource(() -> released.add("d"));

        IllegalStateException thrown = assertThrows(IllegalStateException.class, request::release);

        assertSame(first, thrown);
        assertArrayEquals(new Throwable[]{second}, thrown.getSuppressed());
        assertEquals(List.of("a", "b", "c", "d"), released);
    }

    @Test
    void doubleReleaseReleasesResourcesOnce() {
        NettyHttpRequest<Object> request = request();
        List<String> released = new ArrayList<>();
        request.addDisposalResource(() -> released.add("a"));

        request.release();
        request.release();

        assertEquals(List.of("a"), released);
    }
}
