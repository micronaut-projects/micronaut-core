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
package io.micronaut.web.router;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.ReturnType;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.PathVariables;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import io.micronaut.web.router.builder.AsyncRequestHandler;
import io.micronaut.web.router.builder.DefaultHttpRouteBuilder;
import io.micronaut.web.router.builder.ErrorRouteHandler;
import io.micronaut.web.router.builder.HandlerMethod;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.LocatedAsyncBodyRequestHandler;
import io.micronaut.web.router.builder.LocatedAsyncRequestHandler;
import io.micronaut.web.router.builder.LocatedBodyRequestHandler;
import io.micronaut.web.router.builder.LocatedRequestHandler;
import io.micronaut.web.router.builder.RequestHandler;
import io.micronaut.web.router.builder.RouteMethodProvider;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A handler that implements {@link RouteMethodProvider} gives its route the bean method it
 * implements, as if given with {@code annotationMetadata(...)}, without changing what the route
 * binds and invokes.
 */
class HandlerRouteMethodProviderTest {

    private static final ServiceMethod STATUS = new ServiceMethod("status", Marker.class);
    private static final ServiceMethod OTHER = new ServiceMethod("other", Other.class);

    @Test
    void theRouteHasTheProvidedMethodAndBindsTheArgumentsOfTheHandler() {
        Router router = router(routes -> routes.GET("/status", new StatusHandler(STATUS)));
        for (UriRouteMatch<Object, Object> match : List.of(match(router, HttpRequest.GET("/status")), match(router, HttpRequest.HEAD("/status")))) {
            MethodBasedRouteInfo<?, ?> route = (MethodBasedRouteInfo<?, ?>) match.getRouteInfo();
            assertSame(STATUS, route.getAnnotationMetadataProvider().orElseThrow());
            assertTrue(route.getAnnotationMetadata().hasAnnotation(Marker.class));
            ExecutableMethod<?, ?> invoked = match.getExecutableMethod();
            // the arguments and the invocation stay those of the handler
            assertTrue(invoked instanceof HandlerMethod<?>);
            assertEquals(List.of("request", "pathVariables"), Arrays.stream(invoked.getArguments()).map(Argument::getName).toList());
            assertEquals(HttpResponse.class, invoked.getReturnType().getType());
            assertEquals(Service.class, invoked.getDeclaringType());
            assertEquals("status", invoked.getMethodName());
            assertEquals(STATUS.getTargetMethod(), invoked.getTargetMethod());
        }
    }

    @Test
    void aSynchronousMethodDoesNotMakeAnAsynchronousHandlerSynchronous() {
        Router router = router(routes -> routes.GET("/async").handleAsync(new AsyncStatusHandler(STATUS)));
        RouteInfo<?> route = match(router, HttpRequest.GET("/async")).getRouteInfo();
        assertSame(STATUS, ((MethodBasedRouteInfo<?, ?>) route).getAnnotationMetadataProvider().orElseThrow());
        assertTrue(route.isAsync());
        assertEquals(CompletionStage.class, route.getReturnType().getType());
    }

    @Test
    void anElementGivenToTheRouteReplacesTheProvidedMethod() {
        Router router = router(routes -> routes.GET("/explicit").annotationMetadata(OTHER).handle(new StatusHandler(STATUS)));
        MethodBasedRouteInfo<?, ?> route = (MethodBasedRouteInfo<?, ?>) match(router, HttpRequest.GET("/explicit")).getRouteInfo();
        assertSame(OTHER, route.getAnnotationMetadataProvider().orElseThrow());
        assertTrue(route.getAnnotationMetadata().hasAnnotation(Other.class));
        assertFalse(route.getAnnotationMetadata().hasAnnotation(Marker.class));
    }

    @Test
    void theProvidedMethodReplacesTheElementOfTheGroup() {
        Router router = router(routes -> routes.group(group -> {
            group.annotationMetadata(OTHER);
            group.GET("/provided", new StatusHandler(STATUS));
            group.GET("/lambda", (request, pathVariables) -> HttpResponse.ok());
        }));
        assertSame(STATUS, ((MethodBasedRouteInfo<?, ?>) match(router, HttpRequest.GET("/provided")).getRouteInfo()).getAnnotationMetadataProvider().orElseThrow());
        assertSame(OTHER, ((MethodBasedRouteInfo<?, ?>) match(router, HttpRequest.GET("/lambda")).getRouteInfo()).getAnnotationMetadataProvider().orElseThrow());
    }

    @Test
    void anOrdinaryLambdaHasNoMethod() {
        Router router = router(routes -> routes.GET("/lambda", (request, pathVariables) -> HttpResponse.ok()));
        MethodBasedRouteInfo<?, ?> route = (MethodBasedRouteInfo<?, ?>) match(router, HttpRequest.GET("/lambda")).getRouteInfo();
        assertTrue(route.getAnnotationMetadataProvider().isEmpty());
    }

    @Test
    void theProviderIsReadWhenTheRouteIsAdded() {
        AtomicInteger reads = new AtomicInteger();
        StatusHandler handler = new StatusHandler(STATUS) {
            @Override
            public ExecutableMethod<?, ?> routeMethod() {
                reads.incrementAndGet();
                return super.routeMethod();
            }
        };
        Router router = router(routes -> routes.GET("/status", handler));
        int afterBuild = reads.get();
        assertTrue(afterBuild > 0);
        for (int i = 0; i < 3; i++) {
            RouteInfo<?> route = match(router, HttpRequest.GET("/status")).getRouteInfo();
            assertTrue(route.getAnnotationMetadata().hasAnnotation(Marker.class));
        }
        assertEquals(afterBuild, reads.get());
    }

    @Test
    void aProviderWithoutAMethodFailsTheTerminal() {
        NullPointerException e = assertThrows(NullPointerException.class, () -> router(routes -> routes.GET("/none", new StatusHandler(null))));
        assertTrue(e.getMessage().contains("gave no route method"), e.getMessage());
    }

    @Test
    void anErrorRouteHasTheProvidedMethod() {
        Router router = router(routes -> routes.error(IllegalStateException.class, new ErrorHandler(STATUS)));
        RouteMatch<Object> match = router.findErrorRoute(new IllegalStateException(), HttpRequest.GET("/")).orElseThrow();
        assertSame(STATUS, ((MethodBasedRouteInfo<?, ?>) match.getRouteInfo()).getAnnotationMetadataProvider().orElseThrow());
    }

    @Test
    void anInheritingCopyKeepsTheProvidedMethod() {
        HandlerMethod<HttpResponse<?>> method = HandlerMethod.of(new StatusHandler(STATUS));
        MutableAnnotationMetadata inherited = new MutableAnnotationMetadata();
        inherited.addDeclaredAnnotation(Other.class.getName(), Map.of());
        HandlerMethod<HttpResponse<?>> copy = method.inheriting(inherited);
        assertSame(STATUS, copy.getAnnotationMetadataProvider());
        assertTrue(copy.getAnnotationMetadata().hasAnnotation(Marker.class));
        assertTrue(copy.getAnnotationMetadata().hasAnnotation(Other.class));
        assertEquals("status", copy.getMethodName());
    }

    @Test
    void aLocatedHandlerHasTheProvidedMethod() {
        TestLocatedRoutes<Object> located = TestLocatedRoutes.of(routes -> {
            routes.GET("/sync").handle(new LocatedStatus());
            routes.GET("/async").handleAsync(new LocatedAsyncStatus());
            routes.POST("/body").body(String.class).handle(new LocatedBodyStatus());
            routes.POST("/async-body").body(String.class).handleAsync(new LocatedAsyncBodyStatus());
            routes.GET("/lambda").handle((request, pathVariables, target) -> HttpResponse.ok());
        });
        Router router = router(routes -> routes.locate("/orders/{id}", (request, pathVariables) -> "order", target -> located));
        for (HttpRequest<?> request : List.of(HttpRequest.GET("/orders/1/sync"), HttpRequest.GET("/orders/1/async"),
            HttpRequest.POST("/orders/1/body", "text"), HttpRequest.POST("/orders/1/async-body", "text"))) {
            MethodBasedRouteInfo<?, ?> route = (MethodBasedRouteInfo<?, ?>) match(router, request).getRouteInfo();
            assertSame(STATUS, route.getAnnotationMetadataProvider().orElseThrow(), request.getPath());
            assertTrue(route.getAnnotationMetadata().hasAnnotation(Marker.class), request.getPath());
        }
        MethodBasedRouteInfo<?, ?> lambda = (MethodBasedRouteInfo<?, ?>) match(router, HttpRequest.GET("/orders/1/lambda")).getRouteInfo();
        assertTrue(lambda.getAnnotationMetadataProvider().isEmpty());
    }

    private static UriRouteMatch<Object, Object> match(Router router, HttpRequest<?> request) {
        UriRouteMatch<Object, Object> match = router.findClosest(request);
        assertNotNull(match, request.toString());
        return match;
    }

    private static Router router(Consumer<HttpRouteBuilder> routes) {
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, uri -> uri);
        DefaultHttpRouteBuilder builder = new DefaultHttpRouteBuilder(assembly);
        routes.accept(builder);
        builder.close();
        assembly.addImplicitHeadRoutes();
        return new DefaultRouter(List.of(), List.of(() -> assembly));
    }

    @Retention(RetentionPolicy.RUNTIME)
    @interface Marker {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @interface Other {
    }

    public static final class Service {
        public String status() {
            return "up";
        }

        public String other() {
            return "other";
        }
    }

    static class StatusHandler implements RequestHandler, RouteMethodProvider {
        private final ExecutableMethod<?, ?> method;

        StatusHandler(ExecutableMethod<?, ?> method) {
            this.method = method;
        }

        @Override
        public HttpResponse<?> handle(HttpRequest<?> request, PathVariables pathVariables) {
            return HttpResponse.ok("up");
        }

        @Override
        public ExecutableMethod<?, ?> routeMethod() {
            return method;
        }
    }

    static final class LocatedStatus implements LocatedRequestHandler<Object>, RouteMethodProvider {
        @Override
        public HttpResponse<?> handle(HttpRequest<?> request, PathVariables pathVariables, Object target) {
            return HttpResponse.ok("up");
        }

        @Override
        public ExecutableMethod<?, ?> routeMethod() {
            return STATUS;
        }
    }

    static final class LocatedAsyncStatus implements LocatedAsyncRequestHandler<Object>, RouteMethodProvider {
        @Override
        public CompletionStage<? extends HttpResponse<?>> handle(HttpRequest<?> request, PathVariables pathVariables, Object target) {
            return CompletableFuture.completedFuture(HttpResponse.ok("up"));
        }

        @Override
        public ExecutableMethod<?, ?> routeMethod() {
            return STATUS;
        }
    }

    static final class LocatedBodyStatus implements LocatedBodyRequestHandler<Object, String>, RouteMethodProvider {
        @Override
        public HttpResponse<?> handle(HttpRequest<?> request, PathVariables pathVariables, Object target, String body) {
            return HttpResponse.ok(body);
        }

        @Override
        public ExecutableMethod<?, ?> routeMethod() {
            return STATUS;
        }
    }

    static final class LocatedAsyncBodyStatus implements LocatedAsyncBodyRequestHandler<Object, String>, RouteMethodProvider {
        @Override
        public CompletionStage<? extends HttpResponse<?>> handle(HttpRequest<?> request, PathVariables pathVariables, Object target, String body) {
            return CompletableFuture.completedFuture(HttpResponse.ok(body));
        }

        @Override
        public ExecutableMethod<?, ?> routeMethod() {
            return STATUS;
        }
    }

    record AsyncStatusHandler(ExecutableMethod<?, ?> routeMethod) implements AsyncRequestHandler, RouteMethodProvider {
        @Override
        public CompletionStage<? extends HttpResponse<?>> handle(HttpRequest<?> request, PathVariables pathVariables) {
            return CompletableFuture.completedFuture(HttpResponse.ok("up"));
        }
    }

    record ErrorHandler(ExecutableMethod<?, ?> routeMethod) implements ErrorRouteHandler<IllegalStateException>, RouteMethodProvider {
        @Override
        public HttpResponse<?> handle(HttpRequest<?> request, IllegalStateException error) {
            return HttpResponse.serverError();
        }
    }

    /**
     * A method of {@link Service}, with an annotation.
     */
    static final class ServiceMethod implements ExecutableMethod<Service, String> {
        private final String name;
        private final AnnotationMetadata metadata;

        ServiceMethod(String name, Class<?> annotation) {
            this.name = name;
            MutableAnnotationMetadata metadata = new MutableAnnotationMetadata();
            metadata.addDeclaredAnnotation(annotation.getName(), Map.of());
            this.metadata = metadata;
        }

        @Override
        public Method getTargetMethod() {
            try {
                return Service.class.getMethod(name);
            } catch (NoSuchMethodException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public ReturnType<String> getReturnType() {
            return ReturnType.of(String.class);
        }

        @Override
        public String invoke(Service instance, Object... arguments) {
            return instance.status();
        }

        @Override
        public Class<Service> getDeclaringType() {
            return Service.class;
        }

        @Override
        public String getMethodName() {
            return name;
        }

        @Override
        public Argument<?>[] getArguments() {
            return Argument.ZERO_ARGUMENTS;
        }

        @Override
        public AnnotationMetadata getAnnotationMetadata() {
            return metadata;
        }
    }
}
