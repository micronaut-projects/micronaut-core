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
import io.micronaut.web.router.builder.HandlerMethod;
import io.micronaut.web.router.builder.LocatorHandler;
import io.micronaut.web.router.builder.PathVariables;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * The target of a locator route, see
 * {@link io.micronaut.web.router.builder.HttpRouteBuilder#locate(String, LocatorHandler, Function)}.
 * The route matches its prefix followed by the rest of the path, for every standard HTTP method.
 * When the router selects it, the router runs the locator and matches the rest of the path with
 * the routes of the {@link RouteTable} of the located target, and answers the route of that table,
 * with the path variables of both and the target, instead of the locator route. The table may
 * have locator routes too, which locate again.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class RouteLocator {

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

    private final @Nullable LocatorHandler<?> locator;
    private final @Nullable AsyncLocatorHandler<?> asyncLocator;
    private final Function<Object, RouteTable> tables;

    /**
     * @param locator Locates the target
     * @param tables  The route table of a target
     * @param <T>     The type of the target
     */
    public <T> RouteLocator(LocatorHandler<? extends T> locator, Function<? super T, RouteTable> tables) {
        this.locator = Objects.requireNonNull(locator, "locator");
        this.asyncLocator = null;
        this.tables = tables(tables);
    }

    /**
     * @param locator Locates the target later
     * @param tables  The route table of a target
     * @param <T>     The type of the target
     * @since 5.3.0
     */
    public <T> RouteLocator(AsyncLocatorHandler<? extends T> locator, Function<? super T, RouteTable> tables) {
        this.locator = null;
        this.asyncLocator = Objects.requireNonNull(locator, "locator");
        this.tables = tables(tables);
    }

    /**
     * The route table function, applied to the targets of the locator only.
     */
    @SuppressWarnings("unchecked")
    private static <T> Function<Object, RouteTable> tables(Function<? super T, RouteTable> tables) {
        return (Function<Object, RouteTable>) Objects.requireNonNull(tables, "tables");
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

    /**
     * @param route A route
     * @return The locator of the route, if it is a locator route
     */
    static @Nullable RouteLocator of(UriRouteInfo<?, ?> route) {
        // a handler method only: the target of a bean method route is the bean, created when asked for
        if (route instanceof DefaultUrlRouteInfo<?, ?> defaultRoute && defaultRoute.getTargetMethod() instanceof HandlerMethod<?> handler
            && handler.getTarget() instanceof RouteLocator locator) {
            return locator;
        }
        return null;
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
        UriMatchInfo matchInfo = locatorMatch.matchInfo();
        Object remainderValue = matchInfo.getVariableValues().get(REMAINDER);
        String remainder = remainderValue == null ? "/" : "/" + remainderValue;

        // the variables of the prefixes of the locators that located this one, then of this prefix
        Map<String, Object> rawValues = new LinkedHashMap<>();
        List<UriMatchVariable> variables = new ArrayList<>();
        Object owner = null;
        if (request instanceof LocatedRequest<?> located) {
            rawValues.putAll(located.rawValues);
            variables.addAll(located.variables);
            owner = located.target;
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
        Map<String, Object> decoded = new LinkedHashMap<>(locatorMatch.getVariableValues());
        decoded.remove(REMAINDER);
        if (request instanceof LocatedRequest<?> located) {
            Map<String, Object> all = new LinkedHashMap<>(located.decodedValues);
            all.putAll(decoded);
            decoded = all;
        }
        HttpRequest<?> original = request instanceof LocatedRequest<?> located ? located.original : request;
        // the filters of the groups of the locator routes that located this one, then of this locator route
        List<GenericHttpFilter> filters = new ArrayList<>();
        if (request instanceof LocatedRequest<?> located) {
            filters.addAll(located.filters);
        }
        // the groups with error routes of this locator route, then of the locator routes that located it
        List<RouteAssembly.RouteGroup> errorScopes = new ArrayList<>(1);
        if (locatorMatch.getRouteInfo() instanceof DefaultUrlRouteInfo<?, ?> locatorRoute) {
            filters.addAll(locatorRoute.routeFilters);
            RouteAssembly.RouteGroup errorScope = locatorRoute.errorScope;
            if (errorScope != null) {
                errorScopes.add(errorScope);
            }
        }
        if (request instanceof LocatedRequest<?> located) {
            errorScopes.addAll(located.errorScopes);
        }
        Object target;
        DefaultPathVariables pathVariables = new DefaultPathVariables(decoded, locatorMatch.conversionService, owner);
        try {
            LocatorHandler<?> syncLocator = locator;
            target = syncLocator != null ? syncLocator.locate(original, pathVariables) : locateAsync(original, request.getPath(), pathVariables);
        } catch (Exception e) {
            // like a controller method: the error routes see the exception the locator threw
            return ExceptionUtils.sneakyThrow(e);
        }
        if (target == null) {
            return null;
        }
        RouteTable table = tables.apply(target);
        if (!(table instanceof DefaultRouteTable defaultTable)) {
            throw new IllegalStateException("No route table for the located target: " + target);
        }
        Argument<?> targetType = defaultTable.locatedTargetType();
        if (targetType != null && !targetType.getWrapperType().isInstance(target)) {
            // the handlers of the table receive the target as an instance of its type
            throw new IllegalStateException("The route table for located targets of type " + targetType.getTypeName()
                + " cannot route the located target " + target + " of type " + target.getClass().getName());
        }
        return new Located(defaultTable.routes(), new LocatedRequest<>(original, remainder, target, rawValues, decoded, variables,
            List.copyOf(filters), List.copyOf(errorScopes)), target);
    }

    /**
     * The target of the asynchronous locator, located once per request and level: the outcome
     * of the stage is kept in an attribute of the request. If the stage does not complete now,
     * the router cannot match the request yet, see {@link #pendingLocation(Throwable)}.
     *
     * @param original      The original request
     * @param levelPath     The path matched by the locator route, which tells the levels of nested locators apart
     * @param pathVariables The path variables of the locator
     * @return The target, or {@code null}
     * @throws Exception The error of the locator
     */
    private @Nullable Object locateAsync(HttpRequest<?> original, String levelPath, PathVariables pathVariables) throws Exception {
        Map<LocationKey, Outcome> outcomes = outcomes(original);
        LocationKey key = new LocationKey(this, levelPath);
        Outcome outcome = outcomes.get(key);
        if (outcome == null) {
            CompletionStage<?> stage = Objects.requireNonNull(asyncLocator, "asyncLocator").locate(original, pathVariables);
            if (stage == null) {
                throw new NullPointerException("The locator returned no stage: " + this);
            }
            CompletionStage<Boolean> located = stage.handle((value, error) -> {
                outcomes.put(key, new Outcome(value, error instanceof CompletionException && error.getCause() != null ? error.getCause() : error, null));
                return Boolean.TRUE;
            });
            // unless the stage completed already: the locator is not called again until it does
            outcomes.putIfAbsent(key, new Outcome(null, null, located));
            outcome = Objects.requireNonNull(outcomes.get(key));
        }
        CompletionStage<Boolean> pending = outcome.pending();
        if (pending != null) {
            // not located yet: the router matches the request again when it is
            throw new PendingLocation(pending);
        }
        Throwable error = outcome.error();
        if (error != null) {
            return ExceptionUtils.sneakyThrow(error);
        }
        return outcome.target();
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
     * The outcome of an asynchronous locator.
     *
     * @param target  The target, or {@code null}
     * @param error   The error, or {@code null}
     * @param pending The stage that completes when the target is located, or {@code null} if it is
     */
    private record Outcome(@Nullable Object target, @Nullable Throwable error, @Nullable CompletionStage<Boolean> pending) {
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
            Map<String, Object> values = new LinkedHashMap<>(request.rawValues);
            values.putAll(inner.getVariableValues());
            List<UriMatchVariable> variables = new ArrayList<>(request.variables);
            variables.addAll(inner.getVariables());
            LocatedUriMatchInfo info = new LocatedUriMatchInfo(request.original.getPath(), values, variables, target, request.filters, request.errorScopes);
            return (UriRouteMatch<T, R>) route.locatedMatch(info);
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
     * The request with the rest of the path, matched with the router of a located target.
     *
     * @param <B> The body type
     */
    static final class LocatedRequest<B> extends HttpRequestWrapper<B> {
        private final HttpRequest<B> original;
        private final String path;
        private final Object target;
        private final Map<String, Object> rawValues;
        private final Map<String, Object> decodedValues;
        private final List<UriMatchVariable> variables;
        private final List<GenericHttpFilter> filters;
        private final List<RouteAssembly.RouteGroup> errorScopes;
        private @Nullable URI uri;

        @SuppressWarnings("ParameterNumber")
        LocatedRequest(HttpRequest<B> original, String path, Object target, Map<String, Object> rawValues,
                       Map<String, Object> decodedValues, List<UriMatchVariable> variables, List<GenericHttpFilter> filters,
                       List<RouteAssembly.RouteGroup> errorScopes) {
            super(original);
            this.original = original;
            this.path = path;
            this.target = target;
            this.rawValues = rawValues;
            this.decodedValues = decodedValues;
            this.variables = variables;
            this.filters = filters;
            this.errorScopes = errorScopes;
        }

        @Override
        public String getPath() {
            return path;
        }

        @Override
        public URI getUri() {
            URI result = uri;
            if (result == null) {
                String query = original.getUri().getRawQuery();
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
    static final class LocatedUriMatchInfo implements UriMatchInfo {
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
        Object target() {
            return target;
        }

        /**
         * @return The filters of the groups of the locator routes that located the route, which run
         * before the filters of the route
         */
        List<GenericHttpFilter> filters() {
            return filters;
        }

        /**
         * @return The groups with error or status routes of the locator routes that located the
         * route, the closest locator first
         */
        List<RouteAssembly.RouteGroup> errorScopes() {
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
