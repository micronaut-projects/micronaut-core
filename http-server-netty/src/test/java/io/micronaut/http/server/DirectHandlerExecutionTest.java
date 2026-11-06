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

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.bind.ArgumentBinder;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.type.Argument;
import io.micronaut.http.BasicHttpAttributes;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpRequestWrapper;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.bind.binders.RequestArgumentBinder;
import io.micronaut.http.bind.binders.TypedRequestArgumentBinder;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.server.binding.DefaultServerRequestBinderRegistry;
import io.micronaut.http.server.binding.ServerBodyAnnotationBinder;
import io.micronaut.http.server.multipart.FormFactory;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.web.router.RouteAttributes;
import io.micronaut.web.router.RouteMatch;
import io.micronaut.web.router.builder.DefaultPathVariables;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Routes to handler functions that read only the request and the path variables are fulfilled
 * without their argument binders: they answer like the bound routes they replace, with the
 * compatibility execution, which binds them, as the reference.
 */
class DirectHandlerExecutionTest {

    private static final String SPEC = "DirectHandlerExecutionTest";
    private static final String LOOP = "default-eventLoopGroup";
    private static final String REGISTRY = "DirectHandlerExecutionTest.registry";
    private static final AtomicInteger RELEASED = new AtomicInteger();

    private ApplicationContext ctx;
    private HttpClient httpClient;
    private BlockingHttpClient client;

    private void start(boolean compatibility) {
        start(compatibility, false);
    }

    private void start(boolean compatibility, boolean customBinder) {
        start(compatibility, customBinder, false);
    }

    private void start(boolean compatibility, boolean customBinder, boolean registrySubclass) {
        RELEASED.set(0);
        Map<String, Object> properties = new HashMap<>();
        properties.put("spec.name", SPEC);
        properties.put("micronaut.server.port", -1);
        if (registrySubclass) {
            properties.put(REGISTRY, true);
        }
        ctx = ApplicationContext.run(properties);
        RouteExecutor routeExecutor = ctx.getBean(RouteExecutor.class);
        routeExecutor.forceCompatibility = compatibility;
        if (customBinder) {
            // the registry the server binds with, before a route is prepared
            routeExecutor.getRequestArgumentSatisfier().getBinderRegistry().addArgumentBinder(new WrappingRequestBinder());
        }
        EmbeddedServer server = ctx.getBean(EmbeddedServer.class).start();
        httpClient = ctx.createBean(HttpClient.class, server.getURL());
        client = httpClient.toBlocking();
    }

    @AfterEach
    void stop() {
        if (httpClient != null) {
            httpClient.close();
        }
        if (ctx != null) {
            ctx.close();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void handlersSeeTheRequestAndThePathVariables(boolean compatibility) {
        start(compatibility);
        assertEquals("sync 42 /direct/sync/42", client.retrieve("/direct/sync/42"));
        assertEquals("async 42", client.retrieve("/direct/async/42"));
        assertEquals("function 42", client.retrieve("/direct/function/42"));
        // decoded like a bound path variable
        assertEquals("sync a b /direct/sync/a%20b", client.retrieve("/direct/sync/a%20b"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aStaticResponseDoesNotDecodeThePathVariables(boolean compatibility) {
        start(compatibility);
        // an invalid escape: a handler that reads no variable answers all the same
        assertEquals("static", client.retrieve("/direct/static/%FF"));
        assertEquals("supplied", client.retrieve("/direct/supplied/%FF"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aHandlerWithAnExecutorRunsOnIt(boolean compatibility) {
        start(compatibility);
        assertFalse(client.retrieve("/direct/blocking").startsWith(LOOP));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void whatAFilterRegisteredIsWaitedFor(boolean compatibility) {
        start(compatibility);
        // a wait a filter registered fails the request, though the route binds nothing
        HttpClientResponseException e = assertThrows(HttpClientResponseException.class,
            () -> client.retrieve("/direct/waits"));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, e.getStatus());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void thePathVariablesAreDecodedBeforeWhatAFilterRegistered(boolean compatibility) {
        start(compatibility);
        // the variable fails to decode before the wait of the filter fails with 409
        HttpClientResponseException e = assertThrows(HttpClientResponseException.class,
            () -> client.retrieve("/direct/sync/%FF?conflict=true"));
        assertNotEquals(HttpStatus.CONFLICT, e.getStatus());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void theBodiesAFilterBoundAreReleased(boolean compatibility) {
        start(compatibility);
        assertEquals("released", client.retrieve("/direct/bodies"));
        assertEquals(1, RELEASED.get());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void theMatchIsFulfilledLikeABoundMatch(boolean compatibility) {
        start(compatibility);
        HttpResponse<String> response = client.exchange(HttpRequest.GET("/direct/sync/7"), String.class);
        assertEquals("sync 7 /direct/sync/7", response.body());
        assertEquals("true,true,true", response.header("X-Fulfilled"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void anArgumentGivenToTheMatchIsKept(boolean compatibility) {
        start(compatibility);
        // a request filter gave the path variables to the match
        assertEquals("sync given /direct/sync/7", client.retrieve(HttpRequest.GET("/direct/sync/7").header("X-Give", "given")));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aPathVariableNamedRequestDoesNotHideTheRequest(boolean compatibility) {
        start(compatibility);
        assertEquals("/direct/named/value", client.retrieve("/direct/named/value"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aBinderAddedForTheRequestBindsIt(boolean compatibility) {
        start(compatibility, true);
        assertEquals("wrapped", client.retrieve("/direct/wrapped"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aRegistrySubclassFindingAnotherBinderBindsTheRequest(boolean compatibility) {
        start(compatibility, false, true);
        assertInstanceOf(WrappingRegistry.class, ctx.getBean(RouteExecutor.class).getRequestArgumentSatisfier().getBinderRegistry());
        assertEquals("wrapped", client.retrieve("/direct/wrapped"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aControllerPathVariableNamedRequestDoesNotHideTheRequest(boolean compatibility) {
        start(compatibility);
        assertEquals("/direct/controller/named/value", client.retrieve("/direct/controller/named/value"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aControllerReadingOnlyTheRequestStillDecodesThePathVariables(boolean compatibility) {
        start(compatibility);
        // an invalid escape fails like for a route that reads the variable
        HttpStatus decodingFailed = assertThrows(HttpClientResponseException.class,
            () -> client.retrieve("/direct/sync/%FF")).getStatus();
        HttpClientResponseException e = assertThrows(HttpClientResponseException.class,
            () -> client.retrieve("/direct/controller/named/%FF"));
        assertEquals(decodingFailed, e.getStatus());
    }

    @Controller("/direct/controller")
    @Requires(property = "spec.name", value = SPEC)
    static final class RequestController {
        @Get(value = "/named/{request}", produces = MediaType.TEXT_PLAIN)
        String named(HttpRequest<?> request) {
            return request.getPath();
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    static final class Routes implements HttpRoutes {
        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.GET("/direct/sync/{id}", (request, pathVariables) ->
                HttpResponse.ok("sync " + pathVariables.getString("id") + " " + request.getPath()).contentType(MediaType.TEXT_PLAIN_TYPE));
            routes.GET("/direct/async/{id}").handleAsync((request, pathVariables) ->
                CompletableFuture.completedFuture(HttpResponse.ok("async " + pathVariables.getString("id")).contentType(MediaType.TEXT_PLAIN_TYPE)));
            routes.GET("/direct/function/{id}").respond(pathVariables ->
                HttpResponse.ok("function " + pathVariables.getString("id")).contentType(MediaType.TEXT_PLAIN_TYPE));
            routes.GET("/direct/static/{value}").respond(HttpResponse.ok("static").contentType(MediaType.TEXT_PLAIN_TYPE));
            routes.GET("/direct/supplied/{value}").respond(() -> HttpResponse.ok("supplied").contentType(MediaType.TEXT_PLAIN_TYPE));
            routes.GET("/direct/blocking").executeOn(TaskExecutors.BLOCKING).handle((request, pathVariables) ->
                HttpResponse.ok(Thread.currentThread().getName()).contentType(MediaType.TEXT_PLAIN_TYPE));
            routes.GET("/direct/waits", (request, pathVariables) -> HttpResponse.ok("not waited").contentType(MediaType.TEXT_PLAIN_TYPE));
            routes.GET("/direct/bodies", (request, pathVariables) -> HttpResponse.ok("released").contentType(MediaType.TEXT_PLAIN_TYPE));
            routes.GET("/direct/named/{request}", (request, pathVariables) -> HttpResponse.ok(request.getPath()).contentType(MediaType.TEXT_PLAIN_TYPE));
            routes.GET("/direct/wrapped", (request, pathVariables) ->
                HttpResponse.ok(request instanceof HttpRequestWrapper<?> ? "wrapped" : "plain").contentType(MediaType.TEXT_PLAIN_TYPE));
        }
    }

    @ServerFilter("/direct/**")
    @Requires(property = "spec.name", value = SPEC)
    static final class Filter {
        @RequestFilter
        void filter(HttpRequest<?> request) {
            if (request.getPath().equals("/direct/waits")) {
                BasicHttpAttributes.addRouteWaitsFor(request, ExecutionFlow.error(new IllegalStateException("The wait failed")));
            } else if (request.getPath().equals("/direct/bodies")) {
                BasicHttpAttributes.addRouteBody(request, () -> {
                    RELEASED.incrementAndGet();
                    return CompletableFuture.completedStage(null);
                });
            }
            if (request.getParameters().contains("conflict")) {
                BasicHttpAttributes.addRouteWaitsFor(request, ExecutionFlow.error(new HttpStatusException(HttpStatus.CONFLICT, "The wait failed")));
            }
            String given = request.getHeaders().get("X-Give");
            if (given != null) {
                RouteMatch<?> match = RouteAttributes.getRouteMatch(request).orElseThrow();
                match.fulfill(Map.of("pathVariables", new DefaultPathVariables(Map.of("id", given), ConversionService.SHARED, null, null)));
            }
        }

        @ResponseFilter
        void response(HttpRequest<?> request, MutableHttpResponse<?> response) {
            Optional<RouteMatch<?>> match = RouteAttributes.getRouteMatch(request);
            if (match.isPresent() && request.getPath().startsWith("/direct/sync/")) {
                RouteMatch<?> routeMatch = match.get();
                response.header("X-Fulfilled", routeMatch.isFulfilled() + "," + routeMatch.isSatisfied("request") + "," + routeMatch.isSatisfied("pathVariables"));
            }
        }
    }

    /**
     * A binder added for the request, which wraps it: the route binds the request with it.
     */
    static final class WrappingRequestBinder implements TypedRequestArgumentBinder<HttpRequest<?>> {
        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public Argument<HttpRequest<?>> argumentType() {
            return (Argument) Argument.of(HttpRequest.class);
        }

        @Override
        public BindingResult<HttpRequest<?>> bind(ArgumentConversionContext<HttpRequest<?>> context, HttpRequest<?> source) {
            HttpRequest<?> wrapped = new HttpRequestWrapper<>(source);
            return () -> Optional.of(wrapped);
        }
    }

    /**
     * A registry that finds another binder for the request than the one it registered.
     */
    @Singleton
    @Primary
    @Requires(property = REGISTRY)
    static class WrappingRegistry extends DefaultServerRequestBinderRegistry {
        @SuppressWarnings({"rawtypes", "java:S3740"})
        WrappingRegistry(ConversionService conversionService,
                         List<RequestArgumentBinder> binders,
                         ServerBodyAnnotationBinder<Object> bodyBinder,
                         BeanProvider<FormFactory> formFactory) {
            super(conversionService, binders, bodyBinder, formFactory);
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public <T> Optional<ArgumentBinder<T, HttpRequest<?>>> findArgumentBinder(Argument<T> argument) {
            if (argument.getType() == HttpRequest.class) {
                return Optional.of((ArgumentBinder) new WrappingRequestBinder());
            }
            return super.findArgumentBinder(argument);
        }
    }
}
