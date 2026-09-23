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

import io.micronaut.context.env.DefaultPropertyPlaceholderResolver;
import io.micronaut.context.env.PropertyPlaceholderResolver;
import io.micronaut.context.env.PropertySource;
import io.micronaut.context.env.PropertySourcePropertyResolver;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.web.router.builder.DefaultHttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.PathVariables;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The port of a handler route or group given as a string, like {@code @Controller(port = "${...}")}:
 * a number or property placeholders, with defaults, resolved with the environment when the routes
 * are declared, then checked like a port given as a number.
 */
class RoutePortPropertyTest {

    private static final PropertyPlaceholderResolver RESOLVER = new DefaultPropertyPlaceholderResolver(
        new PropertySourcePropertyResolver(PropertySource.of(Map.of("test.admin.port", "9090", "test.bad.port", "abc"))),
        ConversionService.SHARED);

    @Test
    void aPortPropertyIsResolvedLikeTheOneOfAController() {
        Router router = router(routes -> {
            routes.GET("/metrics", RoutePortPropertyTest::ok).port("${test.admin.port}");
            routes.path("/management", management -> {
                management.port("${test.unset.port:9191}");
                management.GET("/health", RoutePortPropertyTest::ok);
            });
            routes.GET("/literal", RoutePortPropertyTest::ok).port("9292");
        }, RESOLVER);
        assertEquals(Set.of(9090, 9191, 9292), router.getExposedPorts());
    }

    @Test
    void anUnresolvablePlaceholderFailsWithTheConfigurationError() {
        ConfigurationException error = assertThrows(ConfigurationException.class,
            () -> router(routes -> routes.GET("/metrics", RoutePortPropertyTest::ok).port("${test.missing.port}"), RESOLVER));
        assertTrue(error.getMessage().contains("test.missing.port"), error.getMessage());
    }

    @Test
    void aPortThatIsNotANumberOrOutOfRangeIsRejected() {
        IllegalArgumentException notANumber = assertThrows(IllegalArgumentException.class,
            () -> router(routes -> routes.GET("/metrics", RoutePortPropertyTest::ok).port("${test.bad.port}"), RESOLVER));
        assertEquals("The port of a route is not a number: ${test.bad.port}, resolved to: abc", notANumber.getMessage());
        assertThrows(IllegalArgumentException.class,
            () -> router(routes -> routes.group(group -> group.port("70000")), RESOLVER));
        assertThrows(IllegalArgumentException.class,
            () -> router(routes -> routes.GET("/metrics", RoutePortPropertyTest::ok).port("0"), RESOLVER));
    }

    @Test
    void aPlaceholderNeedsAnEnvironment() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> router(routes -> routes.GET("/metrics", RoutePortPropertyTest::ok).port("${test.admin.port}"), null));
        assertTrue(error.getMessage().contains("no environment"), error.getMessage());
        assertEquals(Set.of(9090), router(routes -> routes.GET("/metrics", RoutePortPropertyTest::ok).port("9090"), null).getExposedPorts());
    }

    private static HttpResponse<?> ok(HttpRequest<?> request, PathVariables pathVariables) {
        return HttpResponse.ok();
    }

    private static Router router(Consumer<HttpRouteBuilder> routes, @Nullable PropertyPlaceholderResolver resolver) {
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, uri -> uri, route -> { });
        routes.accept(new DefaultHttpRouteBuilder(assembly, resolver));
        assembly.addImplicitHeadRoutes();
        return new DefaultRouter(List.of(), List.of(() -> assembly));
    }
}
