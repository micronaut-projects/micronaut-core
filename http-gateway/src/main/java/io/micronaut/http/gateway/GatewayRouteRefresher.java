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

import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.runtime.context.scope.refresh.RefreshEvent;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.TaskScheduler;
import io.micronaut.web.router.RouteAssembly;
import io.micronaut.web.router.RouteConditionContext;
import io.micronaut.web.router.builder.DefaultLocatedHttpRouteBuilder;
import io.micronaut.web.router.builder.LocatedHttpRouteBuilder;
import io.micronaut.web.router.builder.LocatedRoutes;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Loads the routes of the {@link GatewayRouteLocator} beans, and replaces them all at once on a
 * refresh: a refresh that fails keeps the last routes. A request is routed by the routes it was
 * matched with, also when a refresh replaces them while it runs.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
@Singleton
final class GatewayRouteRefresher implements ApplicationEventListener<RefreshEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(GatewayRouteRefresher.class);
    private static final Duration LOCATOR_TIMEOUT = Duration.ofMinutes(1);
    /**
     * How long the handlers of replaced routes are kept, for the requests they relay.
     */
    private static final Duration RETIRE_DELAY = Duration.ofMinutes(1);

    private final List<GatewayRouteLocator> locators;
    private final ProxyHandlerFactory factory;
    @Nullable
    private final GatewayDefaultFilters defaultFilters;
    private final Map<String, GatewayWeightGroupConfiguration> weightGroups;
    private final AtomicLong generations = new AtomicLong();
    /**
     * The refresh in flight, if any: a periodic refresh does not start another while a locator is
     * slow, so that the loads do not pile up.
     */
    private final AtomicReference<@Nullable CompletableFuture<Integer>> inFlight = new AtomicReference<>();
    private final AtomicReference<Snapshot> current = new AtomicReference<>(new Snapshot(List.of(), Map.of(), RouteConditionContext.fallback(), 0));
    @Nullable
    private final ScheduledFuture<?> schedule;
    private final TaskScheduler scheduler;

    GatewayRouteRefresher(List<GatewayRouteLocator> locators,
                          ProxyHandlerFactory factory,
                          @Nullable GatewayDefaultFilters defaultFilters,
                          List<GatewayWeightGroupConfiguration> weightGroups,
                          GatewayConfiguration configuration,
                          @Named(TaskExecutors.SCHEDULED) TaskScheduler scheduler) {
        this.locators = List.copyOf(locators);
        this.factory = factory;
        this.defaultFilters = defaultFilters;
        this.weightGroups = GatewayRouteRegistrar.byName(weightGroups);
        this.scheduler = scheduler;
        Duration interval = configuration.getRefreshInterval();
        this.schedule = locators.isEmpty() || interval == null ? null
            : scheduler.scheduleWithFixedDelay(interval, interval, this::scheduledRefresh);
    }

    /**
     * @return Whether there is any route locator
     */
    boolean hasLocators() {
        return !locators.isEmpty();
    }

    /**
     * @return The routes of the last refresh
     */
    Snapshot current() {
        return Objects.requireNonNull(current.get());
    }

    /**
     * @return The routes of the locators, as last refreshed
     */
    List<GatewayRoute> getRoutes() {
        List<GatewayRoute> routes = new ArrayList<>();
        for (ProxyHandler handler : current().handlers()) {
            routes.add(handler.getRoute());
        }
        return routes;
    }

    /**
     * Load the routes of the locators and replace the current ones, unless a locator fails or a
     * route is invalid: then the current routes stay.
     *
     * @return The stage of the number of routes, failed if the refresh failed
     */
    CompletionStage<Integer> refresh() {
        if (locators.isEmpty()) {
            return CompletableFuture.completedFuture(0);
        }
        long generation = generations.incrementAndGet();
        List<CompletableFuture<List<GatewayRoute>>> loads = new ArrayList<>(locators.size());
        for (GatewayRouteLocator locator : locators) {
            CompletableFuture<List<GatewayRoute>> load;
            try {
                // a locator that never answers fails the refresh instead of holding it
                load = locator.getRoutes().toCompletableFuture().orTimeout(LOCATOR_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            } catch (RuntimeException e) {
                load = CompletableFuture.failedFuture(e);
            }
            loads.add(load);
        }
        return CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new)).thenApply(ignored -> {
            List<GatewayRoute> routes = new ArrayList<>();
            for (CompletableFuture<List<GatewayRoute>> load : loads) {
                routes.addAll(load.join());
            }
            List<GatewayFilter> defaults = defaultFilters == null ? List.of() : defaultFilters.filters();
            Snapshot next = new Snapshot(GatewayRouteRegistrar.handlers(routes, factory, defaults), weightGroups, factory.routeConditions(), generation);
            // the routes are declared before the snapshot is published: a route that cannot be
            // declared fails the refresh, and the last routes stay, instead of every request to
            // the routes of the snapshot failing
            next.check();
            // a slower, older refresh does not replace a newer one
            Snapshot previous = current.getAndAccumulate(next, (last, candidate) -> last.generation() < candidate.generation() ? candidate : last);
            if (previous.generation() < next.generation()) {
                // the requests in flight finish on the handlers they started with: their clients
                // are given back later
                retire(previous.handlers());
            } else {
                retire(next.handlers());
            }
            if (LOG.isDebugEnabled()) {
                LOG.debug("Refreshed {} gateway routes of the route locators", routes.size());
            }
            return routes.size();
        }).whenComplete((count, failure) -> {
            if (failure != null) {
                LOG.warn("The refresh of the gateway routes failed, the last routes stay: {}", failure.toString());
            }
        });
    }

    /**
     * The periodic refresh: skipped while the previous refresh still runs.
     */
    void scheduledRefresh() {
        CompletableFuture<Integer> running = inFlight.get();
        if (running != null && !running.isDone()) {
            LOG.debug("The previous refresh of the gateway routes still runs: this one is skipped");
            return;
        }
        CompletableFuture<Integer> next = refresh().toCompletableFuture();
        inFlight.set(next);
    }

    /**
     * Close the handlers of routes that were replaced, once the requests they relay had the time
     * to finish, see {@link #RETIRE_DELAY}.
     *
     * @param handlers The handlers
     */
    private void retire(List<ProxyHandler> handlers) {
        if (handlers.isEmpty()) {
            return;
        }
        scheduler.schedule(RETIRE_DELAY, () -> {
            for (ProxyHandler handler : handlers) {
                ((DefaultProxyHandler) handler).close();
            }
        });
    }

    @Override
    public void onApplicationEvent(RefreshEvent event) {
        refresh();
    }

    /**
     * Stop the refreshes.
     */
    @PreDestroy
    void close() {
        if (schedule != null) {
            schedule.cancel(false);
        }
    }

    /**
     * The routes of one refresh, and their functional routes, declared the first time a request
     * is routed with them.
     */
    static final class Snapshot implements LocatedRoutes<Snapshot> {
        private final List<ProxyHandler> handlers;
        private final Map<String, GatewayWeightGroupConfiguration> weightGroups;
        private final RouteConditionContext conditions;
        private final long generation;
        private final List<GatewayPathPattern> patterns = new ArrayList<>();
        private final boolean anyPath;

        /**
         * @param handlers     The handlers of the routes
         * @param weightGroups The settings of the weight groups
         * @param conditions   What the server resolves for the conditions of the routes
         * @param generation   The number of the refresh
         */
        Snapshot(List<ProxyHandler> handlers, Map<String, GatewayWeightGroupConfiguration> weightGroups, RouteConditionContext conditions,
                 long generation) {
            this.handlers = List.copyOf(handlers);
            this.weightGroups = weightGroups;
            this.conditions = conditions;
            this.generation = generation;
            boolean any = false;
            for (ProxyHandler handler : handlers) {
                List<String> paths = handler.getRoute().getPaths();
                if (paths.isEmpty()) {
                    any = true;
                }
                for (String path : paths) {
                    patterns.add(GatewayPathPattern.parse(path));
                }
            }
            this.anyPath = any;
        }

        List<ProxyHandler> handlers() {
            return handlers;
        }

        long generation() {
            return generation;
        }

        @Override
        public Argument<Snapshot> targetType() {
            return Argument.of(Snapshot.class);
        }

        @Override
        public void routes(LocatedHttpRouteBuilder<Snapshot> routes) {
            GatewayRouteRegistrar.register(routes, handlers, weightGroups, conditions);
        }

        /**
         * Declare the routes of the snapshot on a builder of their own, as the router declares
         * them when a request is first routed with the snapshot.
         *
         * @throws RuntimeException if a route cannot be declared, e.g. a prefix with a variable
         *                          named {@code path}
         */
        void check() {
            RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, uri -> uri);
            DefaultLocatedHttpRouteBuilder<Snapshot> builder = new DefaultLocatedHttpRouteBuilder<>(assembly, targetType(), Snapshot.class);
            try {
                routes(builder);
            } catch (RuntimeException e) {
                builder.discard();
                throw e;
            }
            builder.close();
        }

        /**
         * @return The routes, to locate
         */
        Snapshot routes() {
            return this;
        }

        /**
         * @param rawPath The raw path of a request
         * @return Whether a route of this refresh has a path that matches it
         */
        boolean matchesPath(String rawPath) {
            if (anyPath) {
                return true;
            }
            for (GatewayPathPattern pattern : patterns) {
                if (pattern.rawVariables(rawPath) != null) {
                    return true;
                }
            }
            return false;
        }
    }
}
