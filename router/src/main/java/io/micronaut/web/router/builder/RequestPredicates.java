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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Shorthands of {@link RouteCondition} for {@link HttpRouteSpec#where} and
 * {@link HttpRouteGroup#where}: conditions on the headers, query parameters, accepted media
 * types, content type and method of a request. Each returns a {@link RouteCondition}, which is
 * a {@link Predicate} too, so they combine with {@link RouteCondition#and},
 * {@link RouteCondition#or} and {@link RouteCondition#negate}, with lambdas, or with
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
 * <p>{@link #accept} and {@link #contentType} compare media types, with the wildcards of either
 * side, and read the list of the {@code Accept} header, in which a request without the header
 * accepts every type: they are {@link RouteCondition.Custom} conditions, since a matcher of the
 * string of a header does not express that. So is a condition that takes a lambda of a
 * value.</p>
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
     * @see RouteCondition#header(String)
     */
    public static RouteCondition header(String name) {
        return RouteCondition.header(name);
    }

    /**
     * A request with a header of the name that has the value, one of its values if the header
     * is repeated.
     *
     * @param name  The name of the header, case-insensitive
     * @param value The value, case-sensitive
     * @return The condition
     * @see RouteCondition#header(String, String)
     */
    public static RouteCondition header(String name, String value) {
        return RouteCondition.header(name, value);
    }

    /**
     * A request with a header of the name whose value meets a condition, one of its values if
     * the header is repeated. {@link RouteCondition#header(String, ValueMatcher)} is a condition
     * the router can read.
     *
     * @param name  The name of the header, case-insensitive
     * @param value The condition on the value
     * @return The condition, a {@link RouteCondition.Custom} one
     */
    public static RouteCondition header(String name, Predicate<String> value) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(value, "value");
        return new RouteCondition.Custom(new ValuePredicate(name, value, false));
    }

    /**
     * A request with a query parameter of the name, with or without a value, e.g.
     * {@code queryParam("debug")} for {@code ?debug}.
     *
     * @param name The name of the query parameter, case-sensitive
     * @return The condition
     * @see RouteCondition#query(String)
     */
    public static RouteCondition queryParam(String name) {
        return RouteCondition.query(name);
    }

    /**
     * A request with a query parameter of the name that has the value, one of its values if the
     * parameter is repeated, e.g. {@code queryParam("format", "csv")} for {@code ?format=csv}.
     *
     * @param name  The name of the query parameter, case-sensitive
     * @param value The decoded value, case-sensitive
     * @return The condition
     * @see RouteCondition#query(String, String)
     */
    public static RouteCondition queryParam(String name, String value) {
        return RouteCondition.query(name, value);
    }

    /**
     * A request with a query parameter of the name whose value meets a condition, one of its
     * values if the parameter is repeated. {@link RouteCondition#query(String, ValueMatcher)}
     * is a condition the router can read.
     *
     * @param name  The name of the query parameter, case-sensitive
     * @param value The condition on the decoded value
     * @return The condition, a {@link RouteCondition.Custom} one
     */
    public static RouteCondition queryParam(String name, Predicate<String> value) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(value, "value");
        return new RouteCondition.Custom(new ValuePredicate(name, value, true));
    }

    /**
     * A request that accepts one of the media types: a type of its {@code Accept} header is
     * compatible with one of them, e.g. {@code text/*} with {@code text/csv}. A request without
     * an {@code Accept} header accepts every type.
     *
     * @param mediaTypes The media types
     * @return The condition, a {@link RouteCondition.Custom} one
     */
    public static RouteCondition accept(MediaType... mediaTypes) {
        return new RouteCondition.Custom(new MediaTypePredicate(mediaTypes(mediaTypes), true));
    }

    /**
     * A request whose content type is compatible with one of the media types, e.g.
     * {@code application/*} with {@code application/json}. A request without a content type
     * does not meet the condition.
     *
     * @param mediaTypes The media types
     * @return The condition, a {@link RouteCondition.Custom} one
     */
    public static RouteCondition contentType(MediaType... mediaTypes) {
        return new RouteCondition.Custom(new MediaTypePredicate(mediaTypes(mediaTypes), false));
    }

    /**
     * A request of one of the HTTP methods, e.g. for the routes of a group, or of a route of
     * several methods: {@code group.where(method(HttpMethod.GET).or(header("X-Write-Token")))}.
     * A custom method is {@link HttpMethod#CUSTOM}.
     *
     * @param methods The methods
     * @return The condition
     * @see RouteCondition#method(HttpMethod...)
     */
    public static RouteCondition method(HttpMethod... methods) {
        return RouteCondition.method(methods);
    }

    /**
     * A request that meets every condition. The router evaluates them cheapest first until one
     * is not met, the lambdas last, in order.
     *
     * @param conditions The conditions, a lambda becomes a {@link RouteCondition.Custom} condition
     * @return The condition, met by every request if there are none
     */
    @SafeVarargs
    public static RouteCondition all(Predicate<HttpRequest<?>>... conditions) {
        return new RouteCondition.AllOf(conditions(conditions));
    }

    /**
     * A request that meets one of the conditions. The router evaluates them cheapest first
     * until one is met, the lambdas last, in order.
     *
     * @param conditions The conditions, a lambda becomes a {@link RouteCondition.Custom} condition
     * @return The condition, met by no request if there are none
     */
    @SafeVarargs
    public static RouteCondition any(Predicate<HttpRequest<?>>... conditions) {
        return new RouteCondition.AnyOf(conditions(conditions));
    }

    private static List<RouteCondition> conditions(Predicate<HttpRequest<?>>[] conditions) {
        List<RouteCondition> result = new ArrayList<>(conditions.length);
        for (Predicate<HttpRequest<?>> condition : conditions) {
            result.add(RouteCondition.custom(condition));
        }
        return result;
    }

    private static List<MediaType> mediaTypes(MediaType[] mediaTypes) {
        Objects.requireNonNull(mediaTypes, "mediaTypes");
        if (mediaTypes.length == 0) {
            throw new IllegalArgumentException("A media type is required");
        }
        return List.of(mediaTypes);
    }

    /**
     * A condition on the values of a header or of a query parameter.
     *
     * @param name      The name of the header or of the query parameter
     * @param value     The condition on a value
     * @param parameter Whether it is a query parameter
     */
    private record ValuePredicate(String name, Predicate<String> value, boolean parameter) implements Predicate<HttpRequest<?>> {
        @Override
        public boolean test(HttpRequest<?> request) {
            List<String> values = parameter ? request.getParameters().getAll(name) : request.getHeaders().getAll(name);
            for (String candidate : values) {
                if (candidate != null && value.test(candidate)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public String toString() {
            return (parameter ? "queryParam(" : "header(") + name + ", " + value + ")";
        }
    }

    /**
     * A condition on the accepted media types or on the content type of a request.
     *
     * @param types  The media types
     * @param accept Whether it is on the accepted media types
     */
    private record MediaTypePredicate(List<MediaType> types, boolean accept) implements Predicate<HttpRequest<?>> {
        @Override
        public boolean test(HttpRequest<?> request) {
            if (!accept) {
                return request.getContentType().map(this::compatible).orElse(false);
            }
            Collection<MediaType> accepted = request.accept();
            if (accepted.isEmpty()) {
                return true;
            }
            for (MediaType acceptedType : accepted) {
                if (compatible(acceptedType)) {
                    return true;
                }
            }
            return false;
        }

        private boolean compatible(MediaType type) {
            for (MediaType candidate : types) {
                if (type.matches(candidate) || candidate.matches(type)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public String toString() {
            return (accept ? "accept" : "contentType") + types;
        }
    }
}
