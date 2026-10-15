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
package io.micronaut.web.router.downstream;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.ExecutionHandleLocator;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.CustomHttpMethod;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.web.router.AnnotatedMethodRouteBuilder;
import io.micronaut.web.router.Router;
import io.micronaut.web.router.UriRoute;
import io.micronaut.web.router.UriRouteMatch;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A route builder of another package that replaces {@link AnnotatedMethodRouteBuilder} and
 * overrides {@code buildBeanRoute}, like the one of the Google Cloud Function module that
 * prepends the context path, builds every controller route.
 */
class ReplacedAnnotatedMethodRouteBuilderTest {

    static final String SPEC_NAME = "ReplacedAnnotatedMethodRouteBuilderTest";

    @Test
    void theOverriddenBeanRouteBuildsTheControllerRoutes() {
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", SPEC_NAME))) {
            assertInstanceOf(ContextPathRouteBuilder.class, context.getBean(AnnotatedMethodRouteBuilder.class));
            Router router = context.getBean(Router.class);

            assertRoute(router, HttpRequest.GET("/cp/replaced/get"), "/cp/replaced/get");
            assertRoute(router, HttpRequest.HEAD("/cp/replaced/get"), "/cp/replaced/get");
            assertRoute(router, HttpRequest.POST("/cp/replaced/post", ""), "/cp/replaced/post");
            assertRoute(router, HttpRequest.create(HttpMethod.CUSTOM, "/cp/replaced/custom", "LOCK"), "/cp/replaced/custom");
            assertNull(router.findClosest(HttpRequest.GET("/replaced/get")));
        }
    }

    private static void assertRoute(Router router, HttpRequest<?> request, String template) {
        UriRouteMatch<Object, Object> match = router.findClosest(request);
        assertNotNull(match, () -> "routed: " + request.getMethodName() + " " + request.getPath());
        assertEquals(template, match.getRouteInfo().getUriMatchTemplate().toString());
    }

    @Singleton
    @Replaces(AnnotatedMethodRouteBuilder.class)
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class ContextPathRouteBuilder extends AnnotatedMethodRouteBuilder {

        public ContextPathRouteBuilder(ExecutionHandleLocator executionHandleLocator,
                                       UriNamingStrategy uriNamingStrategy,
                                       ConversionService conversionService) {
            super(executionHandleLocator, uriNamingStrategy, conversionService);
        }

        @Override
        protected UriRoute buildBeanRoute(String httpMethodName, HttpMethod httpMethod, String uri, BeanDefinition<?> beanDefinition, ExecutableMethod<?, ?> method) {
            return super.buildBeanRoute(httpMethodName, httpMethod, "/cp" + uri, beanDefinition, method);
        }
    }

    @Controller("/replaced")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class ReplacedController {

        @Get("/get")
        String get() {
            return "get";
        }

        @Post("/post")
        String post() {
            return "post";
        }

        @CustomHttpMethod(method = "LOCK", value = "/custom")
        String custom() {
            return "custom";
        }
    }
}
