package io.micronaut.web.router;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.RequestTimeout;
import io.micronaut.web.router.builder.DefaultHttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The request timeout of a route, {@link RouteInfo#getRequestTimeout()}: the option of the route
 * builder, else the annotation.
 */
class RouteTimeoutsTest {

    @Test
    void aRouteWithoutATimeoutHasNone() {
        Router router = router(routes -> routes.GET("/plain", (request, pathVariables) -> HttpResponse.ok()));
        assertNull(route(router, "/plain").getRequestTimeout());
    }

    @Test
    void theRouteBuilderOptionAppliesToTheRoutesOfAGroupWhichARouteOverrides() {
        Router router = router(routes -> routes.path("/g", group -> {
            group.requestTimeout(Duration.ofSeconds(10));
            group.GET("/inherited", (request, pathVariables) -> HttpResponse.ok());
            group.GET("/own").requestTimeout(Duration.ofSeconds(2)).handle((request, pathVariables) -> HttpResponse.ok());
            group.GET("/disabled").requestTimeout(Duration.ZERO).handle((request, pathVariables) -> HttpResponse.ok());
            group.GET("/negative").requestTimeout(Duration.ofSeconds(-1)).handle((request, pathVariables) -> HttpResponse.ok());
        }));
        assertEquals(Duration.ofSeconds(10), route(router, "/g/inherited").getRequestTimeout());
        assertEquals(Duration.ofSeconds(2), route(router, "/g/own").getRequestTimeout());
        assertEquals(Duration.ZERO, route(router, "/g/disabled").getRequestTimeout());
        assertEquals(Duration.ZERO, route(router, "/g/negative").getRequestTimeout());
    }

    @Test
    void anAttributeMayHoldTheTimeoutAsText() {
        Router router = router(routes -> {
            routes.GET("/text").attribute(RouteInfo.REQUEST_TIMEOUT_ATTRIBUTE, "500ms").handle((request, pathVariables) -> HttpResponse.ok());
            routes.GET("/zero").attribute(RouteInfo.REQUEST_TIMEOUT_ATTRIBUTE, "0").handle((request, pathVariables) -> HttpResponse.ok());
            routes.GET("/minus").attribute(RouteInfo.REQUEST_TIMEOUT_ATTRIBUTE, "-5s").handle((request, pathVariables) -> HttpResponse.ok());
        });
        assertEquals(Duration.ofMillis(500), route(router, "/text").getRequestTimeout());
        assertEquals(Duration.ZERO, route(router, "/zero").getRequestTimeout());
        assertEquals(Duration.ZERO, route(router, "/minus").getRequestTimeout());
    }

    @Test
    void theAnnotationGivesTheTimeout() {
        Router router = router(routes -> {
            routes.GET("/annotated").annotate(timeout("2s")).handle((request, pathVariables) -> HttpResponse.ok());
            routes.GET("/annotated-zero").annotate(timeout("0")).handle((request, pathVariables) -> HttpResponse.ok());
            routes.GET("/annotated-blank").annotate(timeout(" ")).handle((request, pathVariables) -> HttpResponse.ok());
            routes.GET("/both").annotate(timeout("2s")).requestTimeout(Duration.ofSeconds(3))
                .handle((request, pathVariables) -> HttpResponse.ok());
        });
        assertEquals(Duration.ofSeconds(2), route(router, "/annotated").getRequestTimeout());
        assertEquals(Duration.ZERO, route(router, "/annotated-zero").getRequestTimeout());
        assertNull(route(router, "/annotated-blank").getRequestTimeout());
        // the option of the route builder wins over the annotation
        assertEquals(Duration.ofSeconds(3), route(router, "/both").getRequestTimeout());
    }

    @Test
    void anInvalidTimeoutFails() {
        Router router = router(routes -> routes.GET("/invalid").annotate(timeout("soon")).handle((request, pathVariables) -> HttpResponse.ok()));
        RouteInfo<?> route = route(router, "/invalid");
        assertThrows(IllegalArgumentException.class, route::getRequestTimeout);
    }

    @Test
    void theTimeoutIsResolvedOnce() {
        Router router = router(routes -> routes.GET("/once").requestTimeout(Duration.ofSeconds(4)).handle((request, pathVariables) -> HttpResponse.ok()));
        RouteInfo<?> route = route(router, "/once");
        assertSame(route.getRequestTimeout(), route.getRequestTimeout());
    }

    private static AnnotationValue<RequestTimeout> timeout(String value) {
        return AnnotationValue.builder(RequestTimeout.class).value(value).build();
    }

    private static RouteInfo<?> route(Router router, String path) {
        UriRouteMatch<Object, Object> match = router.findClosest(HttpRequest.GET(path));
        assertNotNull(match, path);
        return match.getRouteInfo();
    }

    private static Router router(Consumer<HttpRouteBuilder> routes) {
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, uri -> uri);
        DefaultHttpRouteBuilder builder = new DefaultHttpRouteBuilder(assembly);
        routes.accept(builder);
        builder.close();
        assembly.addImplicitHeadRoutes();
        return new DefaultRouter(List.of(), List.of(() -> assembly));
    }
}
