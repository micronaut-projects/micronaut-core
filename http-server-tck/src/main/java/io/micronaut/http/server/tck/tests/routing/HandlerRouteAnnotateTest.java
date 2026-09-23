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
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.version.annotation.Version;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.FilterMatcher;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import io.micronaut.web.router.RouteAttributes;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Annotations put on handler routes and groups with
 * {@link io.micronaut.web.router.builder.HttpRouteSpec#annotate(AnnotationValue)} and the other
 * {@code annotate} methods: a {@link FilterMatcher} annotation binds its filter, directly or as a
 * stereotype given in the annotation value, {@link Version} selects the route, later annotations
 * override earlier ones, and the annotations layer over the ones of a bean method given with
 * {@code annotationMetadata(method)}.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class HandlerRouteAnnotateTest {
    public static final String SPEC_NAME = "HandlerRouteAnnotateTest";

    private static final Map<String, Object> VERSIONING = Map.of(
        "micronaut.router.versioning.enabled", StringUtils.TRUE,
        "micronaut.router.versioning.header.enabled", StringUtils.TRUE
    );

    @Test
    void aFilterMatcherAnnotationPutOnARouteOrAGroupBindsItsFilter() throws IOException {
        try (ServerUnderTest server = server(Map.of())) {
            for (String path : List.of("/annotate/audited", "/annotate/group/inner", "/annotate/group/nested/inner",
                "/annotate/several", "/annotate/stereotype", "/annotate/method")) {
                AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET(path), HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .header("X-Audited", "true")
                    .build());
            }
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/annotate/plain"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("plain")
                .assertResponse(response -> assertFalse(response.getHeaders().contains("X-Audited")))
                .build());
        }
    }

    @Test
    void theVersionAnnotationPutOnARouteSelectsIt() throws IOException {
        try (ServerUnderTest server = server(VERSIONING)) {
            for (String version : List.of("1", "2")) {
                AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/annotate/ping").header("X-API-VERSION", version), HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("pong v" + version)
                    .build());
            }
        }
    }

    @Test
    void laterAnnotationsOverrideEarlierOnes() throws IOException {
        try (ServerUnderTest server = server(Map.of())) {
            // route over route, route over group, nested group over group, builder: the last one
            for (String path : List.of("/annotate/replaced", "/annotate/group/override", "/annotate/group/nested/inner", "/annotate/several")) {
                assertVersion(server, path, "2");
            }
            assertVersion(server, "/annotate/group/inner", "1");
        }
    }

    @Test
    void theAnnotationsOfTheRouteLayerOverTheOnesOfTheBeanMethodItImplements() throws IOException {
        try (ServerUnderTest server = server(Map.of())) {
            // the method has @Version("1"); the route adds @Audited
            assertVersion(server, "/annotate/method", "1");
            assertVersion(server, "/annotate/method-override", "3");
        }
    }

    private static void assertVersion(ServerUnderTest server, String path, String version) {
        AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET(path), HttpResponseAssertion.builder()
            .status(HttpStatus.OK)
            .body("version " + version)
            .build());
    }

    private static ServerUnderTest server(Map<String, Object> configuration) {
        return ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME, configuration);
    }

    private static HttpResponse<?> text(String text) {
        return HttpResponse.ok(text).contentType(MediaType.TEXT_PLAIN_TYPE);
    }

    /**
     * Answers with the version the matched route has.
     */
    private static HttpResponse<?> version(HttpRequest<?> request) {
        String version = RouteAttributes.getRouteInfo(request)
            .flatMap(route -> route.getAnnotationMetadata().stringValue(Version.class))
            .orElse("none");
        return text("version " + version);
    }

    /**
     * Binds {@link AuditFilter} to the routes that carry it.
     */
    @FilterMatcher
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.TYPE})
    @interface Audited {
    }

    /**
     * An annotation whose stereotype is {@link Audited}.
     */
    @Audited
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.TYPE})
    @interface Payment {
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class VersionedTarget {
        @Executable
        @Version("1")
        String target() {
            return "target";
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class AnnotateRoutes implements HttpRoutes {
        private final BeanContext beanContext;

        AnnotateRoutes(BeanContext beanContext) {
            this.beanContext = beanContext;
        }

        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.GET("/annotate/audited", (request, pathVariables) -> text("audited")).annotate(Audited.class);
            routes.GET("/annotate/plain", (request, pathVariables) -> text("plain"));
            routes.path("/annotate/group", group -> {
                group.annotate(Audited.class);
                group.annotate(AnnotationValue.builder(Version.class).value("1").build());
                group.GET("/inner", (request, pathVariables) -> version(request));
                group.GET("/override", (request, pathVariables) -> version(request))
                    .annotate(AnnotationValue.builder(Version.class).value("2").build());
                group.path("/nested", nested -> {
                    nested.annotate(AnnotationValue.builder(Version.class).value("2").build());
                    nested.GET("/inner", (request, pathVariables) -> version(request));
                });
            });
            routes.GET("/annotate/ping", (request, pathVariables) -> text("pong v1"))
                .annotate(AnnotationValue.builder(Version.class).value("1").build());
            routes.GET("/annotate/ping", (request, pathVariables) -> text("pong v2"))
                .annotate(Version.class, version -> version.value("2"));
            routes.GET("/annotate/replaced", (request, pathVariables) -> version(request))
                .annotate(AnnotationValue.builder(Version.class).value("1").build())
                .annotate(AnnotationValue.builder(Version.class).value("2").build());
            routes.GET("/annotate/several", (request, pathVariables) -> version(request))
                .annotate(Version.class, version -> version.value("1"))
                .annotate(Audited.class.getName())
                .annotate(Version.class.getName(), version -> version.value("2"));
            // the meta-annotations of an annotation type are not known at runtime: the value carries the stereotype
            routes.GET("/annotate/stereotype", (request, pathVariables) -> text("payment"))
                .annotate(AnnotationValue.builder(Payment.class).stereotype(AnnotationValue.builder(Audited.class).build()).build());
            routes.GET("/annotate/method", (request, pathVariables) -> version(request))
                .annotationMetadata(beanContext.getBeanDefinition(VersionedTarget.class).getRequiredMethod("target"))
                .annotate(Audited.class);
            routes.GET("/annotate/method-override", (request, pathVariables) -> version(request))
                .annotationMetadata(beanContext.getBeanDefinition(VersionedTarget.class).getRequiredMethod("target"))
                .annotate(AnnotationValue.builder(Version.class).value("3").build());
        }
    }

    @Audited
    @ServerFilter("/**")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class AuditFilter {
        @ResponseFilter
        void audit(MutableHttpResponse<?> response) {
            response.header("X-Audited", "true");
        }
    }
}
