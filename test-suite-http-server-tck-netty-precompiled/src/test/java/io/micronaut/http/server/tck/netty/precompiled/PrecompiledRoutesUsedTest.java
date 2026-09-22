package io.micronaut.http.server.tck.netty.precompiled;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.io.service.SoftServiceLoader;
import io.micronaut.web.router.PrecompiledHttpRoutesDefinition;
import io.micronaut.web.router.Router;
import io.micronaut.web.router.UriRouteInfo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrecompiledRoutesUsedTest {

    @Test
    void theTckControllersArePrecompiled() {
        List<PrecompiledHttpRoutesDefinition> definitions = SoftServiceLoader.load(PrecompiledHttpRoutesDefinition.class).collectAll();
        assertEquals(1, definitions.size());
        List<String> controllerTypes = List.of(definitions.get(0).controllerTypes());
        assertTrue(controllerTypes.contains("io.micronaut.http.server.tck.tests.RemoteAddressTest$TestController"), controllerTypes::toString);
        assertTrue(controllerTypes.size() > 50, controllerTypes::toString);
    }

    @Test
    void theRouterUsesThePrecompiledRoutes() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", "RemoteAddressTest"))) {
            List<UriRouteInfo<?, ?>> routes = context.getBean(Router.class).uriRoutes()
                .filter(route -> route.getDeclaringType().getName().equals("io.micronaut.http.server.tck.tests.RemoteAddressTest$TestController"))
                .toList();
            assertTrue(!routes.isEmpty());
            for (UriRouteInfo<?, ?> route : routes) {
                assertEquals("LazyUriRouteInfo", route.getClass().getSimpleName(), route::toString);
            }
        }
    }
}
