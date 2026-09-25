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
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.version.annotation.Version;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.FilterMatcher;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.server.cors.CrossOrigin;
import io.micronaut.http.server.tck.CorsUtils;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import io.micronaut.web.router.MethodBasedRouteMatch;
import io.micronaut.web.router.RouteAttributes;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.micronaut.http.server.tck.CorsUtils.assertCorsHeaders;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Framework features driven by the annotations of a route, on handler routes that get their annotations with
 * {@link io.micronaut.web.router.builder.HttpRouteSpec#annotationMetadata(AnnotationMetadataProvider)}, from the
 * annotations of a method only, or from the bean method the route implements:
 * route versioning ({@link Version}), CORS ({@link CrossOrigin}) and its preflight, {@code OPTIONS} with
 * {@code Allow}, filter binding with a {@link FilterMatcher} annotation, and a security-style annotation read by a
 * server filter from the matched route. Each is checked against the equivalent controller.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class HandlerRouteAnnotationsTest {
    public static final String SPEC_NAME = "HandlerRouteAnnotationsTest";

    private static final Map<String, Object> VERSIONING = Map.of(
        "micronaut.router.versioning.enabled", StringUtils.TRUE,
        "micronaut.router.versioning.header.enabled", StringUtils.TRUE
    );

    @Test
    void theVersionAnnotationSelectsTheHandlerRoute() throws IOException {
        try (ServerUnderTest server = server(VERSIONING)) {
            for (String prefix : List.of("/ctl-annotations", "/fn-annotations", "/fn-implementing")) {
                AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET(prefix + "/ping").header("X-API-VERSION", "2"), HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("pong v2")
                    .build());
                AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET(prefix + "/ping").header("X-API-VERSION", "1"), HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("pong v1")
                    .build());
            }
        }
    }

    @Test
    void aCrossOriginAnnotationAnswersThePreflightOfAHandlerRoute() throws IOException {
        try (ServerUnderTest server = server(Map.of())) {
            for (String prefix : List.of("/ctl-annotations", "/fn-annotations")) {
                AssertionUtils.assertDoesNotThrow(server, preflight(prefix + "/cors", "https://foo.com", HttpMethod.GET), HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .assertResponse(response -> {
                        assertCorsHeaders(response, "https://foo.com", HttpMethod.GET, false);
                        assertFalse(response.getHeaders().names().contains(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS));
                    })
                    .build());
                // an origin the annotation does not allow is refused, as for the controller
                AssertionUtils.assertThrows(server, preflight(prefix + "/cors", "https://bar.com", HttpMethod.GET), HttpResponseAssertion.builder()
                    .status(HttpStatus.FORBIDDEN)
                    .assertResponse(CorsUtils::assertCorsHeadersNotPresent)
                    .build());
                AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET(prefix + "/cors").header(HttpHeaders.ORIGIN, "https://foo.com"), HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("cors")
                    .header(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://foo.com")
                    .build());
                AssertionUtils.assertThrows(server, HttpRequest.GET(prefix + "/cors").header(HttpHeaders.ORIGIN, "https://bar.com"), HttpResponseAssertion.builder()
                    .status(HttpStatus.FORBIDDEN)
                    .assertResponse(CorsUtils::assertCorsHeadersNotPresent)
                    .build());
            }
        }
    }

    @Test
    void optionsListsTheMethodsOfTheHandlerRoutes() throws IOException {
        try (ServerUnderTest server = server(Map.of("micronaut.server.dispatch-options-requests", StringUtils.TRUE))) {
            for (String prefix : List.of("/ctl-annotations", "/fn-annotations")) {
                AssertionUtils.assertDoesNotThrow(server, HttpRequest.OPTIONS(prefix + "/items/1"), HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .assertResponse(response -> assertEquals(
                        Set.of(HttpMethod.GET.name(), HttpMethod.POST.name(), HttpMethod.HEAD.name(), HttpMethod.OPTIONS.name()),
                        Set.copyOf(response.getHeaders().getAll(HttpHeaders.ALLOW).stream()
                            .flatMap(value -> List.of(value.split(",")).stream())
                            .map(String::trim)
                            .toList()),
                        prefix))
                    .build());
                AssertionUtils.assertThrows(server, HttpRequest.OPTIONS(prefix + "/options"), HttpResponseAssertion.builder()
                    .status(HttpStatus.I_AM_A_TEAPOT)
                    .build());
            }
        }
    }

    @Test
    void optionsWithoutDispatchingIsAMethodNotAllowed() throws IOException {
        try (ServerUnderTest server = server(Map.of())) {
            for (String prefix : List.of("/ctl-annotations", "/fn-annotations")) {
                AssertionUtils.assertThrows(server, HttpRequest.OPTIONS(prefix + "/items/1"), HttpResponseAssertion.builder()
                    .status(HttpStatus.METHOD_NOT_ALLOWED)
                    .assertResponse(response -> {
                        String allow = String.join(",", response.getHeaders().getAll(HttpHeaders.ALLOW));
                        assertTrue(allow.contains(HttpMethod.GET.name()), allow);
                        assertTrue(allow.contains(HttpMethod.POST.name()), allow);
                    })
                    .build());
            }
        }
    }

    @Test
    void aSecurityAnnotationOfTheRouteReachesAServerFilter() throws IOException {
        try (ServerUnderTest server = server(Map.of())) {
            for (String prefix : List.of("/ctl-annotations", "/fn-annotations", "/fn-implementing")) {
                AssertionUtils.assertThrows(server, HttpRequest.GET(prefix + "/admin"), HttpResponseAssertion.builder()
                    .status(HttpStatus.FORBIDDEN)
                    .build());
                AssertionUtils.assertThrows(server, HttpRequest.GET(prefix + "/admin").header("X-Role", "user"), HttpResponseAssertion.builder()
                    .status(HttpStatus.FORBIDDEN)
                    .build());
                AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET(prefix + "/admin").header("X-Role", "admin"), HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("admin")
                    .build());
            }
        }
    }

    @Test
    void aFilterMatcherAnnotationBindsAFilterToTheHandlerRoute() throws IOException {
        try (ServerUnderTest server = server(Map.of())) {
            for (String prefix : List.of("/ctl-annotations", "/fn-annotations")) {
                AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET(prefix + "/audited"), HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("audited")
                    .header("X-Audited", "true")
                    .build());
                AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET(prefix + "/plain"), HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("plain")
                    .assertResponse(response -> assertFalse(response.getHeaders().contains("X-Audited"), prefix))
                    .build());
            }
        }
    }

    private static ServerUnderTest server(Map<String, Object> configuration) {
        return ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME, configuration);
    }

    private static MutableHttpRequest<?> preflight(String uri, String origin, HttpMethod method) {
        return HttpRequest.OPTIONS(uri)
            .header(HttpHeaders.ACCEPT, MediaType.TEXT_PLAIN)
            .header(HttpHeaders.ORIGIN, origin)
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, method);
    }

    private static HttpResponse<?> text(String text) {
        return HttpResponse.ok(text).contentType(MediaType.TEXT_PLAIN_TYPE);
    }

    /**
     * A security-style annotation: the role a route requires.
     */
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.TYPE})
    @interface RequiresRole {
        String value();
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
     * The annotated methods that give the handler routes their annotations.
     */
    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class AnnotatedTargets {
        @Executable
        @Version("1")
        String pingV1() {
            return "pong v1";
        }

        @Executable
        @Version("2")
        String pingV2() {
            return "pong v2";
        }

        @Executable
        @CrossOrigin("https://foo.com")
        String cors() {
            return "cors";
        }

        @Executable
        @RequiresRole("admin")
        String admin() {
            return "admin";
        }

        @Executable
        @Audited
        String audited() {
            return "audited";
        }
    }

    @Controller("/ctl-annotations")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class AnnotatedController {
        @Version("1")
        @Get(value = "/ping", produces = MediaType.TEXT_PLAIN)
        String pingV1() {
            return "pong v1";
        }

        @Version("2")
        @Get(value = "/ping", produces = MediaType.TEXT_PLAIN)
        String pingV2() {
            return "pong v2";
        }

        @CrossOrigin("https://foo.com")
        @Get(value = "/cors", produces = MediaType.TEXT_PLAIN)
        String cors() {
            return "cors";
        }

        @Get(value = "/items/{id}", produces = MediaType.TEXT_PLAIN)
        String item(String id) {
            return "item " + id;
        }

        @Post(value = "/items/{id}", produces = MediaType.TEXT_PLAIN)
        String saveItem(String id) {
            return "saved " + id;
        }

        @io.micronaut.http.annotation.Options("/options")
        HttpResponse<?> options() {
            return HttpResponse.status(HttpStatus.I_AM_A_TEAPOT);
        }

        @RequiresRole("admin")
        @Get(value = "/admin", produces = MediaType.TEXT_PLAIN)
        String admin() {
            return "admin";
        }

        @Audited
        @Get(value = "/audited", produces = MediaType.TEXT_PLAIN)
        String audited() {
            return "audited";
        }

        @Get(value = "/plain", produces = MediaType.TEXT_PLAIN)
        String plain() {
            return "plain";
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class AnnotatedRoutes implements HttpRoutes {
        private final BeanContext beanContext;
        private final AnnotatedTargets targets;

        AnnotatedRoutes(BeanContext beanContext, AnnotatedTargets targets) {
            this.beanContext = beanContext;
            this.targets = targets;
        }

        private AnnotationMetadataProvider metadataOf(String method) {
            // the annotations of the method, not the method: the route does not implement it
            AnnotationMetadata metadata = beanContext.getBeanDefinition(AnnotatedTargets.class).getRequiredMethod(method).getAnnotationMetadata();
            return new AnnotationMetadataProvider() {
                @Override
                public AnnotationMetadata getAnnotationMetadata() {
                    return metadata;
                }
            };
        }

        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.path("/fn-annotations", group -> {
                group.GET("/ping", (request, pathVariables) -> text(targets.pingV1()))
                    .annotationMetadata(metadataOf("pingV1"));
                group.GET("/ping", (request, pathVariables) -> text(targets.pingV2()))
                    .annotationMetadata(metadataOf("pingV2"));
                group.GET("/cors", (request, pathVariables) -> text(targets.cors()))
                    .annotationMetadata(metadataOf("cors"));
                group.GET("/items/{id}", (request, pathVariables) -> text("item " + pathVariables.getString("id")));
                group.POST("/items/{id}", (request, pathVariables) -> text("saved " + pathVariables.getString("id")));
                group.handle(HttpMethod.OPTIONS, "/options", (request, pathVariables) -> HttpResponse.status(HttpStatus.I_AM_A_TEAPOT));
                group.GET("/admin", (request, pathVariables) -> text(targets.admin()))
                    .annotationMetadata(metadataOf("admin"));
                group.GET("/audited", (request, pathVariables) -> text(targets.audited()))
                    .annotationMetadata(metadataOf("audited"));
                group.GET("/plain", (request, pathVariables) -> text("plain"));
            });
            routes.path("/fn-implementing", group -> {
                group.GET("/ping", (request, pathVariables) -> text(targets.pingV1()))
                    .annotationMetadata(beanContext.getBeanDefinition(AnnotatedTargets.class).getRequiredMethod("pingV1"));
                group.GET("/ping", (request, pathVariables) -> text(targets.pingV2()))
                    .annotationMetadata(beanContext.getBeanDefinition(AnnotatedTargets.class).getRequiredMethod("pingV2"));
                group.GET("/admin", (request, pathVariables) -> text(targets.admin()))
                    .annotationMetadata(beanContext.getBeanDefinition(AnnotatedTargets.class).getRequiredMethod("admin"));
            });
        }
    }

    /**
     * Enforces {@link RequiresRole} the way a security rule does: it reads the annotation from the matched route.
     */
    @ServerFilter("/**")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class RoleFilter {
        @RequestFilter
        @Nullable
        HttpResponse<?> requireRole(HttpRequest<?> request) {
            String required = RouteAttributes.getRouteMatch(request)
                .filter(MethodBasedRouteMatch.class::isInstance)
                .map(MethodBasedRouteMatch.class::cast)
                .flatMap(match -> match.stringValue(RequiresRole.class))
                .orElse(null);
            if (required != null && !required.equals(request.getHeaders().get("X-Role"))) {
                return HttpResponse.status(HttpStatus.FORBIDDEN);
            }
            return null;
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
