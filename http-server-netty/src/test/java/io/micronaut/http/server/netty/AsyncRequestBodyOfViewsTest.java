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

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.http.BasicHttpAttributes;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpRequestWrapper;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.body.AsyncRequestBody;
import io.micronaut.http.body.ReleasableRequestBody;
import io.micronaut.http.netty.body.NettyByteBodyFactory;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.http.server.binding.AsyncRequestBodyFactory;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.charset.StandardCharsets;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Body readers preserve filter replacements and share byte consumption through request wrappers.
 */
class AsyncRequestBodyOfViewsTest {
    private static final String ORIGINAL = "original secret";
    private final ApplicationContext context = ApplicationContext.run();
    private final EmbeddedChannel channel = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
    private final NettyHttpRequest<Object> request = request();

    @AfterEach
    void close() {
        request.release();
        channel.finishAndReleaseAll();
        context.close();
    }

    @ParameterizedTest
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void aBodyClearedAfterLookupIsEmptyWhenRead(boolean wrapped, boolean copied) {
        MutableHttpRequest<Object> mutable = request.mutate();
        HttpRequest<?> view = wrapped ? new HttpRequestWrapper<>(mutable) : mutable;
        AsyncRequestBody body = AsyncRequestBody.of(view);
        if (copied) {
            body = body.copy();
        }
        assertTrue(body.hasBody());
        assertEquals(OptionalLong.of(ORIGINAL.length()), body.expectedBodySize());

        mutable.body(null);

        assertFalse(body.hasBody());
        assertEquals(OptionalLong.of(0), body.expectedBodySize());
        assertEquals("", body.text().toCompletableFuture().join());
        assertEquals("", AsyncRequestBody.of(view).text().toCompletableFuture().join());
        assertEquals(ORIGINAL, AsyncRequestBody.of(request).text().toCompletableFuture().join());
    }

    @ParameterizedTest
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void aBodyReplacedAfterLookupIsReadFromTheReplacement(boolean wrapped, boolean copied) {
        MutableHttpRequest<Object> mutable = request.mutate();
        HttpRequest<?> view = wrapped ? new HttpRequestWrapper<>(mutable) : mutable;
        AsyncRequestBody body = AsyncRequestBody.of(view);
        if (copied) {
            body = body.copy();
        }
        mutable.body("sanitized");

        assertTrue(body.hasBody());
        assertEquals(OptionalLong.empty(), body.expectedBodySize());
        assertEquals("sanitized", body.body(String.class).toCompletableFuture().join());
        assertEquals(ORIGINAL, AsyncRequestBody.of(request).text().toCompletableFuture().join());
    }

    @Test
    void wrapperLookupsPreserveCopiesAndDoNotAllowTheBytesToBeConsumedTwice() {
        AsyncRequestBody first = AsyncRequestBody.of(request);
        AsyncRequestBody wrapped = AsyncRequestBody.of(new HttpRequestWrapper<>(request));
        AsyncRequestBody last = AsyncRequestBody.of(request);

        assertEquals(ORIGINAL, first.copy().text().toCompletableFuture().join());
        assertEquals(ORIGINAL, wrapped.copy().text().toCompletableFuture().join());
        assertEquals(ORIGINAL, last.text().toCompletableFuture().join());
        assertThrows(IllegalStateException.class, first::text);
        assertThrows(IllegalStateException.class, () -> AsyncRequestBody.of(request).copy().text());
    }

    @Test
    void acquiringAndReadingACopyLeavesTheRoutesBindingStateAlone() {
        ExecutionFlow<?> waits = ExecutionFlow.just("route");
        ReleasableRequestBody routeBody = () -> CompletableFuture.completedStage(null);
        BasicHttpAttributes.addRouteWaitsFor(request, waits);
        BasicHttpAttributes.addRouteBody(request, routeBody);

        AsyncRequestBody body = AsyncRequestBody.of(new HttpRequestWrapper<>(request));
        assertEquals(ORIGINAL, body.copy().text().toCompletableFuture().join());

        assertSame(waits, BasicHttpAttributes.getRouteWaitsFor(request));
        assertSame(routeBody, BasicHttpAttributes.takeRouteBodies(request));
    }

    private NettyHttpRequest<Object> request() {
        NettyHttpRequest<Object> result = new NettyHttpRequest<>(
            new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/"),
            new NettyByteBodyFactory(channel).copyOf(ORIGINAL, StandardCharsets.UTF_8),
            channel.pipeline().firstContext(), ConversionService.SHARED, new HttpServerConfiguration());
        result.setAsyncRequestBodySource(context.getBean(AsyncRequestBodyFactory.class));
        return result;
    }
}
