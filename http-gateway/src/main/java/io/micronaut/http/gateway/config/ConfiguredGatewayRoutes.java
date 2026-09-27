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
package io.micronaut.http.gateway.config;

import io.micronaut.context.BeanLocator;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.HttpVersion;
import io.micronaut.http.client.HttpVersionSelection;
import io.micronaut.http.gateway.GatewayConfiguration;
import io.micronaut.http.gateway.GatewayDefaultFilters;
import io.micronaut.http.gateway.GatewayFilter;
import io.micronaut.http.gateway.GatewayRoute;
import io.micronaut.http.gateway.GatewayRouteBuilder;
import io.micronaut.http.gateway.GatewayRoutes;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The gateway routes of the configuration, {@code micronaut.gateway.routes}, and the default
 * filters, {@code micronaut.gateway.default-filters}. An unknown predicate or filter, an invalid
 * argument or URI fails the startup with the id of the route.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
@Singleton
final class ConfiguredGatewayRoutes implements GatewayRoutes, GatewayDefaultFilters {

    private final List<GatewayRouteConfiguration> routes;
    private final GatewayConfiguration configuration;
    private final BeanLocator beanLocator;
    private final ConversionService conversionService;

    ConfiguredGatewayRoutes(List<GatewayRouteConfiguration> routes, GatewayConfiguration configuration,
                            BeanLocator beanLocator, ConversionService conversionService) {
        this.routes = routes;
        this.configuration = configuration;
        this.beanLocator = beanLocator;
        this.conversionService = conversionService;
    }

    @Override
    public List<GatewayFilter> filters() {
        List<GatewayFilter> filters = new ArrayList<>();
        for (String filter : configuration.getDefaultFilters()) {
            filters.add(filter("the default filters", filter));
        }
        return filters;
    }

    @Override
    public void routes(GatewayRouteBuilder gateway) {
        for (GatewayRouteConfiguration route : routes) {
            if (route.isEnabled()) {
                gateway.route(toRoute(route));
            }
        }
    }

    /**
     * @param route The configuration of a route
     * @return The route
     */
    GatewayRoute toRoute(GatewayRouteConfiguration route) {
        String owner = "the gateway route " + route.getId();
        String uri = route.getUri();
        if (uri == null || uri.isBlank()) {
            throw new ConfigurationException("The gateway route " + route.getId() + " has no uri");
        }
        GatewayRoute.Builder builder = GatewayRoute.builder(route.getId())
            .order(route.getOrder())
            .responseTimeout(route.getResponseTimeout())
            .activityTimeout(route.getActivityTimeout());
        try {
            builder.uri(uri);
        } catch (IllegalArgumentException e) {
            throw new ConfigurationException("The uri of " + owner + " is not a URI: " + uri, e);
        }
        HttpVersion httpVersion = route.getHttpVersion();
        if (httpVersion != null) {
            builder.httpVersion(HttpVersionSelection.forLegacyVersion(httpVersion));
        }
        route.getMetadata().forEach(builder::metadata);
        for (Object predicate : route.getPredicates()) {
            Entry entry = entry(owner, predicate);
            GatewayPredicateFactory factory = beanLocator.findBean(GatewayPredicateFactory.class, Qualifiers.byName(entry.name()))
                .orElseThrow(() -> new ConfigurationException("Unknown predicate " + entry.name() + " of " + owner));
            try {
                factory.apply(builder, arguments(owner, entry, factory.shortcutFields()));
            } catch (IllegalArgumentException e) {
                throw new ConfigurationException("Invalid predicate " + entry.name() + " of " + owner + ": " + e.getMessage(), e);
            }
        }
        for (Object filter : route.getFilters()) {
            builder.filters(filter(owner, filter));
        }
        try {
            return builder.build();
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ConfigurationException("Invalid " + owner + ": " + e.getMessage(), e);
        }
    }

    private GatewayFilter filter(String owner, Object filter) {
        Entry entry = entry(owner, filter);
        GatewayFilterFactory factory = beanLocator.findBean(GatewayFilterFactory.class, Qualifiers.byName(entry.name()))
            .orElseThrow(() -> new ConfigurationException("Unknown filter " + entry.name() + " of " + owner));
        try {
            return factory.create(arguments(owner, entry, factory.shortcutFields()));
        } catch (IllegalArgumentException e) {
            throw new ConfigurationException("Invalid filter " + entry.name() + " of " + owner + ": " + e.getMessage(), e);
        }
    }

    private GatewayArguments arguments(String owner, Entry entry, List<String> fields) {
        String of = entry.name() + " of " + owner;
        if (entry.shortcut() != null) {
            return GatewayArguments.ofShortcut(of, fields, entry.shortcut(), conversionService);
        }
        return new GatewayArguments(of, entry.args(), conversionService);
    }

    /**
     * @param owner The owner, for the messages
     * @param value A predicate or a filter: a shortcut string or a map
     * @return Its name and arguments
     */
    private static Entry entry(String owner, Object value) {
        if (value instanceof CharSequence text) {
            String s = text.toString().strip();
            int equals = s.indexOf('=');
            String name = (equals < 0 ? s : s.substring(0, equals)).strip();
            if (name.isEmpty()) {
                throw new ConfigurationException("A predicate or filter of " + owner + " has no name: " + s);
            }
            return new Entry(name, equals < 0 ? "" : s.substring(equals + 1), Map.of());
        }
        if (value instanceof Map<?, ?> map) {
            Object name = map.get("name");
            if (name == null) {
                throw new ConfigurationException("A predicate or filter of " + owner + " has no name: " + map);
            }
            Map<String, Object> args = new LinkedHashMap<>();
            Object rawArgs = map.get("args");
            if (rawArgs instanceof Map<?, ?> argMap) {
                argMap.forEach((k, v) -> args.put(String.valueOf(k), v));
            } else if (rawArgs != null) {
                throw new ConfigurationException("The args of " + name + " of " + owner + " are not a map: " + rawArgs);
            }
            return new Entry(name.toString().strip(), null, args);
        }
        throw new ConfigurationException("A predicate or filter of " + owner + " is neither a string nor a map: " + value);
    }

    /**
     * A predicate or a filter of the configuration.
     *
     * @param name     The name
     * @param shortcut The arguments of the shortcut syntax, or {@code null}
     * @param args     The arguments by name
     */
    private record Entry(String name, @org.jspecify.annotations.Nullable String shortcut, Map<String, Object> args) {
    }
}
