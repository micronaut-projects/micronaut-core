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

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.util.StringUtils;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import io.micronaut.web.router.builder.RequestCondition;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Map;
import java.util.Objects;

/**
 * Turns the gateway routes of the {@link GatewayRoutes} beans into functional routes.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
@Singleton
@Requires(property = GatewayConfiguration.PREFIX + ".enabled", notEquals = StringUtils.FALSE, defaultValue = StringUtils.TRUE)
@Requires(beans = ProxyHandlerFactory.class)
final class GatewayRouteRegistrar implements HttpRoutes, GatewayRouteRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(GatewayRouteRegistrar.class);

    private final List<GatewayRoutes> sources;
    private final ProxyHandlerFactory factory;
    @Nullable
    private final GatewayDefaultFilters defaultFilters;
    private final AtomicReference<List<ProxyHandler>> handlers = new AtomicReference<>(List.of());

    GatewayRouteRegistrar(List<GatewayRoutes> sources, ProxyHandlerFactory factory, @Nullable GatewayDefaultFilters defaultFilters) {
        List<GatewayRoutes> ordered = new ArrayList<>(sources);
        OrderUtil.sort(ordered);
        this.sources = ordered;
        this.factory = factory;
        this.defaultFilters = defaultFilters;
    }

    @Override
    public List<GatewayRoute> getRoutes() {
        List<ProxyHandler> current = Objects.requireNonNull(handlers.get());
        List<GatewayRoute> routes = new ArrayList<>(current.size());
        for (ProxyHandler handler : current) {
            routes.add(handler.getRoute());
        }
        return routes;
    }

    @Override
    public void routes(HttpRouteBuilder routes) {
        Map<String, GatewayRoute> declared = new LinkedHashMap<>();
        Builder builder = new Builder(declared);
        for (GatewayRoutes source : sources) {
            source.routes(builder);
            builder.flush();
        }
        List<GatewayFilter> defaults = defaultFilters == null ? List.of() : defaultFilters.filters();
        List<ProxyHandler> created = new ArrayList<>(declared.size());
        for (GatewayRoute route : declared.values()) {
            GatewayRoute withDefaults = route;
            if (!defaults.isEmpty()) {
                List<GatewayFilter> filters = new ArrayList<>(defaults);
                filters.addAll(route.getFilters());
                GatewayRoute.Builder b = route.toBuilder().replaceFilters(filters);
                // the retries and the circuit breaker of the route win over the default ones
                if (route.getRetry() != null) {
                    b.retry(route.getRetry());
                }
                if (route.getCircuitBreaker() != null) {
                    b.circuitBreaker(route.getCircuitBreaker());
                }
                withDefaults = b.build();
            }
            ProxyHandler handler = factory.create(withDefaults);
            register(routes, handler);
            created.add(handler);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Gateway route {} {} -> {}", withDefaults.getId(), withDefaults.getPaths(), withDefaults.getUri());
            }
        }
        handlers.set(Collections.unmodifiableList(created));
    }

    private static void register(HttpRouteBuilder routes, ProxyHandler handler) {
        GatewayRoute route = handler.getRoute();
        List<String> paths = route.getPaths().isEmpty() ? List.of("/**") : route.getPaths();
        for (String path : paths) {
            GatewayRouteSpec spec = GatewayRouteSpec.of(routes, path);
            for (RequestCondition condition : route.getConditions()) {
                spec.where(condition);
            }
            spec.order(route.getOrder());
            for (GatewayFilter filter : route.getFilters()) {
                spec.configure(filter);
            }
            spec.handle(handler);
        }
    }

    /**
     * The builder of the routes of one {@link GatewayRoutes} bean.
     */
    private static final class Builder implements GatewayRouteBuilder {
        private final Map<String, GatewayRoute> declared;
        private final List<GatewayRoute.Builder> pending = new ArrayList<>();

        Builder(Map<String, GatewayRoute> declared) {
            this.declared = declared;
        }

        @Override
        public GatewayRoute.Builder route(String id) {
            GatewayRoute.Builder builder = GatewayRoute.builder(id);
            pending.add(builder);
            return builder;
        }

        @Override
        public GatewayRouteBuilder route(GatewayRoute route) {
            add(Objects.requireNonNull(route, "route"));
            return this;
        }

        void flush() {
            for (GatewayRoute.Builder builder : pending) {
                add(builder.build());
            }
            pending.clear();
        }

        private void add(GatewayRoute route) {
            if (declared.putIfAbsent(route.getId(), route) != null) {
                throw new IllegalStateException("Two gateway routes have the id " + route.getId());
            }
        }
    }
}
