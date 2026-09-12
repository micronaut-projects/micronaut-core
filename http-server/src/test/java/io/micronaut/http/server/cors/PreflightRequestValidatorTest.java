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
package io.micronaut.http.server.cors;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Executable;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.web.router.DefaultRouteBuilder;
import io.micronaut.web.router.DefaultRouter;
import io.micronaut.web.router.resource.StaticResourceResolver;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;

import static io.micronaut.http.HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS;
import static io.micronaut.http.HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD;
import static io.micronaut.http.HttpHeaders.ACCESS_CONTROL_REQUEST_PRIVATE_NETWORK;
import static io.micronaut.http.HttpHeaders.ORIGIN;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PreflightRequestValidatorTest {
    private static final String ROUTE = "/resource";
    private static final String REQUEST_ORIGIN = "https://example.com";

    private final ApplicationContext applicationContext;
    private final PreflightRequestValidator validator;

    PreflightRequestValidatorTest() {
        applicationContext = ApplicationContext.run();
        TestController controller = new TestController();
        applicationContext.registerSingleton(controller);
        TestRouteBuilder routeBuilder = new TestRouteBuilder(applicationContext);
        routeBuilder.addRoute(controller);
        validator = new DefaultPreflightRequestValidator(
            StaticResourceResolver.EMPTY,
            new DefaultRouter(routeBuilder)
        );
    }

    @AfterAll
    void closeApplicationContext() {
        applicationContext.close();
    }

    @Test
    void acceptsValidPreflightRequestForMatchingRoute() {
        assertTrue(validator.validatePreflightRequest(
            preflightRequest(HttpMethod.GET),
            new CorsOriginConfiguration()
        ));
    }

    @Test
    void rejectsRequestThatIsNotPreflight() {
        HttpRequest<?> request = HttpRequest.GET(ROUTE).header(ORIGIN, REQUEST_ORIGIN);

        assertFalse(validator.validatePreflightRequest(request, new CorsOriginConfiguration()));
    }

    @Test
    void rejectsMethodThatIsNotAllowed() {
        CorsOriginConfiguration configuration = new CorsOriginConfiguration();
        configuration.setAllowedMethods(List.of(HttpMethod.POST));

        assertFalse(validator.validatePreflightRequest(
            preflightRequest(HttpMethod.GET),
            configuration
        ));
    }

    @Test
    void rejectsMethodWithoutMatchingRoute() {
        assertFalse(validator.validatePreflightRequest(
            preflightRequest(HttpMethod.POST),
            new CorsOriginConfiguration()
        ));
    }

    @Test
    void validatesRequestedHeadersCaseInsensitively() {
        CorsOriginConfiguration configuration = new CorsOriginConfiguration();
        configuration.setAllowedHeaders(List.of("X-Allowed"));

        assertTrue(validator.validatePreflightRequest(
            preflightRequest(HttpMethod.GET).header(ACCESS_CONTROL_REQUEST_HEADERS, "x-allowed"),
            configuration
        ));
        assertFalse(validator.validatePreflightRequest(
            preflightRequest(HttpMethod.GET).header(ACCESS_CONTROL_REQUEST_HEADERS, "X-Denied"),
            configuration
        ));
    }

    @Test
    void rejectsDisallowedPrivateNetworkRequest() {
        CorsOriginConfiguration configuration = new CorsOriginConfiguration();
        configuration.setAllowPrivateNetwork(false);

        assertFalse(validator.validatePreflightRequest(
            preflightRequest(HttpMethod.GET).header(ACCESS_CONTROL_REQUEST_PRIVATE_NETWORK, "true"),
            configuration
        ));
        assertTrue(validator.validatePreflightRequest(
            preflightRequest(HttpMethod.GET).header(ACCESS_CONTROL_REQUEST_PRIVATE_NETWORK, "false"),
            configuration
        ));
    }

    private static MutableHttpRequest<?> preflightRequest(HttpMethod method) {
        return HttpRequest.OPTIONS(ROUTE)
            .header(ORIGIN, REQUEST_ORIGIN)
            .header(ACCESS_CONTROL_REQUEST_METHOD, method.name());
    }

    private static final class TestRouteBuilder extends DefaultRouteBuilder {
        TestRouteBuilder(ApplicationContext applicationContext) {
            super(applicationContext);
        }

        void addRoute(TestController controller) {
            GET(ROUTE, controller);
        }
    }

    @Singleton
    @Executable
    static class TestController {
        String index() {
            return "ok";
        }
    }
}
