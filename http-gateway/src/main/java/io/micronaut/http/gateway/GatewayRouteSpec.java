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
package io.micronaut.http.gateway;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.http.MediaType;
import io.micronaut.web.router.builder.HttpRouteScope;
import io.micronaut.web.router.builder.HttpRouteSpec;
import io.micronaut.web.router.builder.RequestCondition;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The pending functional routes of one path pattern of a gateway route, declared with the routes
 * of any HTTP method of the route builder: {@code any(template)} for a template, and for a
 * prefix, e.g. {@code /api/**}, {@code any(prefix)} and {@code any(prefix + "/{+path}")}. Each
 * consumes and produces every media type, whatever its group declares, and sends the error
 * responses of the upstream as they are, see {@link HttpRouteSpec#keepErrorResponses()}. Their
 * settings are declared first, then {@link #handle(ProxyHandler)} adds them.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class GatewayRouteSpec {

    /**
     * The name of the variable of the rest of the path of a prefix pattern.
     */
    static final String PATH_VARIABLE = "path";

    /**
     * A variable named {@value #PATH_VARIABLE} in a template, e.g. {@code {path}} or {@code {+path:.*}}.
     */
    private static final Pattern PATH_VARIABLE_IN_TEMPLATE = Pattern.compile("\\{[+#./;?&]?" + PATH_VARIABLE + "(?![A-Za-z0-9_-])");

    private final List<HttpRouteSpec> specs;

    private GatewayRouteSpec(List<HttpRouteSpec> specs) {
        this.specs = specs;
    }

    /**
     * Declare the pending routes of a path pattern of a gateway route.
     *
     * @param routes  The scope to declare them in
     * @param pattern The path pattern, see {@link GatewayPathPattern}
     * @return The pending routes
     * @throws IllegalArgumentException if a prefix pattern has a variable named {@value #PATH_VARIABLE}
     */
    static GatewayRouteSpec of(HttpRouteScope routes, String pattern) {
        List<String> templates = templates(pattern);
        List<HttpRouteSpec> specs = new ArrayList<>(templates.size());
        for (String template : templates) {
            specs.add(any(routes, template));
        }
        return new GatewayRouteSpec(List.copyOf(specs));
    }

    /**
     * @param pattern The path pattern of a gateway route, see {@link GatewayPathPattern}
     * @return The templates of the routes it becomes
     * @throws IllegalArgumentException if a prefix pattern has a variable named {@value #PATH_VARIABLE}
     */
    static List<String> templates(String pattern) {
        GatewayPathPattern parsed = GatewayPathPattern.parse(pattern);
        if (!parsed.prefix()) {
            return List.of(parsed.template());
        }
        if (PATH_VARIABLE_IN_TEMPLATE.matcher(parsed.template()).find()) {
            throw new IllegalArgumentException("The prefix of the gateway path pattern " + pattern
                + " must not have a variable named '" + PATH_VARIABLE + "': it is the rest of the path");
        }
        String prefix = parsed.template();
        while (prefix.endsWith("/")) {
            prefix = prefix.substring(0, prefix.length() - 1);
        }
        // the prefix itself, and every path under it
        return List.of(prefix.isEmpty() ? "/" : prefix, prefix + "/{+" + PATH_VARIABLE + "}");
    }

    private static HttpRouteSpec any(HttpRouteScope routes, String template) {
        // the upstream decides the media types, and its error responses are relayed as they are
        return routes.any(template)
            .consumesAll()
            .produces(MediaType.ALL_TYPE)
            .keepErrorResponses();
    }

    GatewayRouteSpec where(RequestCondition condition) {
        for (HttpRouteSpec spec : specs) {
            spec.where(condition);
        }
        return this;
    }

    GatewayRouteSpec order(int order) {
        for (HttpRouteSpec spec : specs) {
            spec.order(order);
        }
        return this;
    }

    /**
     * Give each request of the routes its {@link GatewayRequestContext} before the route filters
     * run.
     *
     * @param route The gateway route
     */
    void context(GatewayRoute route) {
        for (HttpRouteSpec spec : specs) {
            spec.before(request -> GatewayRequestContext.of(request, route));
        }
    }

    GatewayRouteSpec annotate(AnnotationValue<?> annotation) {
        for (HttpRouteSpec spec : specs) {
            spec.annotate(annotation);
        }
        return this;
    }

    /**
     * Declare the route filters of a gateway filter on the routes.
     *
     * @param filter The gateway filter
     */
    void configure(GatewayFilter filter) {
        for (HttpRouteSpec spec : specs) {
            filter.configure(spec);
        }
    }

    /**
     * Add the routes.
     *
     * @param handler The handler that relays their requests
     */
    void handle(ProxyHandler handler) {
        for (HttpRouteSpec spec : specs) {
            spec.handleAsync(handler);
        }
    }

    /**
     * A built-in gateway filter that declares more than route filters on the routes, e.g. an
     * annotation.
     */
    interface Configurer {

        /**
         * Declare what the filter needs on the routes, before its route filters.
         *
         * @param route The routes
         */
        void configure(GatewayRouteSpec route);
    }
}
