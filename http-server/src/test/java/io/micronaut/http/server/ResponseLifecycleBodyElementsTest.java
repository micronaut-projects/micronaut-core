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
package io.micronaut.http.server;

import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.http.ByteBodyHttpResponse;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.body.BodyElements;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.web.router.RouteAttributes;
import io.micronaut.web.router.RouteInfo;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A {@link BodyElements} body that the response lifecycle does not stream: the elements are
 * closed.
 */
class ResponseLifecycleBodyElementsTest {

    private static ByteBodyHttpResponse<?> encode(HttpRequest<?> request, HttpResponse<?> response, Function<Throwable, ExecutionFlow<HttpResponse<?>>> handler) throws Exception {
        // anonymous: JUnit discovery would reflect over a nested class, and ResponseLifecycle
        // references optional JSON types
        ResponseLifecycle lifecycle = new ResponseLifecycle(null, ResponseLifecycleWriteErrorTest.REGISTRY, ConversionService.SHARED, ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE)) {
            @Override
            protected Executor ioExecutor() {
                return Runnable::run;
            }
        };
        CompletableFuture<ByteBodyHttpResponse<?>> future = new CompletableFuture<>();
        lifecycle.encodeHttpResponseSafe(request, response, handler).onComplete((v, e) -> {
            if (e == null) {
                future.complete(v);
            } else {
                future.completeExceptionally(e);
            }
        });
        return future.get();
    }

    private static BodyElements<String> elements(AtomicBoolean closed) {
        return BodyElements.of(() -> CompletableFuture.completedFuture(Optional.of("never pulled")), () -> closed.set(true));
    }

    @Test
    void elementsWhoseEncoderCannotBePreparedAreClosedAndTheFailureIsHandled() throws Exception {
        AtomicBoolean closed = new AtomicBoolean();
        IllegalStateException failure = new IllegalStateException("the route cannot tell");
        // a route whose writer configuration fails while the encoder of the elements is prepared
        RouteInfo<?> route = (RouteInfo<?>) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{RouteInfo.class}, (proxy, method, args) -> {
            if (method.getName().equals("isResponseBodyJsonFormattable")) {
                throw failure;
            }
            throw new UnsupportedOperationException(method.getName());
        });
        MutableHttpResponse<?> response = HttpResponse.ok(elements(closed)).contentType(MediaType.APPLICATION_JSON_TYPE);
        RouteAttributes.setRouteInfo(response, route);
        List<Throwable> handled = new ArrayList<>();
        try (ByteBodyHttpResponse<?> result = encode(HttpRequest.GET("/"), response, e -> {
            handled.add(e);
            return ExecutionFlow.just(HttpResponse.serverError("handled").contentType(MediaType.TEXT_PLAIN_TYPE));
        })) {
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.getCode(), result.code());
        }
        assertEquals(1, handled.size());
        assertSame(failure, handled.getFirst());
        assertTrue(closed.get(), "nothing streams the elements: they are closed");
    }

    @Test
    void elementsOfTheResponseToAHeadRequestAreClosedWithoutBeingPulled() throws Exception {
        AtomicBoolean closed = new AtomicBoolean();
        try (ByteBodyHttpResponse<?> result = encode(HttpRequest.HEAD("/"), HttpResponse.ok(elements(closed)).contentType(MediaType.APPLICATION_JSON_TYPE), e -> {
            throw new AssertionError(e);
        })) {
            assertEquals(HttpStatus.OK.getCode(), result.code());
            assertEquals(MediaType.APPLICATION_JSON, result.getHeaders().get("Content-Type"));
        }
        assertTrue(closed.get());
    }
}
