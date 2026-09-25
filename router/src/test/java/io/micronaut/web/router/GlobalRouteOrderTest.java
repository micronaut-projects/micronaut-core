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
import io.micronaut.core.annotation.Order;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The order of a route is a global tie-break: it is compared between the routes left for a
 * request whoever declared them, controllers (order {@code 0}) and every {@link HttpRoutes} bean
 * and group, and the lower order wins. The order of the beans does not matter.
 */
class GlobalRouteOrderTest {

    static final String SPEC_NAME = "GlobalRouteOrderTest";

    private static ApplicationContext context;
    private static Router router;

    @BeforeAll
    static void start() {
        context = ApplicationContext.run(Map.of("spec.name", SPEC_NAME));
        router = context.getBean(Router.class);
    }

    @AfterAll
    static void stop() {
        context.close();
    }

    @Test
    void theLowestOrderWinsAcrossHttpRoutesBeansWhateverTheOrderOfTheBeans() {
        // the bean ordered first declares the order 1, the one ordered last the order -1
        assertEquals("last-bean", winner("/global-order/beans"));
    }

    @Test
    void aControllerRouteHasTheOrderZero() {
        assertEquals("controller", winner("/global-order/positive"));
        assertEquals("negative", winner("/global-order/negative"));
    }

    @Test
    void theOrderOfAGroupCompetesWithTheRoutesOfAnotherBeanAndAController() {
        assertEquals("group", winner("/global-order/group"));
    }

    private static String winner(String path) {
        UriRouteMatch<Object, Object> match = router.findClosest(HttpRequest.GET(path));
        assertNotNull(match, path);
        UriRouteInfo<Object, Object> route = match.getRouteInfo();
        Object name = route.getAttributes().get("name");
        return name == null ? route.getTargetMethod().getMethodName() : name.toString();
    }

    @Singleton
    @Order(-100)
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class FirstRoutes implements HttpRoutes {
        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.GET("/global-order/beans", GlobalRouteOrderTest::ok).attribute("name", "first-bean").order(1);
            routes.GET("/global-order/positive", GlobalRouteOrderTest::ok).attribute("name", "positive").order(1);
            routes.GET("/global-order/group", GlobalRouteOrderTest::ok).attribute("name", "first-bean").order(-1);
        }
    }

    @Singleton
    @Order(100)
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class LastRoutes implements HttpRoutes {
        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.GET("/global-order/beans", GlobalRouteOrderTest::ok).attribute("name", "last-bean").order(-1);
            routes.GET("/global-order/negative", GlobalRouteOrderTest::ok).attribute("name", "negative").order(-1);
            routes.path("/global-order", group -> {
                group.order(-2);
                group.GET("/group", GlobalRouteOrderTest::ok).attribute("name", "group");
            });
        }
    }

    @Controller("/global-order")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class OrderController {
        @Get("/positive")
        String controller() {
            return "controller";
        }

        @Get("/negative")
        String negativeLoses() {
            return "controller";
        }

        @Get("/group")
        String groupLoses() {
            return "controller";
        }
    }

    private static HttpResponse<?> ok(HttpRequest<?> request, io.micronaut.http.PathVariables pathVariables) {
        return HttpResponse.ok();
    }
}
