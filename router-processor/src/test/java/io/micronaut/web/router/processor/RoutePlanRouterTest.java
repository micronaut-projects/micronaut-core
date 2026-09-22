package io.micronaut.web.router.processor;

import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.uri.RouteTemplate;
import io.micronaut.http.uri.UriTemplateMatcher;
import io.micronaut.http.uri.spi.RouteTemplateEngines;
import io.micronaut.web.router.DefaultRouter;
import io.micronaut.web.router.RouteAssembly;
import io.micronaut.web.router.Router;
import io.micronaut.web.router.UriRouteInfo;
import io.micronaut.web.router.UriRouteMatch;
import io.micronaut.web.router.builder.DefaultHttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.RouteDeclaration;
import io.micronaut.web.router.spi.PlannedRouteDeclaration;
import io.micronaut.web.router.spi.RoutePlan;
import io.micronaut.web.router.spi.RouteSlot;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Handler routes bound to the declarations of a generated plan: the parser of the plan finds the
 * routes of compiled slots, and they compete with the other routes of the router under the same
 * selection rules. A second engine is lowered by its own compiler, and an engine without a
 * compiler is matched at runtime in the same router.
 */
class RoutePlanRouterTest {

    private static final RouteTemplate COLON_ITEM = RouteTemplate.of(ColonRouteTemplateEngine.ID, "/items/:id");
    private static final RouteTemplate SPECIAL = RouteTemplate.micronaut("/items/special");
    private static final RouteTemplate RUNTIME_FILE = RouteTemplate.of(ColonRouteTemplateEngine.RUNTIME_ID, "/files/:name");
    private static final RouteTemplate NATIVE_OWNER = RouteTemplate.micronaut("/items/{id}/owners/{owner}");
    private static final RouteTemplate UNBOUND = RouteTemplate.micronaut("/unbound/{id}");

    private static final CompiledRoutePlan COMPILED = new RoutePlanCompiler().plan("test.$Items$RoutePlan", "test:items", List.of(), List.of(
        GeneratedPlans.route("test:item", "GET", COLON_ITEM),
        GeneratedPlans.route("test:special", "GET", SPECIAL),
        GeneratedPlans.route("test:file", "GET", RUNTIME_FILE),
        GeneratedPlans.route("test:owner", "GET", NATIVE_OWNER),
        GeneratedPlans.route("test:propfind", "PROPFIND", COLON_ITEM),
        GeneratedPlans.route("test:unbound", "GET", UNBOUND)
    ));

    private static final List<HttpRequest<?>> REQUESTS = List.of(
        HttpRequest.GET("/items/5"),
        HttpRequest.GET("/items/5/"),
        HttpRequest.GET("/items/5?q=1"),
        HttpRequest.HEAD("/items/5"),
        HttpRequest.GET("/items/special"),
        HttpRequest.HEAD("/items/special"),
        HttpRequest.GET("/items/5/owners/a%20b"),
        HttpRequest.GET("/items/5/owners/a+b"),
        HttpRequest.GET("/files/a"),
        HttpRequest.GET("/files/a/b"),
        HttpRequest.create(HttpMethod.CUSTOM, "/items/5", "PROPFIND"),
        HttpRequest.create(HttpMethod.CUSTOM, "/items/5", "MKCOL"),
        HttpRequest.DELETE("/items/5"),
        HttpRequest.GET("/unbound/1"),
        HttpRequest.GET("/items"),
        HttpRequest.GET("/")
    );

    @Test
    void theCompilerLowersTheEnginesWithACompiler() {
        Map<String, RouteSlot> slots = slots();
        assertTrue(slots.get("test:item").compiled());
        assertEquals(List.of("id"), List.of(slots.get("test:item").captures()));
        assertTrue(slots.get("test:special").compiled());
        assertTrue(slots.get("test:owner").compiled());
        assertEquals(List.of("id", "owner"), List.of(slots.get("test:owner").captures()));
        // a valid engine without a compiler: matched at runtime, not an error
        assertFalse(slots.get("test:file").compiled());
        assertEquals("no route template compiler for the engine 'test.colon-runtime'", slots.get("test:file").fallbackReason());
        assertEquals("1", slots.get("test:item").engineVersion());
        // the unbound slot is compiled too: the plan has potential routes
        assertEquals("/", COMPILED.commonPrefix());
    }

    @Test
    void aCompilerOfAnotherEngineVersionIsNotUsed() {
        RouteTemplateCompiler future = new RouteTemplateCompiler() {
            @Override
            public String engineId() {
                return ColonRouteTemplateEngine.ID;
            }

            @Override
            public String engineVersion() {
                return "2";
            }

            @Override
            public LoweredTemplate lower(io.micronaut.http.uri.ParsedRouteTemplate template) {
                throw new AssertionError("not called");
            }
        };
        CompiledRoutePlan plan = new RoutePlanCompiler(RouteTemplateEngines.defaults(), List.of(future))
            .plan("test.$Future$RoutePlan", "test:future", List.of(), List.of(GeneratedPlans.route("test:item", "GET", COLON_ITEM)));
        assertFalse(plan.slots().get(0).compiled());
        assertEquals("the route template compiler of the engine 'test.colon' is for the version 2, not 1", plan.slots().get(0).fallbackReason());
    }

    @Test
    void aMissingEngineOrADuplicateKeyFailsTheCompilation() {
        RoutePlanCompiler compiler = new RoutePlanCompiler();
        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class, () -> compiler.plan("test.X", "test:x", List.of(),
            List.of(GeneratedPlans.route("test:x", "GET", RouteTemplate.of("test.missing", "/x")))));
        assertTrue(missing.getMessage().contains("test.missing"), missing.getMessage());
        IllegalArgumentException duplicate = assertThrows(IllegalArgumentException.class, () -> compiler.plan("test.X", "test:x", List.of(), List.of(
            GeneratedPlans.route("test:x", "GET", RouteTemplate.micronaut("/a")),
            GeneratedPlans.route("test:x", "POST", RouteTemplate.micronaut("/b")))));
        assertTrue(duplicate.getMessage().contains("test:x"), duplicate.getMessage());
    }

    @Test
    void theParserOfTheColonEngineAgreesWithItsPattern() {
        RoutePlan plan = GeneratedPlans.load(COMPILED);
        RouteTemplateEngines engines = RouteTemplateEngines.defaults();
        for (String path : List.of("/items/5", "/items/5/", "/items/", "/items//", "/items/a+b", "/items/%20", "/items/5/6", "/items", "/files/a")) {
            Map<String, List<String>> matched = GeneratedPlans.match(plan, UriTemplateMatcher.normalizeForMatching(path));
            var captures = engines.matcher(engines.parse(COLON_ITEM)).match(path);
            assertEquals(captures != null, matched.containsKey("test:item"), path);
            if (captures != null) {
                assertEquals(captures.values(), matched.get("test:item"), path);
            }
            assertFalse(matched.containsKey("test:file"), path);
        }
    }

    @Test
    void theRouterSelectsTheSameRoutesWithAndWithoutThePlan() {
        GeneratedPlans.ObservedPlan observed = new GeneratedPlans.ObservedPlan(GeneratedPlans.load(COMPILED));
        Router planned = routerOf(declarations(observed));
        Router ordinary = routerOf(key -> RouteDeclaration.of(slots().get(key).httpMethodName(), slots().get(key).template()));
        for (HttpRequest<?> request : REQUESTS) {
            assertEquals(describe(ordinary.findClosest(request)), describe(planned.findClosest(request)), request::toString);
            assertEquals(ordinary.findAllClosest(request).stream().map(RoutePlanRouterTest::describe).toList(),
                planned.findAllClosest(request).stream().map(RoutePlanRouterTest::describe).toList(), request::toString);
            assertEquals(ordinary.findAny(request).stream().map(RoutePlanRouterTest::describe).sorted().toList(),
                planned.findAny(request).stream().map(RoutePlanRouterTest::describe).sorted().toList(), request::toString);
        }
        assertTrue(observed.matches > 0);
    }

    @Test
    void theParserFindsTheRoutesOfCompiledSlotsAndTheEngineTheOthers() {
        GeneratedPlans.ObservedPlan observed = new GeneratedPlans.ObservedPlan(GeneratedPlans.load(COMPILED));
        Router router = routerOf(declarations(observed));

        assertEquals(COLON_ITEM, findClosest(router, HttpRequest.GET("/items/5")).getRouteInfo().getRouteTemplate());
        assertEquals("5", findClosest(router, HttpRequest.GET("/items/5")).getVariableValues().get("id"));
        assertTrue(observed.reported.contains("test:item"));

        // a literal route of the plan wins over a variable of another engine, both found by the parser
        observed.reported.clear();
        assertEquals(SPECIAL, findClosest(router, HttpRequest.GET("/items/special")).getRouteInfo().getRouteTemplate());
        assertEquals(List.of("test:item", "test:propfind", "test:special"), observed.reported.stream().sorted().toList());

        // the implicit HEAD route of a declared GET route is bound to the slot of the GET route
        UriRouteMatch<?, ?> head = findClosest(router, HttpRequest.HEAD("/items/5"));
        assertTrue(head.getRouteInfo().isImplicitHead());
        assertEquals("5", head.getVariableValues().get("id"));

        // a custom method name
        assertEquals("PROPFIND", findClosest(router, HttpRequest.create(HttpMethod.CUSTOM, "/items/5", "PROPFIND")).getRouteInfo().getHttpMethodName());

        // a slot of an engine without a compiler: the engine matches it
        observed.reported.clear();
        assertEquals("a", findClosest(router, HttpRequest.GET("/files/a")).getVariableValues().get("name"));
        assertTrue(observed.reported.isEmpty());

        // the raw values of a native template, captured by the parser and decoded by the match like any other
        UriRouteMatch<?, ?> owner = findClosest(router, HttpRequest.GET("/items/5/owners/a%20b"));
        assertEquals("CapturedUriMatchInfo", matchedBy(owner));
        assertEquals(Map.of("id", "5", "owner", "a b"), owner.getVariableValues());

        // a declared slot without a handler is not a route
        assertNull(router.findClosest(HttpRequest.GET("/unbound/1")));
    }

    @Test
    void aSlotBoundTwiceIsSelectedByTheUsualRules() {
        GeneratedPlans.ObservedPlan observed = new GeneratedPlans.ObservedPlan(GeneratedPlans.load(COMPILED));
        PlannedRouteDeclaration item = PlannedRouteDeclaration.of(observed, "test:item");
        Router router = router(routes -> {
            routes.handle(item, (request, variables) -> HttpResponse.ok("json")).produces(MediaType.APPLICATION_JSON_TYPE);
            routes.handle(item, (request, variables) -> HttpResponse.ok("text")).produces(MediaType.TEXT_PLAIN_TYPE);
        });
        assertEquals(List.of(MediaType.TEXT_PLAIN_TYPE),
            findClosest(router, HttpRequest.GET("/items/5").accept(MediaType.TEXT_PLAIN_TYPE)).getRouteInfo().getProduces());
        assertEquals(List.of(MediaType.APPLICATION_JSON_TYPE),
            findClosest(router, HttpRequest.GET("/items/5").accept(MediaType.APPLICATION_JSON_TYPE)).getRouteInfo().getProduces());
        assertEquals(2, router.findAllClosest(HttpRequest.GET("/items/5")).size());
    }

    @Test
    void eachRouterHasItsOwnBindings() {
        RoutePlan plan = GeneratedPlans.load(COMPILED);
        PlannedRouteDeclaration item = PlannedRouteDeclaration.of(plan, "test:item");
        // the same slot bound to another handler in each router, e.g. two route tables
        Router first = router(routes -> routes.handle(item, (request, variables) -> HttpResponse.ok()).produces(MediaType.APPLICATION_JSON_TYPE));
        Router second = router(routes -> routes.handle(item, (request, variables) -> HttpResponse.ok()).produces(MediaType.TEXT_PLAIN_TYPE));
        UriRouteInfo<?, ?> firstRoute = findClosest(first, HttpRequest.GET("/items/5")).getRouteInfo();
        UriRouteInfo<?, ?> secondRoute = findClosest(second, HttpRequest.GET("/items/5")).getRouteInfo();
        assertNotSame(firstRoute, secondRoute);
        assertEquals(List.of(MediaType.APPLICATION_JSON_TYPE), firstRoute.getProduces());
        assertEquals(List.of(MediaType.TEXT_PLAIN_TYPE), secondRoute.getProduces());
        // a router that binds only some slots of the plan
        Router partial = router(routes -> routes.handle(PlannedRouteDeclaration.of(plan, "test:special"), (request, variables) -> HttpResponse.ok()));
        assertNull(partial.findClosest(HttpRequest.GET("/items/5")));
        assertNotNull(partial.findClosest(HttpRequest.GET("/items/special")));
    }

    @Test
    void aPlanThatDoesNotAgreeWithTheRuntimeIsNotUsed() {
        GeneratedPlans.ObservedPlan stale = new GeneratedPlans.ObservedPlan(GeneratedPlans.load(COMPILED), "stale");
        Router router = routerOf(declarations(stale));
        Router ordinary = routerOf(key -> RouteDeclaration.of(slots().get(key).httpMethodName(), slots().get(key).template()));
        for (HttpRequest<?> request : REQUESTS) {
            assertEquals(describe(ordinary.findClosest(request)), describe(router.findClosest(request)), request::toString);
        }
        assertEquals(0, stale.matches);
        assertTrue(stale.reported.isEmpty());
    }

    @Test
    void aDeclarationUnderAContextPathIsAnOrdinaryRoute() {
        GeneratedPlans.ObservedPlan observed = new GeneratedPlans.ObservedPlan(GeneratedPlans.load(COMPILED));
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, "/ctx", route -> { });
        new DefaultHttpRouteBuilder(assembly).handle(PlannedRouteDeclaration.of(observed, "test:owner"), (request, variables) -> HttpResponse.ok());
        assembly.addImplicitHeadRoutes();
        Router router = new DefaultRouter(List.of(), List.of(() -> assembly));
        assertNotNull(router.findClosest(HttpRequest.GET("/ctx/items/5/owners/6")));
        assertNull(router.findClosest(HttpRequest.GET("/items/5/owners/6")));
        assertEquals(0, observed.matches);
    }

    private static Map<String, RouteSlot> slots() {
        Map<String, RouteSlot> slots = new java.util.LinkedHashMap<>();
        for (RouteSlot slot : COMPILED.slots()) {
            slots.put(slot.key(), slot);
        }
        return slots;
    }

    private static Function<String, RouteDeclaration> declarations(RoutePlan plan) {
        return key -> PlannedRouteDeclaration.of(plan, key);
    }

    private static Router routerOf(Function<String, RouteDeclaration> declarations) {
        return router(routes -> {
            for (String key : List.of("test:item", "test:special", "test:file", "test:owner", "test:propfind")) {
                routes.handle(declarations.apply(key), (request, variables) -> HttpResponse.ok(key));
            }
            // an ordinary route that competes with the routes of the plan
            routes.DELETE("/items/{id}", (request, variables) -> HttpResponse.ok());
        });
    }

    private static Router router(Consumer<HttpRouteBuilder> routes) {
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, (String) null, route -> { });
        routes.accept(new DefaultHttpRouteBuilder(assembly));
        assembly.addImplicitHeadRoutes();
        return new DefaultRouter(List.of(), List.of(() -> assembly));
    }

    private static UriRouteMatch<?, ?> findClosest(Router router, HttpRequest<?> request) {
        UriRouteMatch<?, ?> match = router.findClosest(request);
        assertNotNull(match, request::toString);
        return match;
    }

    private static @Nullable String describe(@Nullable UriRouteMatch<?, ?> match) {
        if (match == null) {
            return null;
        }
        return match.getRouteInfo().getHttpMethodName() + ' ' + match.getRouteInfo().getRouteTemplate() + ' '
            + match.getRouteInfo().isImplicitHead() + ' ' + match.getVariableValues() + ' ' + match.getUri();
    }

    private static String matchedBy(UriRouteMatch<?, ?> match) {
        try {
            var field = match.getClass().getDeclaredField("matchInfo");
            field.setAccessible(true);
            return field.get(match).getClass().getSimpleName();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
