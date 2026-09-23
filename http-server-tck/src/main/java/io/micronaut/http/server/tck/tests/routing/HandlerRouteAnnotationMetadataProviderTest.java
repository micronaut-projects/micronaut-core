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
package io.micronaut.http.server.tck.tests.routing;

import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.web.router.MethodBasedRouteInfo;
import io.micronaut.web.router.RouteAttributes;
import io.micronaut.web.router.RouteInfo;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * The element a handler route has the annotations of, given with
 * {@link io.micronaut.web.router.builder.HttpRouteSpec#annotationMetadata(AnnotationMetadataProvider)}, is the one
 * an integration reads back from the matched route with {@link MethodBasedRouteInfo#getAnnotationMetadataProvider()}:
 * a bean method the route implements, any other annotated element, or none, and the bean method of a controller
 * route.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class HandlerRouteAnnotationMetadataProviderTest {
    public static final String SPEC_NAME = "HandlerRouteAnnotationMetadataProviderTest";

    @Test
    void aHandlerRouteGivenABeanMethodExposesTheMethod() throws IOException {
        assertBody("/provider/method", "method target of Target, route of Target");
    }

    @Test
    void aHandlerRouteGivenAnotherElementExposesItsAnnotationsOnly() throws IOException {
        assertBody("/provider/definition", "annotations marked=true");
    }

    @Test
    void aHandlerRouteWithoutAnnotationsExposesNoElement() throws IOException {
        assertBody("/provider/none", "none");
    }

    @Test
    void aControllerRouteExposesItsBeanMethod() throws IOException {
        assertBody("/provider/controller", "method controller of ProviderController, route of ProviderController");
    }

    private static void assertBody(String path, String body) throws IOException {
        try (ServerUnderTest server = ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME)) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET(path), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body(body)
                .build());
        }
    }

    /**
     * Describes the element of the annotations of the route of a request, as an integration reads it.
     *
     * @param request The request
     * @return The description
     */
    static String describe(HttpRequest<?> request) {
        RouteInfo<?> route = RouteAttributes.getRouteInfo(request).orElseThrow();
        if (!(route instanceof MethodBasedRouteInfo<?, ?> methodRoute)) {
            return "not a method route";
        }
        return methodRoute.getAnnotationMetadataProvider()
            .map(provider -> provider instanceof ExecutableMethod<?, ?> method
                // the route implements the method: it is declared by the bean class
                ? "method " + method.getMethodName() + " of " + name(method.getDeclaringType())
                    + ", route of " + name(route.getDeclaringType())
                : "annotations marked=" + provider.getAnnotationMetadata().hasAnnotation(Marked.class))
            .orElse("none");
    }

    private static String name(Class<?> type) {
        if (type == Target.class) {
            return "Target";
        }
        return type == ProviderController.class ? "ProviderController" : "another type";
    }

    private static HttpResponse<?> text(String body) {
        return HttpResponse.ok(body).contentType(MediaType.TEXT_PLAIN_TYPE);
    }

    @Retention(RetentionPolicy.RUNTIME)
    @interface Marked {
    }

    @Singleton
    @Marked
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Target {
        @Executable
        String target() {
            return "target";
        }
    }

    @Controller("/provider")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class ProviderController {
        @Get("/controller")
        @Produces(MediaType.TEXT_PLAIN)
        String controller(HttpRequest<?> request) {
            return describe(request);
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class ProviderRoutes implements HttpRoutes {
        private final BeanContext beanContext;

        ProviderRoutes(BeanContext beanContext) {
            this.beanContext = beanContext;
        }

        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.GET("/provider/method", (request, pathVariables) -> text(describe(request)))
                .annotationMetadata(beanContext.getBeanDefinition(Target.class).getRequiredMethod("target"));
            routes.GET("/provider/definition", (request, pathVariables) -> text(describe(request)))
                .annotationMetadata(beanContext.getBeanDefinition(Target.class));
            routes.GET("/provider/none", (request, pathVariables) -> text(describe(request)));
        }
    }
}
