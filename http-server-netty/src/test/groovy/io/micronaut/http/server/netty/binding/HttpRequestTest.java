/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.http.server.netty.binding;

import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.http.server.netty.NettyHttpRequest;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpVersion;
import junit.framework.TestCase;
import spock.mock.DetachedMockFactory;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

public class HttpRequestTest extends TestCase {

    public void testForEach() {
        final DefaultFullHttpRequest nettyRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, io.netty.handler.codec.http.HttpMethod.GET, "/test");
        nettyRequest.headers().add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON);
        HttpRequest<?> request = new NettyHttpRequest(
                nettyRequest,
                new DetachedMockFactory().Mock(ChannelHandlerContext.class),
                ConversionService.SHARED,
                new HttpServerConfiguration()
        );
        final HttpHeaders headers = request.getHeaders();

        headers.forEach((name, values) -> {
            assertEquals(HttpHeaders.CONTENT_TYPE, name);
            assertEquals(1, values.size());
            assertEquals(MediaType.APPLICATION_JSON, values.iterator().next());
        });

    }


    public void testForEach2() {
        final DefaultFullHttpRequest nettyRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, io.netty.handler.codec.http.HttpMethod.GET, "/test");
        nettyRequest.headers().add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON);
        nettyRequest.headers().add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_XML);
        HttpRequest<?> request = new NettyHttpRequest(
                nettyRequest,
                new DetachedMockFactory().Mock(ChannelHandlerContext.class),
                ConversionService.SHARED,
                new HttpServerConfiguration()
        );
        final HttpHeaders headers = request.getHeaders();

        headers.forEach((name, values) -> {
            assertEquals(HttpHeaders.CONTENT_TYPE, name);
            assertEquals(2, values.size());
            assertTrue(values.contains(MediaType.APPLICATION_JSON));
            assertTrue(values.contains(MediaType.APPLICATION_XML));
        });

        AtomicInteger integer = new AtomicInteger(0);
        headers.forEachValue((s, s2) -> integer.incrementAndGet());

        assertEquals(2, integer.get());

    }
}
