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
package io.micronaut.web.router.builder;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Conditions on a request for {@link HttpRouteSpec#where} and {@link HttpRouteGroup#where}: on its
 * headers, query parameters, accepted media types, content type and method. They combine with
 * {@link Predicate#and}, {@link Predicate#or} and {@link Predicate#negate}, or with
 * {@link #all} and {@link #any}.
 *
 * <pre>{@code
 * import static io.micronaut.web.router.builder.RequestPredicates.*;
 *
 * routes.GET("/reports/{id}", csvHandler)
 *     .where(queryParam("format", "csv").or(accept(MediaType.of("text/csv"))));
 * routes.path("/beta", beta -> {
 *     beta.where(header("X-Beta", value -> value.equals("on")));
 *     beta.GET("/search", betaSearchHandler);
 * });
 * }</pre>
 *
 * <p>The URI, the method, the media types a route consumes and produces, and the
 * {@code @RouteCondition} expression of an annotated method are conditions of their own, which
 * also decide the {@code 405}, {@code 415} and {@code 406} answers. A condition of
 * {@code where} does not: a request it rejects is answered as if the route did not exist.
 * Use {@link #accept} and {@link #contentType} to choose among routes that consume or produce
 * the same media types by another criterion, not to replace {@link HttpRouteSpec#consumes} or
 * {@link HttpRouteSpec#produces}.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public final class RequestPredicates {

    private RequestPredicates() {
    }

    /**
     * A request with a header of the name, e.g. {@code header("X-Beta")}.
     *
     * @param name The name of the header, case-insensitive
     * @return The condition
     */
    public static Predicate<HttpRequest<?>> header(String name) {
        Objects.requireNonNull(name, "name");
        return request -> request.getHeaders().contains(name);
    }

    /**
     * A request with a header of the name that has the value, one of its values if the header
     * is repeated.
     *
     * @param name  The name of the header, case-insensitive
     * @param value The value, case-sensitive
     * @return The condition
     */
    public static Predicate<HttpRequest<?>> header(String name, String value) {
        Objects.requireNonNull(value, "value");
        return header(name, value::equals);
    }

    /**
     * A request with a header of the name whose value meets a condition, one of its values if
     * the header is repeated.
     *
     * @param name  The name of the header, case-insensitive
     * @param value The condition on the value
     * @return The condition
     */
    public static Predicate<HttpRequest<?>> header(String name, Predicate<String> value) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(value, "value");
        return request -> anyMatch(request.getHeaders().getAll(name), value);
    }

    /**
     * A request with a query parameter of the name, with or without a value, e.g.
     * {@code queryParam("debug")} for {@code ?debug}.
     *
     * @param name The name of the query parameter, case-sensitive
     * @return The condition
     */
    public static Predicate<HttpRequest<?>> queryParam(String name) {
        Objects.requireNonNull(name, "name");
        return request -> request.getParameters().contains(name);
    }

    /**
     * A request with a query parameter of the name that has the value, one of its values if the
     * parameter is repeated, e.g. {@code queryParam("format", "csv")} for {@code ?format=csv}.
     *
     * @param name  The name of the query parameter, case-sensitive
     * @param value The decoded value, case-sensitive
     * @return The condition
     */
    public static Predicate<HttpRequest<?>> queryParam(String name, String value) {
        Objects.requireNonNull(value, "value");
        return queryParam(name, value::equals);
    }

    /**
     * A request with a query parameter of the name whose value meets a condition, one of its
     * values if the parameter is repeated.
     *
     * @param name  The name of the query parameter, case-sensitive
     * @param value The condition on the decoded value
     * @return The condition
     */
    public static Predicate<HttpRequest<?>> queryParam(String name, Predicate<String> value) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(value, "value");
        return request -> anyMatch(request.getParameters().getAll(name), value);
    }

    /**
     * A request that accepts one of the media types: a type of its {@code Accept} header is
     * compatible with one of them, e.g. {@code text/*} with {@code text/csv}. A request without
     * an {@code Accept} header accepts every type.
     *
     * @param mediaTypes The media types
     * @return The condition
     */
    public static Predicate<HttpRequest<?>> accept(MediaType... mediaTypes) {
        List<MediaType> types = mediaTypes(mediaTypes);
        return request -> {
            Collection<MediaType> accepted = request.accept();
            if (accepted.isEmpty()) {
                return true;
            }
            for (MediaType acceptedType : accepted) {
                if (compatible(acceptedType, types)) {
                    return true;
                }
            }
            return false;
        };
    }

    /**
     * A request whose content type is compatible with one of the media types, e.g.
     * {@code application/*} with {@code application/json}. A request without a content type
     * does not meet the condition.
     *
     * @param mediaTypes The media types
     * @return The condition
     */
    public static Predicate<HttpRequest<?>> contentType(MediaType... mediaTypes) {
        List<MediaType> types = mediaTypes(mediaTypes);
        return request -> request.getContentType().map(contentType -> compatible(contentType, types)).orElse(false);
    }

    /**
     * A request of one of the HTTP methods, e.g. for the routes of a group, or of a route of
     * several methods: {@code group.where(method(HttpMethod.GET).or(header("X-Write-Token")))}.
     * A custom method is {@link HttpMethod#CUSTOM}.
     *
     * @param methods The methods
     * @return The condition
     */
    public static Predicate<HttpRequest<?>> method(HttpMethod... methods) {
        Objects.requireNonNull(methods, "methods");
        if (methods.length == 0) {
            throw new IllegalArgumentException("A method is required");
        }
        // Set.copyOf rejects null elements
        Set<HttpMethod> set = Set.copyOf(Arrays.asList(methods));
        return request -> set.contains(request.getMethod());
    }

    /**
     * A request that meets every condition, evaluated in order until one is not met.
     *
     * @param conditions The conditions
     * @return The condition, met by every request if there are none
     */
    @SafeVarargs
    public static Predicate<HttpRequest<?>> all(Predicate<HttpRequest<?>>... conditions) {
        List<Predicate<HttpRequest<?>>> all = List.of(conditions);
        return request -> {
            for (Predicate<HttpRequest<?>> condition : all) {
                if (!condition.test(request)) {
                    return false;
                }
            }
            return true;
        };
    }

    /**
     * A request that meets one of the conditions, evaluated in order until one is met.
     *
     * @param conditions The conditions
     * @return The condition, met by no request if there are none
     */
    @SafeVarargs
    public static Predicate<HttpRequest<?>> any(Predicate<HttpRequest<?>>... conditions) {
        List<Predicate<HttpRequest<?>>> any = List.of(conditions);
        return request -> {
            for (Predicate<HttpRequest<?>> condition : any) {
                if (condition.test(request)) {
                    return true;
                }
            }
            return false;
        };
    }

    private static boolean anyMatch(List<String> values, Predicate<String> condition) {
        for (String value : values) {
            if (value != null && condition.test(value)) {
                return true;
            }
        }
        return false;
    }

    private static List<MediaType> mediaTypes(MediaType[] mediaTypes) {
        Objects.requireNonNull(mediaTypes, "mediaTypes");
        if (mediaTypes.length == 0) {
            throw new IllegalArgumentException("A media type is required");
        }
        return List.of(mediaTypes);
    }

    private static boolean compatible(MediaType type, List<MediaType> types) {
        for (MediaType candidate : types) {
            if (type.matches(candidate) || candidate.matches(type)) {
                return true;
            }
        }
        return false;
    }
}
