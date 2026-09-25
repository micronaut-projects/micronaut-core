package io.micronaut.http.server.tck.netty.compiled;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.io.service.SoftServiceLoader;
import io.micronaut.http.HttpRequest;
import io.micronaut.web.router.Router;
import io.micronaut.web.router.UriRouteInfo;
import io.micronaut.web.router.UriRouteMatch;
import io.micronaut.web.router.spi.RoutePlan;
import io.micronaut.web.router.spi.RouteSlot;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompiledRoutesUsedTest {

    private static final String CONTROLLER = "io.micronaut.http.server.tck.tests.RemoteAddressTest$TestController";

    @Test
    void theTckControllersAreLinkedIntoOnePlan() {
        List<RoutePlan> plans = SoftServiceLoader.load(RoutePlan.class).collectAll();
        assertEquals(1, plans.size());
        RoutePlan plan = plans.get(0);
        assertEquals("linked:io.micronaut.http.server.tck.netty.compiled.$LinkedRoutePlan", plan.id());
        List<String> owners = List.of(plan.owners());
        assertTrue(owners.contains(CONTROLLER), owners::toString);
        assertTrue(owners.size() > 50, owners::toString);
        assertEquals(RoutePlan.fingerprint(plan.slots()), plan.fingerprint());
        // most templates are compiled, the others are matched at runtime
        long compiled = Arrays.stream(plan.slots()).filter(RouteSlot::compiled).count();
        assertTrue(compiled > plan.slots().length / 2, () -> compiled + " of " + plan.slots().length);
    }

    @Test
    void theRouterUsesTheCompiledRoutes() throws ReflectiveOperationException {
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", "RemoteAddressTest"))) {
            Router router = context.getBean(Router.class);
            List<UriRouteInfo<?, ?>> routes = router.uriRoutes()
                .filter(route -> route.getDeclaringType().getName().equals(CONTROLLER))
                .toList();
            assertTrue(!routes.isEmpty());
            for (UriRouteInfo<?, ?> route : routes) {
                assertEquals("LazyUriRouteInfo", route.getClass().getSimpleName(), route::toString);
            }
            // the parser of the plan found the route and captured nothing to match again
            UriRouteMatch<?, ?> match = router.findClosest(HttpRequest.GET("/remoteAddress/fromSourceIp"));
            assertNotNull(match);
            Field matchInfo = match.getClass().getDeclaredField("matchInfo");
            matchInfo.setAccessible(true);
            assertEquals("CapturedUriMatchInfo", matchInfo.get(match).getClass().getSimpleName());
        }
    }
}
