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

import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.netty.body.NettyByteBodyFactory;
import io.micronaut.http.netty.cookies.NettyCookie;
import io.micronaut.http.server.HttpServerConfiguration;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A cookie added to the mutable copy of a received request, as a proxy filter does, goes into the
 * single {@code Cookie} header the request already has, replacing a cookie of the same name
 * (issue #11819).
 */
class NettyHttpRequestCookieHeaderTest {
    private final EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());

    @AfterEach
    void close() {
        channel.finishAndReleaseAll();
    }

    @Test
    void mutatedRequestKeepsOneCookieHeader() {
        DefaultFullHttpRequest nettyRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/foo");
        nettyRequest.headers().add(HttpHeaders.COOKIE, "a=1; b=2");
        NettyHttpRequest<Object> request = new NettyHttpRequest<>(
            nettyRequest,
            NettyByteBodyFactory.empty(),
            channel.pipeline().firstContext(),
            ConversionService.SHARED,
            new HttpServerConfiguration()
        );

        MutableHttpRequest<Object> mutable = request.mutate();
        mutable.cookie(new NettyCookie("a", "3"));
        mutable.cookie(new NettyCookie("c", "4"));

        assertEquals(List.of("a=3; b=2; c=4"), mutable.getHeaders().getAll(HttpHeaders.COOKIE));
    }

    @Test
    void mutatedRequestKeepsDuplicatePairsOfOtherNames() {
        DefaultFullHttpRequest nettyRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/foo");
        nettyRequest.headers().add(HttpHeaders.COOKIE, "sid=app; sid=root; theme=dark");
        NettyHttpRequest<Object> request = new NettyHttpRequest<>(
            nettyRequest,
            NettyByteBodyFactory.empty(),
            channel.pipeline().firstContext(),
            ConversionService.SHARED,
            new HttpServerConfiguration()
        );

        MutableHttpRequest<Object> mutable = request.mutate();
        mutable.cookie(new NettyCookie("trace", "1"));

        assertEquals(List.of("sid=app; sid=root; theme=dark; trace=1"), mutable.getHeaders().getAll(HttpHeaders.COOKIE));
    }
}
