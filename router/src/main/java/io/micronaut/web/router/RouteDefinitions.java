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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.CustomHttpMethod;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Head;
import io.micronaut.http.annotation.HttpMethodMapping;
import io.micronaut.http.annotation.Options;
import io.micronaut.http.annotation.Patch;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.Query;
import io.micronaut.http.annotation.Trace;
import io.micronaut.http.annotation.UriMapping;
import io.micronaut.http.uri.UriTemplate;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Derives the URI routes of a controller method from its annotations. Shared by the runtime
 * {@link AnnotatedMethodRouteBuilder} and the compile-time generator of precompiled routes, so
 * that both derive the same routes.
 * <p>Annotations are looked up by name, so this works on the annotation metadata of both a
 * compiled {@code ExecutableMethod} and a method element of the annotation processor.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class RouteDefinitions {
    private static final MediaType[] DEFAULT_MEDIA_TYPES = {MediaType.APPLICATION_JSON_TYPE};

    /**
     * The HTTP method annotations with a route of their own, by annotation name.
     */
    private static final Map<String, HttpMethod> METHODS = Map.of(
        Get.class.getName(), HttpMethod.GET,
        Post.class.getName(), HttpMethod.POST,
        Put.class.getName(), HttpMethod.PUT,
        Patch.class.getName(), HttpMethod.PATCH,
        Query.class.getName(), HttpMethod.QUERY,
        Delete.class.getName(), HttpMethod.DELETE,
        Head.class.getName(), HttpMethod.HEAD,
        Options.class.getName(), HttpMethod.OPTIONS,
        Trace.class.getName(), HttpMethod.TRACE,
        CustomHttpMethod.class.getName(), HttpMethod.CUSTOM
    );

    private RouteDefinitions() {
    }

    /**
     * Derive the URI routes of a controller method. {@code @Error} methods have no URI route and
     * yield none.
     *
     * @param beanMetadata   The annotation metadata of the controller
     * @param methodMetadata The annotation metadata of the method
     * @param methodName     The method name
     * @param uriResolver    Resolves the controller URI and default method URIs
     * @return The routes, in the order they are registered
     */
    public static List<RouteSpec> resolve(AnnotationMetadata beanMetadata,
                                          AnnotationMetadata methodMetadata,
                                          String methodName,
                                          UriResolver uriResolver) {
        Optional<String> actionAnnotation = methodMetadata.getAnnotationNameByStereotype(HttpMethodMapping.class);
        if (actionAnnotation.isPresent()) {
            HttpMethod httpMethod = METHODS.get(actionAnnotation.get());
            if (httpMethod == null) {
                // e.g. @Error, which is not a URI route
                return List.of();
            }
            int port = beanMetadata.intValue(Controller.class, "port").orElse(-1);
            List<RouteSpec> routes = new ArrayList<>(2);
            for (String uri : resolveUrisMapping(actionAnnotation.get(), methodMetadata)) {
                String resolvedUri = resolveUri(uri, methodName, uriResolver);
                switch (httpMethod) {
                    case GET -> {
                        MediaType[] produces = resolveProduces(methodMetadata);
                        routes.add(new RouteSpec(HttpMethod.GET.name(), HttpMethod.GET, resolvedUri, null, produces, false, port, false));
                        if (methodMetadata.booleanValue(Get.class, "headRoute").orElse(true)) {
                            routes.add(new RouteSpec(HttpMethod.HEAD.name(), HttpMethod.HEAD, resolvedUri, null, produces, true, port, false));
                        }
                    }
                    case HEAD, TRACE ->
                        routes.add(new RouteSpec(httpMethod.name(), httpMethod, resolvedUri, null, null, false, port, false));
                    case CUSTOM -> {
                        String name = methodMetadata.stringValue(CustomHttpMethod.class, "method").orElseThrow();
                        routes.add(new RouteSpec(name, HttpMethod.CUSTOM, resolvedUri, resolveConsumes(methodMetadata), resolveProduces(methodMetadata), false, port, false));
                    }
                    default ->
                        routes.add(new RouteSpec(httpMethod.name(), httpMethod, resolvedUri, resolveConsumes(methodMetadata), resolveProduces(methodMetadata), false, port, false));
                }
            }
            return routes;
        }
        if (methodMetadata.isDeclaredAnnotationPresent(UriMapping.class)) {
            Set<String> uris = CollectionUtils.setOf(methodMetadata.stringValues(UriMapping.class, "uris"));
            uris.add(methodMetadata.stringValue(UriMapping.class).orElse(UriMapping.DEFAULT_URI));
            List<RouteSpec> routes = new ArrayList<>(uris.size());
            for (String uri : uris) {
                routes.add(new RouteSpec(
                    HttpMethod.GET.name(),
                    HttpMethod.GET,
                    resolveUri(uri, methodName, uriResolver),
                    null,
                    MediaType.of(methodMetadata.stringValues(Produces.class)),
                    false,
                    -1,
                    true
                ));
            }
            return routes;
        }
        return List.of();
    }

    private static Set<String> resolveUrisMapping(String httpMethodAnnotation, AnnotationMetadata method) {
        Set<String> uris = CollectionUtils.setOf(method.stringValues(httpMethodAnnotation, "uris"));
        Optional<String> uri = method.stringValue(HttpMethodMapping.class);
        if (uris.isEmpty()) {
            uris.add(uri.orElse(UriMapping.DEFAULT_URI));
        } else {
            uri.ifPresent(uris::add);
        }
        return uris;
    }

    private static String resolveUri(String value, String methodName, UriResolver uriResolver) {
        UriTemplate rootUri = UriTemplate.of(uriResolver.controllerUri());
        if (StringUtils.isNotEmpty(value)) {
            return rootUri.nest(value).toString();
        }
        return rootUri.nest(uriResolver.methodUri(methodName)).toString();
    }

    private static MediaType[] resolveConsumes(AnnotationMetadata method) {
        MediaType[] consumes = MediaType.of(method.stringValues(Consumes.class));
        return ArrayUtils.isEmpty(consumes) ? DEFAULT_MEDIA_TYPES : consumes;
    }

    private static MediaType[] resolveProduces(AnnotationMetadata method) {
        MediaType[] produces = MediaType.of(method.stringValues(Produces.class));
        return ArrayUtils.isEmpty(produces) ? DEFAULT_MEDIA_TYPES : produces;
    }

    /**
     * Resolves the URIs of the controller and of methods without an explicit URI, see
     * {@link RouteBuilder.UriNamingStrategy}.
     */
    public interface UriResolver {
        /**
         * @return The URI of the controller
         */
        String controllerUri();

        /**
         * @param methodName The method name
         * @return The URI of a method that declares none
         */
        String methodUri(String methodName);
    }

    /**
     * A URI route of a controller method.
     *
     * @param httpMethodName The HTTP method name, which differs from {@link HttpMethod#name()} for custom methods
     * @param httpMethod     The HTTP method
     * @param uri            The URI template, the controller URI nested with the method URI
     * @param consumes       The consumed media types, or {@code null} to keep the route builder default
     * @param produces       The produced media types, or {@code null} to keep the route builder default
     * @param implicitHead   Whether this is the implicit {@code HEAD} route of a {@code GET} method
     * @param port           The port the route is exposed on, or {@code -1}
     * @param declaringTypeTarget Whether the route targets the method through its declaring type
     *                       rather than through the bean definition (a plain {@code @UriMapping} method)
     */
    public record RouteSpec(String httpMethodName,
                            HttpMethod httpMethod,
                            String uri,
                            MediaType @Nullable [] consumes,
                            MediaType @Nullable [] produces,
                            boolean implicitHead,
                            int port,
                            boolean declaringTypeTarget) {
    }
}
