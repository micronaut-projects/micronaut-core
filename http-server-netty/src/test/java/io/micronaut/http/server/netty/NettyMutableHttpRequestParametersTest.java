package io.micronaut.http.server.netty;

import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.netty.body.NettyByteBodyFactory;
import io.micronaut.http.server.HttpServerConfiguration;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The parameters of a mutable view of a {@link NettyHttpRequest} follow its URI, and a view of a view keeps the
 * parameters the first view changed.
 */
class NettyMutableHttpRequestParametersTest {
    private final EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());

    @AfterEach
    void close() {
        channel.finishAndReleaseAll();
    }

    private NettyHttpRequest<Object> request(String uri) {
        return new NettyHttpRequest<>(
            new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri),
            NettyByteBodyFactory.empty(),
            channel.pipeline().firstContext(),
            ConversionService.SHARED,
            new HttpServerConfiguration()
        );
    }

    @Test
    void viewOfViewKeepsChangedParameters() {
        MutableHttpRequest<Object> view = request("/foo?a=1").mutate();
        view.getParameters().add("b", List.of("2"));

        MutableHttpRequest<Object> viewOfView = view.mutate();

        assertEquals("1", viewOfView.getParameters().get("a"));
        assertEquals("2", viewOfView.getParameters().get("b"));

        // a change to the parameters of one view does not reach the other
        viewOfView.getParameters().add("c", List.of("3"));
        assertNull(view.getParameters().get("c"));
    }

    @Test
    void parametersFollowUriWithoutQuery() {
        MutableHttpRequest<Object> view = request("/foo?a=1").mutate();
        assertEquals("1", view.getParameters().get("a"));

        view.uri(URI.create("/bar"));

        assertNull(view.getParameters().get("a"));
    }

    @Test
    void parametersFollowUriWithAnotherQuery() {
        MutableHttpRequest<Object> view = request("/foo?a=1").mutate();
        assertEquals("1", view.getParameters().get("a"));

        view.uri(URI.create("/bar?b=2"));

        assertNull(view.getParameters().get("a"));
        assertEquals("2", view.getParameters().get("b"));
    }
}
