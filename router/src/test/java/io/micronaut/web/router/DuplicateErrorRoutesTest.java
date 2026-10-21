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

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.PathVariables;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Error;
import io.micronaut.http.annotation.Produces;
import io.micronaut.inject.MethodExecutionHandle;
import io.micronaut.web.router.builder.DefaultHttpRouteBuilder;
import io.micronaut.web.router.builder.ErrorRouteHandler;
import io.micronaut.web.router.builder.HandlerMethod;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import io.micronaut.web.router.builder.RequestHandler;
import io.micronaut.web.router.builder.StatusRouteHandler;
import io.micronaut.web.router.exceptions.RoutingException;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The global status routes of the test beans of the module, e.g. a {@code 404} one of
 * {@code RouteBuilderTests}, are in every application context: the controllers here use other
 * statuses.
 *
 * <p>Two error routes for the same exception type, or two status routes for the same status, in the
 * same scope fail when the router is built, as the lookup cannot tell them apart: global, or local
 * to the same controller. Routes producing other media types, local to other controllers, or with
 * conditions are not duplicates.
 */
class DuplicateErrorRoutesTest {

    static final String GLOBAL_ERRORS = "DuplicateErrorRoutesTest.globalErrors";
    static final String GLOBAL_STATUSES = "DuplicateErrorRoutesTest.globalStatuses";
    static final String PRODUCES = "DuplicateErrorRoutesTest.produces";
    static final String LOCAL = "DuplicateErrorRoutesTest.local";
    static final String GLOBAL_AND_FUNCTIONAL = "DuplicateErrorRoutesTest.globalAndFunctional";

    @Test
    void twoGlobalErrorMethodsOfControllersForTheSameExceptionFail() {
        RoutingException error = routingFailure(GLOBAL_ERRORS);
        assertTrue(error.getMessage().contains("multiple local routes for error [IllegalStateException]"), error.getMessage());
    }

    @Test
    void twoGlobalStatusMethodsOfControllersForTheSameStatusFail() {
        RoutingException error = routingFailure(GLOBAL_STATUSES);
        assertTrue(error.getMessage().contains("multiple local routes for http status [418]"), error.getMessage());
    }

    @Test
    void aGlobalErrorMethodAndAGlobalFunctionalErrorRouteForTheSameExceptionFail() {
        RoutingException error = routingFailure(GLOBAL_AND_FUNCTIONAL);
        assertTrue(error.getMessage().contains("multiple local routes for error [UnsupportedOperationException]"), error.getMessage());
    }

    @Test
    void globalErrorAndStatusMethodsProducingOtherMediaTypesAreSelectedByTheAcceptHeader() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", PRODUCES))) {
            Router router = context.getBean(Router.class);
            assertEquals("json", errorTarget(router, MediaType.APPLICATION_JSON_TYPE));
            assertEquals("text", errorTarget(router, MediaType.TEXT_PLAIN_TYPE));
            assertEquals("json", statusTarget(router, MediaType.APPLICATION_JSON_TYPE));
            assertEquals("text", statusTarget(router, MediaType.TEXT_PLAIN_TYPE));
        }
    }

    @Test
    void localErrorMethodsOfTwoControllersAndAGlobalOneForTheSameExceptionAreFine() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", LOCAL))) {
            Router router = context.getBean(Router.class);
            assertEquals("first", router.findErrorRoute(FirstLocalController.class, new IllegalStateException(), HttpRequest.GET("/"))
                .map(DuplicateErrorRoutesTest::methodName).orElseThrow());
            assertEquals("second", router.findErrorRoute(SecondLocalController.class, new IllegalStateException(), HttpRequest.GET("/"))
                .map(DuplicateErrorRoutesTest::methodName).orElseThrow());
            assertEquals("global", router.findErrorRoute(new IllegalStateException(), HttpRequest.GET("/"))
                .map(DuplicateErrorRoutesTest::methodName).orElseThrow());
        }
    }

    @Test
    void twoGlobalFunctionalErrorOrStatusRoutesFail() {
        assertThrows(RoutingException.class, () -> router(routes -> {
            routes.error(IllegalStateException.class, new NamedError<>("a"));
            routes.error(IllegalStateException.class, new NamedError<>("b"));
        }));
        assertThrows(RoutingException.class, () -> router(routes -> {
            routes.status(HttpStatus.NOT_FOUND, new NamedStatus("a"));
            routes.status(HttpStatus.NOT_FOUND, new NamedStatus("b"));
        }));
        // in two HttpRoutes beans
        RouteAssembly first = assembly(routes -> routes.error(IllegalStateException.class, new NamedError<>("a")));
        RouteAssembly second = assembly(routes -> routes.error(IllegalStateException.class, new NamedError<>("b")));
        assertThrows(RoutingException.class, () -> new DefaultRouter(List.of(), List.of(() -> first, () -> second)));
    }

    @Test
    void globalErrorRoutesOfOtherTypesOrProducingOtherMediaTypesAreFine() {
        assertNotNull(router(routes -> {
            routes.error(IllegalStateException.class, new NamedError<>("a"));
            routes.error(IllegalArgumentException.class, new NamedError<>("b"));
            routes.error(IllegalStateException.class, new NamedError<>("c")).produces(MediaType.TEXT_PLAIN_TYPE);
            routes.status(HttpStatus.NOT_FOUND, new NamedStatus("a"));
            routes.status(HttpStatus.CONFLICT, new NamedStatus("b"));
            routes.status(HttpStatus.NOT_FOUND, new NamedStatus("c")).produces(MediaType.TEXT_PLAIN_TYPE);
        }));
    }

    @Test
    void aRouteProducingJsonOnlyAndADefaultRouteAnsweringAnyAcceptedTypeAreNotDuplicates() {
        // both produce application/json, but only the default route answers text/plain
        Router router = router(routes -> {
            routes.error(IllegalStateException.class, new NamedError<>("json")).produces(MediaType.APPLICATION_JSON_TYPE);
            routes.error(IllegalStateException.class, new NamedError<>("fallback"));
            routes.status(HttpStatus.NOT_FOUND, new NamedStatus("json")).produces(MediaType.APPLICATION_JSON_TYPE);
            routes.status(HttpStatus.NOT_FOUND, new NamedStatus("fallback"));
        });
        HttpRequest<?> text = HttpRequest.GET("/").accept(MediaType.TEXT_PLAIN_TYPE);
        assertEquals("fallback", name(router.findErrorRoute(new IllegalStateException(), text).orElseThrow()));
        assertEquals("fallback", name(router.findStatusRoute(HttpStatus.NOT_FOUND, text).orElseThrow()));
        // an Accept of application/json, or none, does not tell them apart: the first route answers
        // the same in a group
        assertNotNull(router(routes -> routes.group(group -> {
            group.GET("/x", DuplicateErrorRoutesTest::ok);
            group.error(IllegalStateException.class, new NamedError<>("json")).produces(MediaType.APPLICATION_JSON_TYPE);
            group.error(IllegalStateException.class, new NamedError<>("fallback"));
        })));
    }

    @Test
    void errorRoutesWithConditionsAreNotDuplicates() {
        // a RouteBuilder route: ErrorRoute.where and StatusRoute.where
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, uri -> uri, route -> { });
        assembly.addErrorRoute(null, IllegalStateException.class, handle());
        assembly.addErrorRoute(null, IllegalStateException.class, handle())
            .where(request -> request.getHeaders().contains("X-Conditional"));
        assembly.addStatusRoute(null, HttpStatus.NOT_FOUND, handle());
        assembly.addStatusRoute(null, HttpStatus.NOT_FOUND, handle())
            .where(request -> request.getHeaders().contains("X-Conditional"));

        Router router = new DefaultRouter(List.of(), List.of(() -> assembly));
        HttpRequest<?> request = HttpRequest.GET("/").header("X-Conditional", "true");
        assertNotNull(router.findErrorRoute(new IllegalStateException(), request).orElse(null));
        assertNotNull(router.findStatusRoute(HttpStatus.NOT_FOUND, request).orElse(null));
    }

    private static RoutingException routingFailure(String specName) {
        Throwable error = assertThrows(RuntimeException.class, () -> {
            try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", specName))) {
                context.getBean(Router.class);
            }
        });
        while (error != null && !(error instanceof RoutingException)) {
            error = error.getCause();
        }
        assertNotNull(error, "no RoutingException");
        return (RoutingException) error;
    }

    private static String errorTarget(Router router, MediaType accept) {
        return router.findErrorRoute(new IllegalStateException(), HttpRequest.GET("/").accept(accept))
            .map(DuplicateErrorRoutesTest::methodName).orElseThrow();
    }

    private static String statusTarget(Router router, MediaType accept) {
        return router.findStatusRoute(HttpStatus.GONE, HttpRequest.GET("/").accept(accept))
            .map(DuplicateErrorRoutesTest::methodName).orElseThrow();
    }

    private static HttpResponse<?> ok(HttpRequest<?> request, PathVariables pathVariables) {
        return HttpResponse.ok();
    }

    private static String name(RouteMatch<?> match) {
        Object target = ((HandlerMethod<?>) ((MethodBasedRouteInfo<?, ?>) match.getRouteInfo()).getTargetMethod()).getTarget();
        return target instanceof NamedError<?> named ? named.name() : ((NamedStatus) target).name();
    }

    private static String methodName(RouteMatch<?> match) {
        return ((MethodBasedRouteInfo<?, ?>) match.getRouteInfo()).getTargetMethod().getMethodName();
    }

    @SuppressWarnings("unchecked")
    private static MethodExecutionHandle<Object, Object> handle() {
        return (MethodExecutionHandle<Object, Object>) (MethodExecutionHandle<?, ?>) HandlerMethod.of((RequestHandler) (request, pathVariables) -> HttpResponse.ok());
    }

    private static RouteAssembly assembly(Consumer<HttpRouteBuilder> routes) {
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, uri -> uri, route -> { });
        routes.accept(new DefaultHttpRouteBuilder(assembly));
        return assembly;
    }

    private static Router router(Consumer<HttpRouteBuilder> routes) {
        RouteAssembly assembly = assembly(routes);
        return new DefaultRouter(List.of(), List.of(() -> assembly));
    }

    private record NamedError<E extends Throwable>(String name) implements ErrorRouteHandler<E> {
        @Override
        public HttpResponse<?> handle(HttpRequest<?> request, E error) {
            return HttpResponse.ok(name);
        }
    }

    private record NamedStatus(String name) implements StatusRouteHandler {
        @Override
        public HttpResponse<?> handle(HttpRequest<?> request) {
            return HttpResponse.ok(name);
        }
    }

    @Controller("/first-global")
    @Requires(property = "spec.name", value = GLOBAL_ERRORS)
    static class FirstGlobalErrorController {
        @Error(global = true)
        String first(IllegalStateException e) {
            return "first";
        }
    }

    @Controller("/second-global")
    @Requires(property = "spec.name", value = GLOBAL_ERRORS)
    static class SecondGlobalErrorController {
        @Error(global = true)
        String second(IllegalStateException e) {
            return "second";
        }
    }

    @Controller("/first-status")
    @Requires(property = "spec.name", value = GLOBAL_STATUSES)
    static class FirstGlobalStatusController {
        @Error(global = true, status = HttpStatus.I_AM_A_TEAPOT)
        String first() {
            return "first";
        }
    }

    @Controller("/second-status")
    @Requires(property = "spec.name", value = GLOBAL_STATUSES)
    static class SecondGlobalStatusController {
        @Error(global = true, status = HttpStatus.I_AM_A_TEAPOT)
        String second() {
            return "second";
        }
    }

    @Controller("/global-error")
    @Requires(property = "spec.name", value = GLOBAL_AND_FUNCTIONAL)
    static class GlobalErrorController {
        @Error(global = true)
        String global(UnsupportedOperationException e) {
            return "global";
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = GLOBAL_AND_FUNCTIONAL)
    static class GlobalErrorRoutes implements HttpRoutes {
        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.error(UnsupportedOperationException.class, new NamedError<>("functional"));
        }
    }

    @Controller("/json")
    @Requires(property = "spec.name", value = PRODUCES)
    static class JsonErrorController {
        @Error(global = true)
        @Produces(MediaType.APPLICATION_JSON)
        String json(IllegalStateException e) {
            return "{}";
        }

        @Error(global = true, status = HttpStatus.GONE)
        @Produces(MediaType.APPLICATION_JSON)
        String json() {
            return "{}";
        }
    }

    @Controller("/text")
    @Requires(property = "spec.name", value = PRODUCES)
    static class TextErrorController {
        @Error(global = true)
        @Produces(MediaType.TEXT_PLAIN)
        String text(IllegalStateException e) {
            return "text";
        }

        @Error(global = true, status = HttpStatus.GONE)
        @Produces(MediaType.TEXT_PLAIN)
        String text() {
            return "text";
        }
    }

    @Controller("/first-local")
    @Requires(property = "spec.name", value = LOCAL)
    static class FirstLocalController {
        @Error
        String first(IllegalStateException e) {
            return "first";
        }

        @Error(global = true)
        String global(IllegalStateException e) {
            return "global";
        }
    }

    @Controller("/second-local")
    @Requires(property = "spec.name", value = LOCAL)
    static class SecondLocalController {
        @Error
        String second(IllegalStateException e) {
            return "second";
        }
    }
}
