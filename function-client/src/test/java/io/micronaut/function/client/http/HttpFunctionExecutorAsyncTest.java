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
package io.micronaut.function.client.http;

import com.sun.net.httpserver.HttpServer;
import io.micronaut.context.ApplicationContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.function.client.FunctionDefinition;
import io.micronaut.function.client.FunctionInvoker;
import io.micronaut.function.client.exceptions.FunctionNotFoundException;
import io.micronaut.http.client.HttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link FunctionInvoker#invokeAsync}: the {@link HttpFunctionExecutor} invokes the function with
 * the async view of its HTTP client, and the default implementation adapts the publisher of an
 * invoker that only returns publishers.
 */
class HttpFunctionExecutorAsyncTest {

    private HttpServer server;
    private ApplicationContext context;
    private HttpFunctionExecutor<Object, Object> executor;
    private final AtomicInteger calls = new AtomicInteger();
    private final List<String> accepts = new CopyOnWriteArrayList<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/max", exchange -> {
            calls.incrementAndGet();
            accepts.add(String.valueOf(exchange.getRequestHeaders().getFirst("Accept")));
            byte[] body = "42".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        context = ApplicationContext.run();
        executor = new HttpFunctionExecutor<>(context.getBean(ConversionService.class), HttpClient.create(null));
    }

    @AfterEach
    void stop() {
        executor.close();
        context.close();
        server.stop(0);
    }

    @Test
    void invokeAsyncCompletesWithTheResult() throws Exception {
        CompletionStage<String> result = executor.invokeAsync(definition("/max"), null, Argument.STRING);
        assertEquals("42", result.toCompletableFuture().get(10, TimeUnit.SECONDS));
        assertEquals(1, calls.get());
    }

    @Test
    void invokeAsyncOfAVoidFunctionCompletesWithNull() throws Exception {
        CompletionStage<Void> result = executor.invokeAsync(definition("/max"), null, Argument.VOID);
        assertNull(result.toCompletableFuture().get(10, TimeUnit.SECONDS));
        assertEquals(1, calls.get());
    }

    @Test
    void invokeOfAVoidFunctionReturnsNull() {
        assertNull(executor.invoke(definition("/max"), null, (Argument) Argument.VOID));
        assertEquals(1, calls.get());
    }

    @Test
    void invokeWithACompletionStageTypeReturnsTheFuture() throws Exception {
        Object result = executor.invoke(definition("/max"), null, (Argument) Argument.of(CompletionStage.class, String.class));
        assertInstanceOf(CompletableFuture.class, result);
        assertEquals("42", ((CompletableFuture<?>) result).get(10, TimeUnit.SECONDS));
    }

    @Test
    void invokeAsyncOfAFunctionWithoutUriFailsTheStage() {
        CompletionStage<String> result = executor.invokeAsync(() -> "nowhere", null, Argument.STRING);
        ExecutionException e = assertThrows(ExecutionException.class, () -> result.toCompletableFuture().get(10, TimeUnit.SECONDS));
        assertInstanceOf(FunctionNotFoundException.class, e.getCause());
    }

    @Test
    void theDefaultInvokeAsyncAdaptsThePublisher() throws Exception {
        FunctionInvoker<Object, Object> invoker = publisherInvoker(Mono.just("adapted"));
        assertEquals("adapted", invoker.invokeAsync(definition("/any"), null, Argument.STRING).toCompletableFuture().get());
    }

    @Test
    void theDefaultInvokeAsyncCompletesEmptyWithNull() throws Exception {
        FunctionInvoker<Object, Object> invoker = publisherInvoker(Mono.empty());
        assertNull(invoker.invokeAsync(definition("/any"), null, Argument.STRING).toCompletableFuture().get());
    }

    @Test
    void theDefaultInvokeAsyncFailsWithoutAPublisher() {
        FunctionInvoker<Object, Object> invoker = (definition, input, outputType) -> null;
        ExecutionException e = assertThrows(ExecutionException.class, () -> invoker.invokeAsync(definition("/any"), null, Argument.STRING).toCompletableFuture().get());
        assertInstanceOf(IllegalStateException.class, e.getCause());
    }

    @Test
    void theDefaultInvokeAsyncFailsWithTheErrorOfTheInvoker() {
        IllegalStateException boom = new IllegalStateException("boom");
        FunctionInvoker<Object, Object> invoker = (definition, input, outputType) -> {
            throw boom;
        };
        ExecutionException e = assertThrows(ExecutionException.class, () -> invoker.invokeAsync(definition("/any"), null, Argument.STRING).toCompletableFuture().get());
        assertEquals(boom, e.getCause());
    }

    @Test
    void invokeAsyncSendsTheRequestOfThePublisherOfTheValue() throws Exception {
        try (HttpFunctionExecutor<Object, Object> publisherOnly = new HttpFunctionExecutor<>(context.getBean(ConversionService.class), HttpClient.create(null))) {
            // what the CompletionStage path of the function client sent: the request for a publisher
            Object published = Mono.from((Publisher<?>) publisherOnly.invoke(definition("/max"), null, (Argument) Argument.of(Publisher.class, String.class))).block();
            assertEquals("42", published);
            assertEquals("42", executor.invokeAsync(definition("/max"), null, Argument.STRING).toCompletableFuture().get(10, TimeUnit.SECONDS));
            assertEquals("42", publisherOnly.invokeAsync(definition("/max"), null, Argument.STRING).toCompletableFuture().get(10, TimeUnit.SECONDS));
        }

        assertEquals(3, accepts.size());
        assertFalse(accepts.get(0).contains("text/plain"), accepts.get(0));
        assertEquals(accepts.get(0), accepts.get(1));
        assertEquals(accepts.get(0), accepts.get(2));
    }

    @Test
    void aSubclassThatOverridesInvokeIsCalledThroughItByInvokeAsync() throws Exception {
        AtomicInteger invoked = new AtomicInteger();
        try (HttpFunctionExecutor<Object, Object> subclass = new HttpFunctionExecutor<>(context.getBean(ConversionService.class), HttpClient.create(null)) {
            @Override
            public Object invoke(FunctionDefinition definition, Object input, Argument<Object> outputType) {
                invoked.incrementAndGet();
                assertEquals(Publisher.class, outputType.getType());
                return Mono.just("overridden");
            }
        }) {
            assertEquals("overridden", subclass.invokeAsync(definition("/max"), null, Argument.STRING).toCompletableFuture().get(10, TimeUnit.SECONDS));
        }
        assertEquals(1, invoked.get());
        assertEquals(0, calls.get());
    }

    @Test
    void theDefaultBeanInvokesWithoutAPublisher() {
        assertSame(HttpFunctionExecutor.class, context.getBean(HttpFunctionExecutor.class).getClass());
    }

    private FunctionInvoker<Object, Object> publisherInvoker(Publisher<?> publisher) {
        return (definition, input, outputType) -> {
            assertEquals(Publisher.class, outputType.getType());
            return publisher;
        };
    }

    private FunctionDefinition definition(String path) {
        URI uri = URI.create("http://localhost:" + server.getAddress().getPort() + path);
        return new FunctionDefinition() {
            @Override
            public String getName() {
                return path.substring(1);
            }

            @Override
            public Optional<URI> getURI() {
                return Optional.of(uri);
            }
        };
    }
}
