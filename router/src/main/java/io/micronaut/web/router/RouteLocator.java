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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ExceptionUtils;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpRequestWrapper;
import io.micronaut.http.filter.GenericHttpFilter;
import io.micronaut.http.uri.UriMatchInfo;
import io.micronaut.http.uri.UriMatchVariable;
import io.micronaut.web.router.builder.AsyncLocatorHandler;
import io.micronaut.web.router.builder.DefaultPathVariables;
import io.micronaut.web.router.builder.LocatedRoutes;
import io.micronaut.web.router.builder.LocatorHandler;
import io.micronaut.http.PathVariables;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The target of a locator route, see
 * {@link io.micronaut.web.router.builder.HttpRouteBuilder#locate(String, LocatorHandler, Function)}.
 * The route matches its prefix followed by the rest of the path, for every standard HTTP method.
 * When the router selects it, the router runs the locator and matches the rest of the path with
 * the routes of the {@link RouteTable} of the {@link LocatedRoutes} of the located target, and
 * answers the route of that table, with the path variables of both and the target, instead of the
 * locator route. The table may have locator routes too, which locate again. The table of a
 * {@link LocatedRoutes} is built by the {@link RouteTableFactory} of the routes, on the first
 * target it routes.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class RouteLocator implements DynamicRouteTarget {

    /**
     * The name of the variable of the rest of the path.
     */
    static final String REMAINDER = "micronaut_located_path";

    /**
     * Appended to the prefix: the rest of the path after a slash.
     */
    private static final String TEMPLATE_SUFFIX = "/{+" + REMAINDER + "}";

    /**
     * The request attribute of the targets that asynchronous locators located for the request.
     */
    private static final String LOCATED_ATTRIBUTE = "micronaut.router.located";
    private static final String LOCATOR = "locator";

    private final @Nullable LocatorHandler<?> locator;
    private final @Nullable AsyncLocatorHandler<?> asyncLocator;
    private final Function<Object, ? extends LocatedRoutes<?>> routesOf;
    private final RouteTableFactory tables;

    /**
     * @param locator  Locates the target
     * @param routesOf The routes of a target
     * @param tables   Builds and keeps the tables of the routes
     * @param <T>      The type of the target
     */
    public <T> RouteLocator(LocatorHandler<? extends T> locator, Function<? super T, ? extends LocatedRoutes<?>> routesOf,
                            RouteTableFactory tables) {
        this.locator = Objects.requireNonNull(locator, LOCATOR);
        this.asyncLocator = null;
        this.routesOf = routesOf(routesOf);
        this.tables = Objects.requireNonNull(tables, "tables");
    }

    /**
     * @param locator  Locates the target later
     * @param routesOf The routes of a target
     * @param tables   Builds and keeps the tables of the routes
     * @param <T>      The type of the target
     */
    public <T> RouteLocator(AsyncLocatorHandler<? extends T> locator, Function<? super T, ? extends LocatedRoutes<?>> routesOf,
                            RouteTableFactory tables) {
        this.locator = null;
        this.asyncLocator = Objects.requireNonNull(locator, LOCATOR);
        this.routesOf = routesOf(routesOf);
        this.tables = Objects.requireNonNull(tables, "tables");
    }

    /**
     * The routes function, applied to the targets of the locator only.
     */
    @SuppressWarnings("unchecked")
    private static <T> Function<Object, ? extends LocatedRoutes<?>> routesOf(Function<? super T, ? extends LocatedRoutes<?>> routesOf) {
        return (Function<Object, ? extends LocatedRoutes<?>>) Objects.requireNonNull(routesOf, "routesOf");
    }

    /**
     * The stage that an asynchronous locator is waiting for, when the router could not match a
     * request because of it: match the request again when the stage completes.
     *
     * @param error An error of the router
     * @return The stage, completed with a non-null value when the target is located, or
     * {@code null} if the error is not an asynchronous locator that waits
     */
    public static @Nullable CompletionStage<?> pendingLocation(Throwable error) {
        return error instanceof PendingLocation pending ? pending.stage : null;
    }

    /**
     * The URI templates of a locator route: the prefix alone, and the prefix followed by a slash
     * and the rest of the path. The literal slash keeps a variable at the end of the prefix a
     * whole segment.
     *
     * @param prefix The prefix
     * @return The templates
     */
    public static String[] templates(String prefix) {
        String normalized = prefix;
        while (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.isEmpty() || normalized.equals("/")) {
            // the root: every path
            return new String[]{"/", TEMPLATE_SUFFIX};
        }
        return new String[]{normalized, normalized + TEMPLATE_SUFFIX};
    }

    /**
     * Never invoked: the router resolves a locator route to a route of the located target.
     *
     * @return Never returns
     */
    public Object handle() {
        throw new IllegalStateException("The router resolves a locator route to a route of the located target");
    }

    @Override
    public <T, R> @Nullable UriRouteMatch<T, R> findClosest(HttpRequest<?> request, UriRouteMatch<T, R> match) {
        // the rest of the path is matched with the routes of the located target
        Located located = locate(request, match);
        if (located == null) {
            return null;
        }
        UriRouteMatch<T, R> target = located.routes().findClosest(located.request(), null);
        return target == null ? null : located.wrap(target);
    }

    @Override
    public <T, R> List<UriRouteMatch<T, R>> findAllClosest(HttpRequest<?> request, UriRouteMatch<T, R> match,
                                                           @Nullable Predicate<UriRouteMatch<T, R>> filter) {
        Located located = locate(request, match);
        if (located == null) {
            return List.of();
        }
        List<UriRouteMatch<T, R>> targetMatches = filter == null
            ? located.routes().findAllClosest(located.request(), null, null)
            // the filter sees the match of the request, as it does for the other routes
            : located.routes().findAllClosest(located.request(), targetMatch -> filter.test(located.wrap(targetMatch)), null);
        return located.wrap(targetMatches);
    }

    @Override
    public <T, R> List<UriRouteMatch<T, R>> findAny(HttpRequest<?> request, UriRouteMatch<T, R> match) {
        Located located;
        try {
            located = locate(request, match);
        } catch (RuntimeException e) {
            if (pendingLocation(e) == null) {
                throw e;
            }
            // an asynchronous locator that has not located its target: no route of it is known
            return List.of();
        }
        if (located == null) {
            return List.of();
        }
        return located.wrap(located.routes().<T, R>findAny(located.request(), null));
    }

    /**
     * Run the locator of a matched locator route.
     *
     * @param request The request
     * @param match   The match of the locator route
     * @return The located target and the request to match the rest of the path with, or {@code null} if no target was located
     */
    @Nullable Located locate(HttpRequest<?> request, UriRouteMatch<?, ?> match) {
        if (!(match instanceof DefaultUriRouteMatch<?, ?> locatorMatch)) {
            return null;
        }
        Location location = Location.of(request, locatorMatch);
        Object target;
        DefaultPathVariables pathVariables = new DefaultPathVariables(location.decodedValues(), locatorMatch.conversionService, location.owner());
        try {
            target = locator != null
                ? locateSync(location, request.getPath(), pathVariables)
                : locateAsync(location, request.getPath(), pathVariables);
        } catch (Exception e) {
            // like a controller method: the error routes see the exception the locator threw
            return ExceptionUtils.sneakyThrow(e);
        }
        if (target == null) {
            return null;
        }
        LocatedRoutes<?> routes = routesOf.apply(target);
        if (routes == null) {
            throw new IllegalStateException("No located routes for the located target: " + target);
        }
        DefaultRouteTable defaultTable = (DefaultRouteTable) tables.table(routes);
        Argument<?> targetType = defaultTable.locatedTargetType();
        if (!targetType.getWrapperType().isInstance(target)) {
            // the handlers of the table receive the target as an instance of its type
            throw new IllegalStateException("The located routes for targets of type " + targetType.getTypeName()
                + " cannot route the located target " + target + " of type " + target.getClass().getName());
        }
        return new Located(defaultTable.routes(), new LocatedRequest<>(location, target), target);
    }

    /**
     * The error scopes of the locator whose location failed with an error for the request, see
     * {@link GroupErrorRoutes}: the request matched no route, and the groups of the locator routes
     * answer the error, as they answer the errors of the routes the locators locate.
     *
     * @param request The request
     * @param error   The error
     * @return The groups with error or status routes of the locator routes, the closest first, or
     * an empty list if no locator of the request failed with the error
     */
    static List<RouteAssembly.RouteGroup> failedLocationScopes(HttpRequest<?> request, Throwable error) {
        Map<LocationKey, Outcome> outcomes = existingOutcomes(request);
        if (outcomes == null) {
            return List.of();
        }
        for (Outcome outcome : outcomes.values()) {
            Location location = outcome.location();
            if (location != null && failedWith(outcome, error)) {
                return location.errorScopes();
            }
        }
        return List.of();
    }

    /**
     * @param outcome The outcome of a locator
     * @param error   An error, the error of the locator or an error that wraps it
     * @return Whether the locator failed with the error
     */
    private static boolean failedWith(Outcome outcome, Throwable error) {
        Throwable failure = outcome.error();
        if (failure == null) {
            return false;
        }
        for (Throwable e = error; e != null; e = e.getCause() == e ? null : e.getCause()) {
            if (e == failure) {
                return true;
            }
        }
        return false;
    }

    /**
     * The target of the synchronous locator, located once per request and level: the outcome of
     * the locator is kept in an attribute of the request, so that matching the request again,
     * e.g. to find the allowed methods of a {@code 405}, does not run the locator again.
     *
     * @param location      What the locator route matched
     * @param levelPath     The path matched by the locator route, which tells the levels of nested locators apart
     * @param pathVariables The path variables of the locator
     * @return The target, or {@code null}
     * @throws Exception The error of the locator
     */
    private @Nullable Object locateSync(Location location, String levelPath, PathVariables pathVariables) throws Exception {
        HttpRequest<?> original = location.original();
        Map<LocationKey, Outcome> outcomes = outcomes(original);
        LocationKey key = new LocationKey(this, levelPath);
        Outcome outcome = outcomes.get(key);
        if (outcome == null) {
            try {
                outcome = new Outcome(Objects.requireNonNull(locator, LOCATOR).locate(original, pathVariables), null, null, null, location);
            } catch (Exception e) {
                outcome = new Outcome(null, e, null, null, location);
            }
            outcomes.put(key, outcome);
        }
        return outcome.located();
    }

    /**
     * The target of the asynchronous locator, located once per request and level: the outcome
     * of the stage is kept in an attribute of the request. If the stage does not complete now,
     * the router cannot match the request yet, see {@link #pendingLocation(Throwable)}.
     *
     * @param location      What the locator route matched
     * @param levelPath     The path matched by the locator route, which tells the levels of nested locators apart
     * @param pathVariables The path variables of the locator
     * @return The target, or {@code null}
     * @throws Exception The error of the locator
     */
    private @Nullable Object locateAsync(Location location, String levelPath, PathVariables pathVariables) throws Exception {
        HttpRequest<?> original = location.original();
        Map<LocationKey, Outcome> outcomes = outcomes(original);
        LocationKey key = new LocationKey(this, levelPath);
        Outcome outcome = outcomes.get(key);
        if (outcome == null) {
            CompletionStage<?> stage = Objects.requireNonNull(asyncLocator, "asyncLocator").locate(original, pathVariables);
            if (stage == null) {
                throw new NullPointerException("The locator returned no stage: " + this);
            }
            // completed when the outcome is known: by the stage, or when the location is cancelled
            CompletableFuture<Boolean> located = new CompletableFuture<>();
            Outcome pending = new Outcome(null, null, located, stage, location);
            // the locator is not called again until the stage completes
            outcomes.put(key, pending);
            stage.whenComplete((value, error) -> {
                outcomes.replace(key, pending, new Outcome(value, error instanceof CompletionException && error.getCause() != null ? error.getCause() : error, null, null, location));
                located.complete(Boolean.TRUE);
            });
            // the stage may have completed already
            outcome = Objects.requireNonNull(outcomes.get(key));
        }
        CompletionStage<Boolean> pending = outcome.pending();
        if (pending != null) {
            // not located yet: the router matches the request again when it is
            throw new PendingLocation(pending);
        }
        return outcome.located();
    }

    /**
     * Whether an asynchronous locator has not located its target for the request yet: until it
     * has, the router does not know the routes of the target, e.g. for
     * {@link Router#findAny(HttpRequest)}. Matching the request with
     * {@link Router#findClosest(HttpRequest)} waits for it, see {@link #pendingLocation(Throwable)}.
     *
     * @param request The request
     * @return Whether a locator of the request is still locating its target
     * @since 5.3.0
     */
    public static boolean isLocating(HttpRequest<?> request) {
        Map<LocationKey, Outcome> outcomes = existingOutcomes(request);
        if (outcomes == null) {
            return false;
        }
        for (Outcome outcome : outcomes.values()) {
            if (outcome.pending() != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Cancel the asynchronous locators that have not located their targets for the request yet,
     * e.g. when the client went away: the stage each of them returned is cancelled, and matching
     * the request fails with a {@link CancellationException} instead of waiting for the target.
     *
     * @param request The request
     * @since 5.3.0
     */
    public static void cancelPendingLocations(HttpRequest<?> request) {
        Map<LocationKey, Outcome> outcomes = existingOutcomes(request);
        if (outcomes == null) {
            return;
        }
        for (Map.Entry<LocationKey, Outcome> entry : outcomes.entrySet()) {
            Outcome outcome = entry.getValue();
            CompletableFuture<Boolean> pending = outcome.pending();
            if (pending == null
                || !outcomes.replace(entry.getKey(), outcome, new Outcome(null, new CancellationException("The route locator was cancelled"), null, null, outcome.location()))) {
                continue;
            }
            CompletionStage<?> stage = outcome.stage();
            if (stage != null) {
                try {
                    stage.toCompletableFuture().cancel(false);
                } catch (UnsupportedOperationException e) {
                    // a stage that cannot be cancelled: its outcome is ignored
                }
            }
            pending.complete(Boolean.TRUE);
        }
    }

    @SuppressWarnings("unchecked")
    private static @Nullable Map<LocationKey, Outcome> existingOutcomes(HttpRequest<?> request) {
        Object attribute = request.getAttribute(LOCATED_ATTRIBUTE).orElse(null);
        return attribute instanceof Map<?, ?> map ? (Map<LocationKey, Outcome>) map : null;
    }

    @SuppressWarnings("unchecked")
    private static Map<LocationKey, Outcome> outcomes(HttpRequest<?> request) {
        Object attribute = request.getAttribute(LOCATED_ATTRIBUTE).orElse(null);
        if (attribute instanceof Map<?, ?> map) {
            return (Map<LocationKey, Outcome>) map;
        }
        Map<LocationKey, Outcome> outcomes = new ConcurrentHashMap<>(2);
        request.setAttribute(LOCATED_ATTRIBUTE, outcomes);
        return outcomes;
    }

    @Override
    public String toString() {
        return "RouteLocator[" + (locator != null ? locator : asyncLocator) + ']';
    }

    /**
     * An asynchronous locator of a level of the located path.
     *
     * @param locator   The locator
     * @param levelPath The path the locator route matched
     */
    private record LocationKey(RouteLocator locator, String levelPath) {
    }

    /**
     * The outcome of a locator.
     *
     * @param target  The target, or {@code null}
     * @param error   The error, or {@code null}
     * @param pending Completes when the target is located, or {@code null} if it is
     * @param stage    The stage the asynchronous locator returned, while it is pending
     * @param location What the locator route matched, whose error scopes answer the error
     */
    private record Outcome(@Nullable Object target, @Nullable Throwable error, @Nullable CompletableFuture<Boolean> pending,
                           @Nullable CompletionStage<?> stage, @Nullable Location location) {

        /**
         * @return The target, or {@code null}
         * @throws Exception The error of the locator
         */
        @Nullable Object located() throws Exception {
            Throwable failure = error;
            if (failure != null) {
                return ExceptionUtils.sneakyThrow(failure);
            }
            return target;
        }
    }

    /**
     * The router could not match the request: an asynchronous locator waits for its stage.
     */
    private static final class PendingLocation extends RuntimeException {
        private final transient CompletionStage<?> stage;

        PendingLocation(CompletionStage<?> stage) {
            super("The route locator has not located its target yet", null, false, false);
            this.stage = stage;
        }
    }

    /**
     * A target that was located.
     *
     * @param routes  The routes of the target's table
     * @param request The request to match the rest of the path with
     * @param target  The target
     */
    record Located(UriRouteSet routes, LocatedRequest<?> request, Object target) {

        /**
         * The match of a route of the target's table, with the path variables of the prefixes and
         * the target.
         *
         * @param match The match of the rest of the path
         * @param <T>   The target type
         * @param <R>   The return type
         * @return The match of the request
         */
        @SuppressWarnings("unchecked")
        <T, R> UriRouteMatch<T, R> wrap(UriRouteMatch<T, R> match) {
            if (!(match instanceof DefaultUriRouteMatch<T, R> innerMatch) || !(match.getRouteInfo() instanceof DefaultUrlRouteInfo<?, ?> route)) {
                return match;
            }
            UriMatchInfo inner = innerMatch.matchInfo();
            if (inner instanceof LocatedUriMatchInfo) {
                // located again by a locator of the target's table: it has the variables of every prefix
                return match;
            }
            Location location = request.location();
            Map<String, Object> values = location.withPrefixValues(inner.getVariableValues());
            List<UriMatchVariable> variables = new ArrayList<>(location.variables());
            variables.addAll(inner.getVariables());
            LocatedUriMatchInfo info = new LocatedUriMatchInfo(location.original().getPath(), values, variables, target,
                location.filters(), location.errorScopes());
            return (UriRouteMatch<T, R>) route.resolvedMatch(info);
        }

        /**
         * @param matches The matches of the rest of the path
         * @param <T>     The target type
         * @param <R>     The return type
         * @return The matches of the request
         */
        <T, R> List<UriRouteMatch<T, R>> wrap(List<UriRouteMatch<T, R>> matches) {
            List<UriRouteMatch<T, R>> result = new ArrayList<>(matches.size());
            for (UriRouteMatch<T, R> match : matches) {
                result.add(wrap(match));
            }
            return result;
        }
    }

    /**
     * What a locator route matched, created before its locator runs: the rest of the path, and
     * what the routes it locates inherit from the locator routes, its own and those that located
     * it. The routes of the target are matched and answered with it, and the failures of the
     * locator are answered with its error scopes.
     *
     * @param original      The original request
     * @param remainder     The rest of the path
     * @param owner         The target of the locator route that located this one, or {@code null}
     * @param rawValues     The raw values of the variables of the prefixes, outer prefix first
     * @param decodedValues The decoded values of the variables of the prefixes
     * @param variables     The variables of the prefixes
     * @param filters       The filters of the groups of the locator routes, outer first
     * @param errorScopes   The groups with error or status routes of the locator routes, the closest first
     */
    record Location(HttpRequest<?> original, String remainder, @Nullable Object owner, Map<String, Object> rawValues,
                    Map<String, Object> decodedValues, List<UriMatchVariable> variables, List<GenericHttpFilter> filters,
                    List<RouteAssembly.RouteGroup> errorScopes) {

        /**
         * @param request      The request, a {@link LocatedRequest} for a locator route of a located table
         * @param locatorMatch The match of the locator route
         * @return The location
         */
        static Location of(HttpRequest<?> request, DefaultUriRouteMatch<?, ?> locatorMatch) {
            LocatedRequest<?> parent = request instanceof LocatedRequest<?> located ? located : null;
            UriMatchInfo matchInfo = locatorMatch.matchInfo();
            Object remainderValue = matchInfo.getVariableValues().get(REMAINDER);
            String remainder = remainderValue == null ? "/" : "/" + remainderValue;

            // the variables of the prefixes of the locators that located this one, then of this prefix
            Map<String, Object> rawValues = new LinkedHashMap<>();
            List<UriMatchVariable> variables = new ArrayList<>();
            Map<String, Object> decoded = new LinkedHashMap<>();
            // the filters of the groups of the locator routes that located this one, then of this locator route
            List<GenericHttpFilter> filters = new ArrayList<>();
            if (parent != null) {
                Location outer = parent.location;
                rawValues.putAll(outer.rawValues);
                variables.addAll(outer.variables);
                decoded.putAll(outer.decodedValues);
                filters.addAll(outer.filters);
            }
            matchInfo.getVariableValues().forEach((name, value) -> {
                if (!REMAINDER.equals(name)) {
                    rawValues.put(name, value);
                }
            });
            for (UriMatchVariable variable : matchInfo.getVariables()) {
                if (!REMAINDER.equals(variable.getName())) {
                    variables.add(variable);
                }
            }
            locatorMatch.getVariableValues().forEach((name, value) -> {
                if (!REMAINDER.equals(name)) {
                    decoded.put(name, value);
                }
            });
            // the groups with error routes of this locator route, then of the locator routes that located it
            List<RouteAssembly.RouteGroup> errorScopes = new ArrayList<>(1);
            if (locatorMatch.getRouteInfo() instanceof DefaultUrlRouteInfo<?, ?> locatorRoute) {
                filters.addAll(locatorRoute.routeFilters);
                RouteAssembly.RouteGroup errorScope = locatorRoute.errorScope;
                if (errorScope != null) {
                    errorScopes.add(errorScope);
                }
            }
            if (parent != null) {
                errorScopes.addAll(parent.location.errorScopes);
                return new Location(parent.location.original, remainder, parent.target, rawValues, decoded, variables,
                    List.copyOf(filters), List.copyOf(errorScopes));
            }
            return new Location(request, remainder, null, rawValues, decoded, variables, List.copyOf(filters), List.copyOf(errorScopes));
        }

        /**
         * The variables of a match of the rest of the path, with those of the prefixes: the
         * variables the handler of a located route gets, and its constraints see.
         *
         * @param values The raw values of the variables of the match of the rest of the path
         * @return The raw values of the variables of the prefixes, then of the match
         */
        Map<String, Object> withPrefixValues(Map<String, Object> values) {
            Map<String, Object> all = new LinkedHashMap<>(rawValues);
            all.putAll(values);
            return all;
        }
    }

    /**
     * The request with the rest of the path, matched with the router of a located target.
     *
     * @param <B> The body type
     */
    static final class LocatedRequest<B> extends HttpRequestWrapper<B> {
        private final Location location;
        private final Object target;
        private @Nullable URI uri;

        @SuppressWarnings("unchecked")
        LocatedRequest(Location location, Object target) {
            super((HttpRequest<B>) location.original());
            this.location = location;
            this.target = target;
        }

        /**
         * @return What the locator route matched
         */
        Location location() {
            return location;
        }

        @Override
        public String getPath() {
            return location.remainder();
        }

        @Override
        public URI getUri() {
            URI result = uri;
            if (result == null) {
                String path = location.remainder();
                String query = location.original().getUri().getRawQuery();
                result = URI.create(query == null ? path : path + '?' + query);
                uri = result;
            }
            return result;
        }
    }

    /**
     * The match of a located route: the path variables of the prefixes and of the route, and the
     * target.
     */
    static final class LocatedUriMatchInfo implements ResolvedMatchInfo {
        private final String uri;
        private final Map<String, Object> values;
        private final List<UriMatchVariable> variables;
        private final Map<String, UriMatchVariable> variableMap;
        private final Object target;
        private final List<GenericHttpFilter> filters;
        private final List<RouteAssembly.RouteGroup> errorScopes;

        LocatedUriMatchInfo(String uri, Map<String, Object> values, List<UriMatchVariable> variables, Object target,
                            List<GenericHttpFilter> filters, List<RouteAssembly.RouteGroup> errorScopes) {
            this.uri = uri;
            this.values = values;
            this.variables = variables;
            this.target = target;
            this.filters = filters;
            this.errorScopes = errorScopes;
            this.variableMap = LinkedHashMap.newLinkedHashMap(variables.size());
            for (UriMatchVariable variable : variables) {
                variableMap.put(variable.getName(), variable);
            }
        }

        /**
         * @return The located target
         */
        @Override
        public Object target() {
            return target;
        }

        /**
         * @return The filters of the groups of the locator routes that located the route, which run
         * before the filters of the route
         */
        @Override
        public List<GenericHttpFilter> filters() {
            return filters;
        }

        /**
         * @return The groups with error or status routes of the locator routes that located the
         * route, the closest locator first
         */
        @Override
        public List<RouteAssembly.RouteGroup> errorScopes() {
            return errorScopes;
        }

        @Override
        public String getUri() {
            return uri;
        }

        @Override
        public Map<String, Object> getVariableValues() {
            return values;
        }

        @Override
        public List<UriMatchVariable> getVariables() {
            return variables;
        }

        @Override
        public Map<String, UriMatchVariable> getVariableMap() {
            return variableMap;
        }
    }
}
