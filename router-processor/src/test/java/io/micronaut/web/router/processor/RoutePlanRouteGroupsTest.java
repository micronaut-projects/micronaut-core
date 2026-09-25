package io.micronaut.web.router.processor;

import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.filter.FilterRunner;
import io.micronaut.http.filter.GenericHttpFilter;
import io.micronaut.http.uri.RouteTemplate;
import io.micronaut.web.router.DefaultRouter;
import io.micronaut.web.router.RouteAssembly;
import io.micronaut.web.router.Router;
import io.micronaut.web.router.UriRouteMatch;
import io.micronaut.web.router.builder.DefaultHttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.spi.PlannedRouteDeclaration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Handler routes of a group bound to the slots of a generated plan: the plan finds the routes,
 * and the filters of the group, which belong to the routes, still run.
 */
class RoutePlanRouteGroupsTest {

    private static final RouteTemplate COLON_ITEM = RouteTemplate.of(ColonRouteTemplateEngine.ID, "/items/:id");
    private static final RouteTemplate NATIVE_OWNER = RouteTemplate.micronaut("/items/{id}/owners/{owner}");

    private static final CompiledRoutePlan COMPILED = new RoutePlanCompiler().plan("test.$Grouped$RoutePlan", "test:grouped", List.of(), List.of(
        GeneratedPlans.route("test:item", "GET", COLON_ITEM),
        GeneratedPlans.route("test:owner", "GET", NATIVE_OWNER)
    ));

    @Test
    void theFiltersOfAGroupRunForTheRoutesAPlanFinds() {
        GeneratedPlans.ObservedPlan observed = new GeneratedPlans.ObservedPlan(GeneratedPlans.load(COMPILED));
        List<String> trace = new ArrayList<>();
        Router router = router(routes -> {
            routes.group(group -> {
                group.before(request -> record(trace, "group"));
                group.handle(PlannedRouteDeclaration.of(observed, "test:item"), (request, variables) -> HttpResponse.ok());
                group.handle(PlannedRouteDeclaration.of(observed, "test:owner"), (request, variables) -> HttpResponse.ok());
            });
            routes.GET("/outside", (request, variables) -> HttpResponse.ok());
        });

        for (HttpRequest<?> request : List.of(HttpRequest.GET("/items/5"), HttpRequest.HEAD("/items/5"), HttpRequest.GET("/items/5/owners/6"))) {
            observed.reported.clear();
            trace.clear();
            UriRouteMatch<Object, Object> match = router.findClosest(request);
            assertNotNull(match, request::toString);
            assertTrue(!observed.reported.isEmpty(), "The plan found the route of " + request);
            run(router, request, match, trace);
            assertEquals(List.of("group", "handler"), trace, request::toString);
        }
        assertEquals("6", router.findClosest(HttpRequest.GET("/items/5/owners/6")).getVariableValues().get("owner"));

        // a route outside the group has none of its filters
        trace.clear();
        HttpRequest<?> outside = HttpRequest.GET("/outside");
        run(router, outside, router.findClosest(outside), trace);
        assertEquals(List.of("handler"), trace);
    }

    private static void run(Router router, HttpRequest<?> request, UriRouteMatch<?, ?> match, List<String> trace) {
        assertNotNull(match, request::toString);
        List<GenericHttpFilter> filters = router.findFilters(request, match);
        ExecutionFlow<HttpResponse<?>> flow = new FilterRunner(filters, (r, propagatedContext) -> {
            trace.add("handler");
            return ExecutionFlow.just(HttpResponse.ok());
        }).run(request);
        assertNotNull(flow.tryCompleteValue(), "the filters completed synchronously");
    }

    private static HttpResponse<?> record(List<String> trace, String step) {
        trace.add(step);
        return null;
    }

    private static Router router(Consumer<HttpRouteBuilder> routes) {
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, (String) null, route -> { });
        routes.accept(new DefaultHttpRouteBuilder(assembly));
        assembly.addImplicitHeadRoutes();
        return new DefaultRouter(List.of(), List.of(() -> assembly));
    }
}
