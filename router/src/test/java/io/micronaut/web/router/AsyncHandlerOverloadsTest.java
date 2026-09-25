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

import io.micronaut.context.ExecutionHandleLocator;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.body.AsyncRequestBody;
import io.micronaut.web.router.builder.DefaultHttpRouteBuilder;
import io.micronaut.web.router.builder.DefaultPathVariables;
import io.micronaut.web.router.builder.HandlerMethod;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.PathVariables;
import io.micronaut.web.router.builder.RouteDeclaration;
import io.micronaut.web.router.naming.HyphenatedUriNamingStrategy;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The asynchronous handlers with and without the body are overloads of the same builder methods,
 * told apart by the number of parameters of the lambda or of the method a method reference names:
 * two for an {@code AsyncRequestHandler}, three for an {@code AsyncBodyRequestHandler}. The typed
 * handler of a table of located targets receives the target and the body: four parameters. None of
 * the declarations below is ambiguous, which the compilation of this test checks, and each one is
 * routed to the handler it names, which the arguments of its route show.
 */
class AsyncHandlerOverloadsTest {

    private static final List<Class<?>> NO_BODY = List.of(HttpRequest.class, PathVariables.class);
    private static final List<Class<?>> BODY = List.of(HttpRequest.class, PathVariables.class, AsyncRequestBody.class);

    private final RouteTableFactory tables = new RouteTableFactory(ExecutionHandleLocator.EMPTY, new HyphenatedUriNamingStrategy(), ConversionService.SHARED, null);

    @Test
    void lambdasAreToldApartByTheirNumberOfParameters() {
        Router router = router(routes -> {
            routes.asyncGET("/get", (request, pathVariables) -> ok());
            routes.asyncGET("/get-body", (request, pathVariables, body) -> body.text().thenApply(HttpResponse::ok));
            routes.asyncPOST("/post", (request, pathVariables) -> ok());
            routes.asyncPOST("/post-body", (request, pathVariables, body) -> body.text().thenApply(HttpResponse::ok));
            routes.asyncPUT("/put", (request, pathVariables) -> ok());
            routes.asyncPUT("/put-body", (request, pathVariables, body) -> body.discardBody().thenApply(done -> HttpResponse.ok()));
            routes.asyncPATCH("/patch", (request, pathVariables) -> ok());
            routes.asyncPATCH("/patch-body", (request, pathVariables, body) -> ok());
            routes.asyncDELETE("/delete", (request, pathVariables) -> ok());
            routes.asyncDELETE("/delete-body", (request, pathVariables, body) -> ok());
            routes.handleAsync(HttpMethod.POST, "/method", (request, pathVariables) -> ok());
            routes.handleAsync(HttpMethod.POST, "/method-body", (request, pathVariables, body) -> ok());
            routes.handleAsync(Set.of(HttpMethod.PUT), "/methods", (request, pathVariables) -> ok());
            routes.handleAsync(Set.of(HttpMethod.PUT), "/methods-body", (request, pathVariables, body) -> ok());
            routes.handleAsync("PROPFIND", "/named", (request, pathVariables) -> ok());
            routes.handleAsync("PROPFIND", "/named-body", (request, pathVariables, body) -> ok());
            routes.handleAsync(RouteDeclaration.of(HttpMethod.POST, "/declared"), (request, pathVariables) -> ok());
            routes.handleAsync(RouteDeclaration.of(HttpMethod.POST, "/declared-body"), (request, pathVariables, body) -> ok());
        });
        assertArguments(NO_BODY, router, HttpRequest.GET("/get"));
        assertArguments(BODY, router, HttpRequest.GET("/get-body"));
        assertArguments(NO_BODY, router, HttpRequest.POST("/post", ""));
        assertArguments(BODY, router, HttpRequest.POST("/post-body", ""));
        assertArguments(NO_BODY, router, HttpRequest.PUT("/put", ""));
        assertArguments(BODY, router, HttpRequest.PUT("/put-body", ""));
        assertArguments(NO_BODY, router, HttpRequest.PATCH("/patch", ""));
        assertArguments(BODY, router, HttpRequest.PATCH("/patch-body", ""));
        assertArguments(NO_BODY, router, HttpRequest.DELETE("/delete"));
        assertArguments(BODY, router, HttpRequest.DELETE("/delete-body"));
        assertArguments(NO_BODY, router, HttpRequest.POST("/method", ""));
        assertArguments(BODY, router, HttpRequest.POST("/method-body", ""));
        assertArguments(NO_BODY, router, HttpRequest.PUT("/methods", ""));
        assertArguments(BODY, router, HttpRequest.PUT("/methods-body", ""));
        assertArguments(NO_BODY, router, HttpRequest.create(HttpMethod.CUSTOM, "/named", "PROPFIND"));
        assertArguments(BODY, router, HttpRequest.create(HttpMethod.CUSTOM, "/named-body", "PROPFIND"));
        assertArguments(NO_BODY, router, HttpRequest.POST("/declared", ""));
        assertArguments(BODY, router, HttpRequest.POST("/declared-body", ""));
    }

    @Test
    void methodReferencesAreToldApartByTheParametersOfTheMethod() {
        Handlers handlers = new Handlers();
        Router router = router(routes -> {
            routes.asyncGET("/static", AsyncHandlerOverloadsTest::withoutBody);
            routes.asyncPOST("/static-body", AsyncHandlerOverloadsTest::withBody);
            routes.asyncGET("/bound", handlers::find);
            routes.asyncPOST("/bound-body", handlers::save);
            routes.handleAsync(HttpMethod.PUT, "/method", AsyncHandlerOverloadsTest::withoutBody);
            routes.handleAsync(HttpMethod.PUT, "/method-body", handlers::save);
            routes.handleAsync("PROPFIND", "/named", handlers::find);
            routes.handleAsync("PROPFIND", "/named-body", AsyncHandlerOverloadsTest::withBody);
            routes.handleAsync(RouteDeclaration.of(HttpMethod.DELETE, "/declared"), handlers::find);
            routes.handleAsync(RouteDeclaration.of(HttpMethod.DELETE, "/declared-body"), handlers::save);
        });
        assertArguments(NO_BODY, router, HttpRequest.GET("/static"));
        assertArguments(BODY, router, HttpRequest.POST("/static-body", ""));
        assertArguments(NO_BODY, router, HttpRequest.GET("/bound"));
        assertArguments(BODY, router, HttpRequest.POST("/bound-body", ""));
        assertArguments(NO_BODY, router, HttpRequest.PUT("/method", ""));
        assertArguments(BODY, router, HttpRequest.PUT("/method-body", ""));
        assertArguments(NO_BODY, router, HttpRequest.create(HttpMethod.CUSTOM, "/named", "PROPFIND"));
        assertArguments(BODY, router, HttpRequest.create(HttpMethod.CUSTOM, "/named-body", "PROPFIND"));
        assertArguments(NO_BODY, router, HttpRequest.DELETE("/declared"));
        assertArguments(BODY, router, HttpRequest.DELETE("/declared-body"));
    }

    @Test
    void thePathlessFormsAreToldApartToo() {
        Router router = router(routes -> {
            routes.path("/get", group -> group.asyncGET((request, pathVariables) -> ok()));
            routes.path("/get-body", group -> group.asyncGET((request, pathVariables, body) -> ok()));
            routes.path("/post", group -> group.asyncPOST(AsyncHandlerOverloadsTest::withoutBody));
            routes.path("/post-body", group -> group.asyncPOST(AsyncHandlerOverloadsTest::withBody));
            routes.path("/put", group -> group.asyncPUT((request, pathVariables) -> ok()));
            routes.path("/put-body", group -> group.asyncPUT((request, pathVariables, body) -> ok()));
            routes.path("/patch", group -> group.asyncPATCH((request, pathVariables) -> ok()));
            routes.path("/patch-body", group -> group.asyncPATCH((request, pathVariables, body) -> ok()));
            routes.path("/delete", group -> group.asyncDELETE((request, pathVariables) -> ok()));
            routes.path("/delete-body", group -> group.asyncDELETE((request, pathVariables, body) -> ok()));
            routes.path("/method", group -> group.handleAsync(HttpMethod.POST, (request, pathVariables) -> ok()));
            routes.path("/method-body", group -> group.handleAsync(HttpMethod.POST, (request, pathVariables, body) -> ok()));
            routes.path("/methods", group -> group.handleAsync(Set.of(HttpMethod.PUT), (request, pathVariables) -> ok()));
            routes.path("/methods-body", group -> group.handleAsync(Set.of(HttpMethod.PUT), (request, pathVariables, body) -> ok()));
            routes.path("/named", group -> group.handleAsync("PROPFIND", (request, pathVariables) -> ok()));
            routes.path("/named-body", group -> group.handleAsync("PROPFIND", (request, pathVariables, body) -> ok()));
        });
        assertArguments(NO_BODY, router, HttpRequest.GET("/get"));
        assertArguments(BODY, router, HttpRequest.GET("/get-body"));
        assertArguments(NO_BODY, router, HttpRequest.POST("/post", ""));
        assertArguments(BODY, router, HttpRequest.POST("/post-body", ""));
        assertArguments(NO_BODY, router, HttpRequest.PUT("/put", ""));
        assertArguments(BODY, router, HttpRequest.PUT("/put-body", ""));
        assertArguments(NO_BODY, router, HttpRequest.PATCH("/patch", ""));
        assertArguments(BODY, router, HttpRequest.PATCH("/patch-body", ""));
        assertArguments(NO_BODY, router, HttpRequest.DELETE("/delete"));
        assertArguments(BODY, router, HttpRequest.DELETE("/delete-body"));
        assertArguments(NO_BODY, router, HttpRequest.POST("/method", ""));
        assertArguments(BODY, router, HttpRequest.POST("/method-body", ""));
        assertArguments(NO_BODY, router, HttpRequest.PUT("/methods", ""));
        assertArguments(BODY, router, HttpRequest.PUT("/methods-body", ""));
        assertArguments(NO_BODY, router, HttpRequest.create(HttpMethod.CUSTOM, "/named", "PROPFIND"));
        assertArguments(BODY, router, HttpRequest.create(HttpMethod.CUSTOM, "/named-body", "PROPFIND"));
    }

    @Test
    void theTypedHandlerOfALocatedTableReceivesTheTargetAndTheBody() throws Exception {
        RouteTable orders = tables.buildLocatedHttpRoutes(Order.class, order -> {
            // typed: the target and the body
            order.handleAsync(HttpMethod.POST, "/typed", (request, pathVariables, target, body) ->
                CompletableFuture.completedFuture(HttpResponse.ok("typed " + target.id() + " " + body.hasBody())));
            order.handleAsync(HttpMethod.POST, (request, pathVariables, target, body) ->
                CompletableFuture.completedFuture(HttpResponse.ok("pathless " + target.id())));
            order.handleAsync(RouteDeclaration.of(HttpMethod.PUT, "/declared"), AsyncHandlerOverloadsTest::located);
            // untyped: with and without the body, the target read from the path variables
            order.handleAsync(HttpMethod.GET, "/untyped", (request, pathVariables) ->
                CompletableFuture.completedFuture(HttpResponse.ok("untyped " + pathVariables.locatedTarget(Order.class).id())));
            order.handleAsync(HttpMethod.PATCH, "/untyped-body", (request, pathVariables, body) ->
                CompletableFuture.completedFuture(HttpResponse.ok("untyped body " + pathVariables.locatedTarget(Order.class).id())));
            order.asyncPOST("/shortcut", (request, pathVariables, body) -> ok());
        });
        Router router = router(routes -> routes.locate("/orders/{id}", (request, pathVariables) -> new Order(pathVariables.getLong("id")), order -> orders));

        assertEquals("typed 5 true", invoke(router, HttpRequest.POST("/orders/5/typed", ""), body()).body());
        assertEquals("pathless 5", invoke(router, HttpRequest.POST("/orders/5", ""), body()).body());
        assertEquals("declared 5", invoke(router, HttpRequest.PUT("/orders/5/declared", ""), body()).body());
        assertEquals("untyped 5", invoke(router, HttpRequest.GET("/orders/5/untyped")).body());
        assertEquals("untyped body 5", invoke(router, HttpRequest.PATCH("/orders/5/untyped-body", ""), body()).body());
        assertArguments(BODY, router, HttpRequest.POST("/orders/5/shortcut", ""));
    }

    private static CompletionStage<HttpResponse<?>> ok() {
        return CompletableFuture.completedFuture(HttpResponse.ok());
    }

    private static CompletionStage<? extends HttpResponse<?>> withoutBody(HttpRequest<?> request, PathVariables pathVariables) {
        return ok();
    }

    private static CompletionStage<? extends HttpResponse<?>> withBody(HttpRequest<?> request, PathVariables pathVariables, AsyncRequestBody body) {
        return ok();
    }

    private static CompletionStage<? extends HttpResponse<?>> located(HttpRequest<?> request, PathVariables pathVariables, Order order, AsyncRequestBody body) {
        return CompletableFuture.completedFuture(HttpResponse.ok("declared " + order.id()));
    }

    private static void assertArguments(List<Class<?>> expected, Router router, HttpRequest<?> request) {
        UriRouteMatch<Object, Object> match = router.findClosest(request);
        assertNotNull(match, request.getMethodName() + " " + request.getPath());
        List<Class<?>> types = Arrays.stream(match.getRouteInfo().getTargetMethod().getArguments()).<Class<?>>map(Argument::getType).toList();
        assertEquals(expected, types, request.getMethodName() + " " + request.getPath());
    }

    private static HttpResponse<?> invoke(Router router, HttpRequest<?> request, Object... extra) throws Exception {
        UriRouteMatch<Object, Object> match = router.findClosest(request);
        assertNotNull(match, request.getPath());
        Object target = ((RouteLocator.LocatedUriMatchInfo) ((DefaultUriRouteMatch<?, ?>) match).matchInfo()).target();
        HandlerMethod<?> handler = assertInstanceOf(HandlerMethod.class, ((DefaultUrlRouteInfo<?, ?>) match.getRouteInfo()).getTargetMethod());
        Object[] arguments = new Object[2 + extra.length];
        arguments[0] = request;
        arguments[1] = new DefaultPathVariables(match.getVariableValues(), ConversionService.SHARED, target);
        System.arraycopy(extra, 0, arguments, 2, extra.length);
        return (HttpResponse<?>) ((CompletionStage<?>) handler.invoke(arguments)).toCompletableFuture().get();
    }

    /**
     * @return A body that has a body, and nothing else
     */
    private static AsyncRequestBody body() {
        return (AsyncRequestBody) Proxy.newProxyInstance(AsyncHandlerOverloadsTest.class.getClassLoader(), new Class<?>[] {AsyncRequestBody.class}, (proxy, method, args) -> {
            if (method.getName().equals("hasBody")) {
                return true;
            }
            throw new UnsupportedOperationException(method.getName());
        });
    }

    private static Router router(Consumer<HttpRouteBuilder> routes) {
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, uri -> uri, route -> { });
        routes.accept(new DefaultHttpRouteBuilder(assembly));
        return new DefaultRouter(List.of(), List.of(() -> assembly));
    }

    record Order(long id) {
    }

    static final class Handlers {
        CompletionStage<? extends HttpResponse<?>> find(HttpRequest<?> request, PathVariables pathVariables) {
            return ok();
        }

        CompletionStage<? extends HttpResponse<?>> save(HttpRequest<?> request, PathVariables pathVariables, AsyncRequestBody body) {
            return body.text().thenApply(HttpResponse::ok);
        }
    }
}
