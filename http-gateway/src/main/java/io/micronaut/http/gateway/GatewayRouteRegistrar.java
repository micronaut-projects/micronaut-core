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
import io.micronaut.web.router.RouteConditionContext;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRouteScope;
import io.micronaut.web.router.builder.HttpRoutes;
import io.micronaut.web.router.builder.RequestCondition;
import jakarta.inject.Singleton;
import io.micronaut.context.annotation.Property;
import org.jspecify.annotations.Nullable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

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
    private final Map<String, GatewayWeightGroupConfiguration> weightGroups;
    private final GatewayRouteRefresher refresher;
    @Nullable
    private final InMemoryGatewayRouteRepository repository;
    @Nullable
    private final String contextPath;

    GatewayRouteRegistrar(List<GatewayRoutes> sources, ProxyHandlerFactory factory, @Nullable GatewayDefaultFilters defaultFilters,
                          List<GatewayWeightGroupConfiguration> weightGroups, GatewayRouteRefresher refresher,
                          @Nullable InMemoryGatewayRouteRepository repository,
                          @Nullable @Property(name = "micronaut.server.context-path") String contextPath) {
        List<GatewayRoutes> ordered = new ArrayList<>(sources);
        OrderUtil.sort(ordered);
        this.sources = ordered;
        this.factory = factory;
        this.defaultFilters = defaultFilters;
        this.weightGroups = byName(weightGroups);
        this.refresher = refresher;
        this.repository = repository;
        this.contextPath = contextPath;
    }

    @Override
    public CompletionStage<Integer> refresh() {
        return refresher.refresh();
    }

    @Override
    public CompletionStage<Boolean> deleteRoute(String id) {
        InMemoryGatewayRouteRepository repo = repository;
        GatewayRoute removed = repo == null ? null : repo.remove(id);
        if (repo == null || removed == null) {
            return CompletableFuture.completedFuture(false);
        }
        // deleted once the routes without it are published: a failed refresh keeps the route
        // live, so it goes back to the repository and the delete can be tried again
        return refresher.refresh().handle((count, failure) -> {
            if (failure != null) {
                repo.restore(removed);
                throw failure instanceof CompletionException ce ? ce : new CompletionException(failure);
            }
            return true;
        });
    }

    /**
     * @param path The path of a request
     * @return The path under the context path of the server, which the paths of the routes of
     * the locators do not have
     */
    private String withoutContextPath(String path) {
        String context = contextPath;
        if (context == null || context.isEmpty() || context.equals("/")) {
            return path;
        }
        String prefix = context.endsWith("/") ? context.substring(0, context.length() - 1) : context;
        if (path.startsWith(prefix) && (path.length() == prefix.length() || path.charAt(prefix.length()) == '/')) {
            String rest = path.substring(prefix.length());
            return rest.isEmpty() ? "/" : rest;
        }
        return path;
    }

    /**
     * @param weightGroups The settings of the weight groups
     * @return The settings by the name of their group
     */
    static Map<String, GatewayWeightGroupConfiguration> byName(List<GatewayWeightGroupConfiguration> weightGroups) {
        Map<String, GatewayWeightGroupConfiguration> byName = new LinkedHashMap<>();
        for (GatewayWeightGroupConfiguration group : weightGroups) {
            byName.put(group.getGroup(), group);
        }
        return byName;
    }

    @Override
    public List<GatewayRoute> getRoutes() {
        List<ProxyHandler> current = Objects.requireNonNull(handlers.get());
        List<GatewayRoute> routes = new ArrayList<>(current.size());
        for (ProxyHandler handler : current) {
            routes.add(handler.getRoute());
        }
        // then the routes of the route locators, as last refreshed
        routes.addAll(refresher.getRoutes());
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
        List<ProxyHandler> created = handlers(declared.values(), factory, defaults);
        register(routes, created, weightGroups, factory.routeConditions());
        handlers.set(Collections.unmodifiableList(created));
        if (refresher.hasLocators()) {
            // the routes of the locators, which a refresh replaces: after the other routes, and
            // only for a path one of them has, so that the other routes are not shadowed
            routes.group(group -> {
                group.order(Integer.MAX_VALUE)
                    .where(request -> refresher.current().matchesPath(withoutContextPath(request.getPath())));
                group.locate("/", (request, pathVariables) -> refresher.current(), GatewayRouteRefresher.Snapshot::routes);
            });
            refresher.refresh();
        }
    }

    /**
     * @param declared The gateway routes
     * @param factory  The factory of the handlers
     * @param defaults The default filters, applied before the filters of each route
     * @return The handlers of the routes
     */
    static List<ProxyHandler> handlers(Collection<GatewayRoute> declared, ProxyHandlerFactory factory, List<GatewayFilter> defaults) {
        List<ProxyHandler> created = new ArrayList<>(declared.size());
        for (GatewayRoute route : declared) {
            created.add(factory.create(withDefaults(route, defaults)));
        }
        return created;
    }

    /**
     * Declare the handlers of gateway routes as functional routes.
     *
     * @param routes       The route builder
     * @param handlers     The handlers of the routes
     * @param weightGroups The settings of the weight groups
     * @param conditions   What the server resolves for the conditions of the routes
     */
    static void register(HttpRouteScope routes, List<ProxyHandler> handlers, Map<String, GatewayWeightGroupConfiguration> weightGroups,
                         RouteConditionContext conditions) {
        List<GatewayRoute> declared = new ArrayList<>(handlers.size());
        for (ProxyHandler handler : handlers) {
            declared.add(handler.getRoute());
        }
        // the groups of the weighted routes, from all the routes: each route matches when the group chooses it
        Map<String, WeightGroup> groups = WeightGroup.of(declared, weightGroups, conditions);
        for (ProxyHandler handler : handlers) {
            GatewayRoute route = handler.getRoute();
            register(routes, handler, route.getWeight() == null ? null : groups.get(route.getWeight().group()));
            if (LOG.isDebugEnabled()) {
                LOG.debug("Gateway route {} {} -> {}", route.getId(), route.getPaths(), RelayHeaders.redact(route.getUri()));
            }
        }
    }

    private static GatewayRoute withDefaults(GatewayRoute route, List<GatewayFilter> defaults) {
        if (defaults.isEmpty()) {
            return route;
        }
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
        return b.build();
    }

    private static void register(HttpRouteScope routes, ProxyHandler handler, @Nullable WeightGroup group) {
        GatewayRoute route = handler.getRoute();
        List<String> paths = route.getPaths().isEmpty() ? List.of("/**") : route.getPaths();
        for (String path : paths) {
            GatewayRouteSpec spec = GatewayRouteSpec.of(routes, path);
            for (RequestCondition condition : route.getConditions()) {
                spec.where(condition);
            }
            if (group != null) {
                String id = route.getId();
                spec.where(RequestCondition.custom(request -> id.equals(group.chosen(request))));
            }
            spec.order(route.getOrder());
            // the route filters of the gateway filters read the route of the request
            spec.context(route);
            for (GatewayFilter filter : route.getFilters()) {
                if (filter instanceof GatewayRouteSpec.Configurer configurer) {
                    configurer.configure(spec);
                }
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
