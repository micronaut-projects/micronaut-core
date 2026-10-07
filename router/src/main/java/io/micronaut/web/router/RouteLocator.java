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
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ExceptionUtils;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpRequestWrapper;
import io.micronaut.http.filter.GenericHttpFilter;
import io.micronaut.http.uri.UriMatchInfo;
import io.micronaut.http.uri.UriMatchVariable;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.web.router.builder.AsyncLocatorHandler;
import io.micronaut.web.router.builder.DefaultPathVariables;
import io.micronaut.web.router.builder.LocatedRoutes;
import io.micronaut.web.router.builder.LocatorHandler;
import io.micronaut.web.router.builder.RouteSettings;
import io.micronaut.http.PathVariables;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
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
    private static final String LOCATOR_NAME = "locator";

    private final @Nullable LocatorHandler<?> locator;
    private final @Nullable AsyncLocatorHandler<?> asyncLocator;
    private final Function<Object, ? extends LocatedRoutes<?>> routesOf;
    private final RouteTableFactory tables;
    /**
     * The media types and the executor of the groups of the locator routes, which the located
     * routes inherit, or {@code null} outside a group.
     */
    private final @Nullable RouteSettings groupSettings;

    /**
     * @param locator  Locates the target
     * @param routesOf The routes of a target
     * @param tables   Builds and keeps the tables of the routes
     * @param <T>      The type of the target
     */
    public <T> RouteLocator(LocatorHandler<? extends T> locator, Function<? super T, ? extends LocatedRoutes<?>> routesOf,
                            RouteTableFactory tables) {
        this(locator, routesOf, tables, null);
    }

    /**
     * @param locator       Locates the target
     * @param routesOf      The routes of a target
     * @param tables        Builds and keeps the tables of the routes
     * @param groupSettings The media types and the executor of the groups of the locator routes,
     *                      set when the groups are closed, which the located routes inherit, or {@code null}
     * @param <T>           The type of the target
     */
    public <T> RouteLocator(LocatorHandler<? extends T> locator, Function<? super T, ? extends LocatedRoutes<?>> routesOf,
                            RouteTableFactory tables, @Nullable RouteSettings groupSettings) {
        this.locator = Objects.requireNonNull(locator, LOCATOR_NAME);
        this.asyncLocator = null;
        this.routesOf = routesOf(routesOf);
        this.tables = Objects.requireNonNull(tables, "tables");
        this.groupSettings = groupSettings;
    }

    /**
     * @param locator  Locates the target later
     * @param routesOf The routes of a target
     * @param tables   Builds and keeps the tables of the routes
     * @param <T>      The type of the target
     */
    public <T> RouteLocator(AsyncLocatorHandler<? extends T> locator, Function<? super T, ? extends LocatedRoutes<?>> routesOf,
                            RouteTableFactory tables) {
        this(locator, routesOf, tables, null);
    }

    /**
     * @param locator       Locates the target later
     * @param routesOf      The routes of a target
     * @param tables        Builds and keeps the tables of the routes
     * @param groupSettings The media types and the executor of the groups of the locator routes,
     *                      set when the groups are closed, which the located routes inherit, or {@code null}
     * @param <T>           The type of the target
     */
    public <T> RouteLocator(AsyncLocatorHandler<? extends T> locator, Function<? super T, ? extends LocatedRoutes<?>> routesOf,
                            RouteTableFactory tables, @Nullable RouteSettings groupSettings) {
        this.locator = null;
        this.asyncLocator = Objects.requireNonNull(locator, LOCATOR_NAME);
        this.routesOf = routesOf(routesOf);
        this.tables = Objects.requireNonNull(tables, "tables");
        this.groupSettings = groupSettings;
    }

    /**
     * @return The media types and the executor of the groups of the locator routes, or {@code null}
     */
    @Nullable RouteSettings groupSettings() {
        return groupSettings;
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
     * The URI template of the routes of a match, including the prefixes of the locator routes
     * that located a route of a located target, e.g. {@code /shops/{shop}/items/{item}} for the
     * located route {@code /items/{item}} under the prefix {@code /shops/{shop}}: the template of
     * the request, e.g. for the metrics and the traces, see
     * {@link io.micronaut.http.BasicHttpAttributes#getUriTemplate(HttpRequest)}.
     *
     * @param match A match
     * @return The URI template of its route, under the prefixes of the locator routes for a located route
     * @since 5.3.0
     */
    public static String uriTemplate(UriRouteMatch<?, ?> match) {
        String template = match.getRouteInfo().getUriMatchTemplate().toString();
        if (match instanceof DefaultUriRouteMatch<?, ?> defaultMatch && defaultMatch.matchInfo() instanceof LocatedUriMatchInfo located) {
            return joinTemplates(located.prefixTemplate, template);
        }
        return template;
    }

    /**
     * @param locatorTemplate The URI template of a locator route
     * @return The template of its prefix: without the rest of the path, empty for the root
     */
    private static String prefixTemplate(String locatorTemplate) {
        return locatorTemplate.endsWith(TEMPLATE_SUFFIX)
            ? locatorTemplate.substring(0, locatorTemplate.length() - TEMPLATE_SUFFIX.length())
            : locatorTemplate;
    }

    /**
     * @param prefix   The template of a prefix, empty for the root
     * @param template The template of a route under the prefix
     * @return The template of the route under the prefix
     */
    private static String joinTemplates(String prefix, String template) {
        if (prefix.isEmpty() || prefix.equals("/")) {
            return template;
        }
        if (template.isEmpty() || template.equals("/")) {
            // a route at the prefix itself
            return prefix;
        }
        return template.charAt(0) == '/' && prefix.charAt(prefix.length() - 1) == '/'
            ? prefix + template.substring(1)
            : prefix + template;
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
        UriRouteMatch<T, R> target;
        try {
            target = located.routes().findClosest(located.request(), null);
        } catch (RuntimeException e) {
            throw located.failed(e);
        }
        return target == null ? null : located.wrap(target);
    }

    @Override
    public <T, R> List<UriRouteMatch<T, R>> findAllClosest(HttpRequest<?> request, UriRouteMatch<T, R> match,
                                                           @Nullable Predicate<UriRouteMatch<T, R>> filter) {
        Located located = locate(request, match);
        if (located == null) {
            return List.of();
        }
        List<UriRouteMatch<T, R>> targetMatches;
        try {
            targetMatches = filter == null
                ? located.routes().findAllClosest(located.request(), null, null)
                // the filter sees the match of the request, as it does for the other routes
                : located.routes().findAllClosest(located.request(), targetMatch -> filter.test(located.wrap(targetMatch)), null);
        } catch (RuntimeException e) {
            throw located.failed(e);
        }
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
        List<UriRouteMatch<T, R>> targetMatches;
        try {
            targetMatches = located.routes().findAny(located.request(), null);
        } catch (RuntimeException e) {
            if (pendingLocation(e) == null) {
                throw located.failed(e);
            }
            // a nested asynchronous locator that has not located its target
            return List.of();
        }
        return located.wrap(targetMatches);
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
        LocationKey key = new LocationKey(this, request.getPath());
        Object target;
        DefaultPathVariables pathVariables = new DefaultPathVariables(location.decodedValues(), locatorMatch.conversionService, location.owner());
        try {
            target = locator != null
                ? locateSync(location, key, pathVariables)
                : locateAsync(location, key, pathVariables);
        } catch (Exception e) {
            // like a controller method: the error routes see the exception the locator threw
            return ExceptionUtils.sneakyThrow(e);
        }
        if (target == null) {
            return null;
        }
        UriRouteSet routes;
        try {
            routes = routesOf(target);
        } catch (RuntimeException e) {
            // answered by the error scopes of the location, like the failure of the locator
            throw failed(location, key, e);
        }
        return new Located(routes, new LocatedRequest<>(location, target), target, key);
    }

    /**
     * @param target The located target
     * @return The routes of the table of the target
     */
    private UriRouteSet routesOf(Object target) {
        LocatedRoutes<?> routes = routesOf.apply(target);
        if (routes == null) {
            throw new IllegalStateException("No located routes for the located target: " + target);
        }
        DefaultRouteTable defaultTable = (DefaultRouteTable) tables.table(routes);
        Argument<?> targetType = defaultTable.locatedTargetType();
        if (!targetType.getWrapperType().isInstance(target)) {
            // the handlers of the table receive the target as an instance of its type
            throw new IllegalStateException("The located routes for targets of type " + targetType.getTypeName()
                + " cannot route the located target " + target + " of type " + target.getClass().getName()
                + (target instanceof CompletionStage<?> && locator != null
                ? ": a locator that returns a CompletionStage of the target is declared with locateAsync" : ""));
        }
        return defaultTable.routes();
    }

    /**
     * Record that a location failed after its locator located the target: selecting the table of
     * the target, or matching the rest of the path with it. The location fails with the error for
     * the rest of the request, and the error is answered by its error and status scopes, unless a
     * location nested in it already failed with the error.
     *
     * @param location The location
     * @param key      The key of the outcome of the location
     * @param error    The error
     * @return The error, to throw
     */
    private static RuntimeException failed(Location location, LocationKey key, RuntimeException error) {
        if (pendingLocation(error) != null) {
            return error;
        }
        Map<LocationKey, Outcome> outcomes = outcomes(location.original());
        for (Outcome outcome : outcomes.values()) {
            if (failedWith(outcome, error)) {
                // the closest location that failed answers the error
                return error;
            }
        }
        outcomes.put(key, new Outcome(null, error, null, null, location));
        return error;
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
            if (location != null && failedWith(outcome, error) && !(outcome.error() instanceof LocationAbandoned)) {
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
     * @param key           The locator and the path its route matched, which tells the levels of nested locators apart
     * @param pathVariables The path variables of the locator
     * @return The target, or {@code null}
     * @throws Exception The error of the locator
     */
    private @Nullable Object locateSync(Location location, LocationKey key, PathVariables pathVariables) throws Exception {
        HttpRequest<?> original = location.original();
        Map<LocationKey, Outcome> outcomes = outcomes(original);
        Outcome outcome = outcomes.get(key);
        if (outcome == null) {
            try {
                outcome = new Outcome(Objects.requireNonNull(locator, LOCATOR_NAME).locate(original, pathVariables), null, null, null, location);
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
     * @param key           The locator and the path its route matched, which tells the levels of nested locators apart
     * @param pathVariables The path variables of the locator
     * @return The target, or {@code null}
     * @throws Exception The error of the locator
     */
    private @Nullable Object locateAsync(Location location, LocationKey key, PathVariables pathVariables) throws Exception {
        HttpRequest<?> original = location.original();
        Map<LocationKey, Outcome> outcomes = outcomes(original);
        Outcome outcome = outcomes.get(key);
        if (outcome == null) {
            CompletionStage<?> stage;
            try {
                stage = Optional.ofNullable((CompletionStage<?>) Objects.requireNonNull(asyncLocator, "asyncLocator").locate(original, pathVariables))
                    .orElseThrow(() -> new NullPointerException("The locator returned no stage: " + this));
            } catch (Exception e) {
                // failed before returning a stage: an outcome, like a stage that failed
                outcome = new Outcome(null, e, null, null, location);
                outcomes.put(key, outcome);
                return outcome.located();
            }
            // completed when the outcome is known: by the stage, or when the request is abandoned
            CompletableFuture<Boolean> located = new CompletableFuture<>();
            Completion completion = new Completion(key);
            Outcome pending = new Outcome(null, null, located, completion, location);
            completion.waitFor(outcomes, pending);
            // the locator is not called again until the stage completes
            outcomes.put(key, pending);
            stage.whenComplete(completion);
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
     * Completes when the asynchronous locators that have not located their targets for the
     * request yet have, e.g. for a filter that needs the routes of the request before they are
     * matched: the routes of a located target are known once its locator located it. Finding the
     * routes again may start the locators of the located routes, which may be pending again.
     *
     * @param request The request
     * @return A stage that completes with a non-null value when the pending locators completed, or
     * {@code null} if none is pending
     * @since 5.3.0
     */
    public static @Nullable CompletionStage<?> whenLocated(HttpRequest<?> request) {
        Map<LocationKey, Outcome> outcomes = existingOutcomes(request);
        if (outcomes == null) {
            return null;
        }
        List<CompletableFuture<Boolean>> pending = new ArrayList<>(1);
        for (Outcome outcome : outcomes.values()) {
            CompletableFuture<Boolean> located = outcome.pending();
            if (located != null) {
                pending.add(located);
            }
        }
        return switch (pending.size()) {
            case 0 -> null;
            case 1 -> pending.getFirst();
            // with a value, like each of them
            default -> CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new)).thenApply(ignored -> Boolean.TRUE);
        };
    }

    /**
     * Stop waiting for the asynchronous locators that have not located their targets for the
     * request yet, e.g. when the client went away: matching the request fails instead of waiting
     * for a target no one receives a response for, and the stages of the locators no longer
     * reference the request. The stages are not cancelled: a locator may return a stage it
     * shares with other requests, e.g. of a cache, which they keep waiting for.
     *
     * @param request The request
     * @since 5.3.0
     */
    public static void abandonPendingLocations(HttpRequest<?> request) {
        Map<LocationKey, Outcome> outcomes = existingOutcomes(request);
        if (outcomes == null) {
            return;
        }
        for (Map.Entry<LocationKey, Outcome> entry : outcomes.entrySet()) {
            Outcome outcome = entry.getValue();
            CompletableFuture<Boolean> pending = outcome.pending();
            if (pending == null
                || !outcomes.replace(entry.getKey(), outcome, new Outcome(null, new LocationAbandoned(), null, null, outcome.location()))) {
                continue;
            }
            Completion completion = outcome.completion();
            if (completion != null) {
                completion.release();
            }
            pending.complete(Boolean.TRUE);
        }
    }

    /**
     * @param error An error
     * @return Whether it is the error of a request abandoned before its route locators located
     * their targets, see {@link #abandonPendingLocations(HttpRequest)}: no one receives its response
     * @since 5.3.0
     */
    public static boolean isAbandonment(Throwable error) {
        return error instanceof LocationAbandoned;
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
     * @param inherited The annotations a route inherits
     * @param own       The annotations of the route, which override the inherited ones
     * @return The annotations of both
     */
    static AnnotationMetadata layered(AnnotationMetadata inherited, AnnotationMetadata own) {
        if (inherited.isEmpty()) {
            return own;
        }
        if (own.isEmpty()) {
            return inherited;
        }
        return new AnnotationMetadataHierarchy(true, inherited, own);
    }

    /**
     * The route the media type checks of a request see: for a request a locator route located,
     * the route with the settings it inherits at the location, so that, e.g., what a group of the
     * locator route produces selects a located route like a route declared in that group.
     *
     * @param request The request
     * @param route   A route of the table the request is matched with
     * @return The route at the location of the request, or the route for any other request
     */
    static UriRouteInfo<Object, Object> atLocation(HttpRequest<?> request, UriRouteInfo<Object, Object> route) {
        if (request instanceof LocatedRequest<?> located && route instanceof DefaultUrlRouteInfo<Object, Object> info) {
            return info.inheriting(located.location.inheritance);
        }
        return route;
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
     * @param pending    Completes when the target is located, or {@code null} if it is
     * @param completion Completes the outcome when the stage of the asynchronous locator completes, while it is pending
     * @param location   What the locator route matched, whose error scopes answer the error
     */
    private record Outcome(@Nullable Object target, @Nullable Throwable error, @Nullable CompletableFuture<Boolean> pending,
                           @Nullable Completion completion, @Nullable Location location) {

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
     * The request was abandoned before its asynchronous locator located the target, see
     * {@link #abandonPendingLocations(HttpRequest)}: an expected outcome, without a stack trace.
     */
    private static final class LocationAbandoned extends RuntimeException {
        LocationAbandoned() {
            super("The request was abandoned before its route locator located the target", null, false, false);
        }
    }

    /**
     * Records the outcome of the stage of an asynchronous locator. The stage references it until
     * it completes, and it references the request until it is released: a request abandoned
     * while the stage is pending is not kept by the stage.
     */
    private static final class Completion implements BiConsumer<Object, Throwable> {
        private final LocationKey key;
        /**
         * The outcome the stage completes, until it does or the request is abandoned.
         */
        private final AtomicReference<@Nullable Waiting> waiting = new AtomicReference<>();

        /**
         * @param key The key of the outcome
         */
        Completion(LocationKey key) {
            this.key = key;
        }

        /**
         * @param outcomes The outcomes of the locators of the request
         * @param pending  The pending outcome the stage completes
         */
        void waitFor(Map<LocationKey, Outcome> outcomes, Outcome pending) {
            waiting.set(new Waiting(outcomes, pending));
        }

        @Override
        public void accept(@Nullable Object value, @Nullable Throwable error) {
            Waiting current = waiting.getAndSet(null);
            if (current == null) {
                // the request was abandoned
                return;
            }
            Outcome pending = current.pending();
            Throwable failure = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
            current.outcomes().replace(key, pending, new Outcome(value, failure, null, null, pending.location()));
            Objects.requireNonNull(pending.pending()).complete(Boolean.TRUE);
        }

        /**
         * Stop referencing the request.
         */
        void release() {
            waiting.set(null);
        }
    }

    /**
     * The pending outcome of a request that the stage of its asynchronous locator completes.
     *
     * @param outcomes The outcomes of the locators of the request
     * @param pending  The pending outcome
     */
    private record Waiting(Map<LocationKey, Outcome> outcomes, Outcome pending) {
    }

    /**
     * A target that was located.
     *
     * @param routes  The routes of the target's table
     * @param request The request to match the rest of the path with
     * @param target  The target
     * @param key     The key of the outcome of the location
     */
    record Located(UriRouteSet routes, LocatedRequest<?> request, Object target, LocationKey key) {

        /**
         * Record that matching the rest of the path with the routes of the target failed.
         *
         * @param error The error
         * @return The error, to throw
         */
        RuntimeException failed(RuntimeException error) {
            return RouteLocator.failed(request.location(), key, error);
        }

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
            // the route with the settings of the location, built once per location like the route of a group
            DefaultUrlRouteInfo<?, ?> located = route.inheriting(location.inheritance());
            LocatedUriMatchInfo info = new LocatedUriMatchInfo(location.original().getPath(), values, variables, target,
                location.filters(), location.errorScopes(),
                located == route ? location.inheritance().annotationMetadata() : AnnotationMetadata.EMPTY_METADATA, location.prefixTemplate());
            return (UriRouteMatch<T, R>) located.resolvedMatch(info);
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
     * @param inheritance   What the located routes inherit from the groups of the locator routes, outer locator first
     * @param prefixTemplate The URI template of the prefixes, empty for the root
     */
    record Location(HttpRequest<?> original, String remainder, @Nullable Object owner, Map<String, Object> rawValues,
                    Map<String, Object> decodedValues, List<UriMatchVariable> variables, List<GenericHttpFilter> filters,
                    List<RouteAssembly.RouteGroup> errorScopes, LocationInheritance inheritance, String prefixTemplate) {

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
            // what the located routes inherit from the locator routes that located this one, overridden by this locator route
            LocationInheritance inheritance = inheritance(parent, locatorMatch);
            String prefixTemplate = RouteLocator.prefixTemplate(locatorMatch.getRouteInfo().getUriMatchTemplate().toString());
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
                    List.copyOf(filters), List.copyOf(errorScopes), inheritance, joinTemplates(parent.location.prefixTemplate, prefixTemplate));
            }
            return new Location(request, remainder, null, rawValues, decoded, variables, List.copyOf(filters), List.copyOf(errorScopes),
                inheritance, prefixTemplate);
        }

        /**
         * What the routes a locator route locates inherit, see {@link LocationInheritance}: of the
         * groups of the locator route, overridden by its own, over what the locator routes that
         * located it inherit. It is the same for every request of the location, so the located
         * routes with it are built once, see {@link DefaultUrlRouteInfo#inheriting(LocationInheritance)}.
         *
         * @param parent       The request located by the locator route that located this one, or {@code null}
         * @param locatorMatch The match of the locator route
         * @return The inheritance
         */
        private static LocationInheritance inheritance(@Nullable LocatedRequest<?> parent, DefaultUriRouteMatch<?, ?> locatorMatch) {
            if (locatorMatch.getRouteInfo() instanceof DefaultUrlRouteInfo<?, ?> locatorRoute) {
                // the locator route at the location that located it, built once for that location
                DefaultUrlRouteInfo<?, ?> atLocation = parent == null ? locatorRoute : locatorRoute.inheriting(parent.location.inheritance);
                return atLocation.locatedInheritance();
            }
            return parent == null ? LocationInheritance.NONE : parent.location.inheritance;
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

        /**
         * The variables the constraints of a located route see: the decoded values of the
         * prefixes, the values the handler gets, with the variables of the match.
         *
         * @param values The values of the variables of the match of the rest of the path
         * @return The decoded values of the variables of the prefixes, then of the match
         */
        Map<String, Object> withDecodedPrefixValues(Map<String, Object> values) {
            Map<String, Object> all = new LinkedHashMap<>(decodedValues);
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
        private final AnnotationMetadata annotationMetadata;
        /**
         * The URI template of the prefixes of the locator routes, empty for the root.
         */
        private final String prefixTemplate;

        @SuppressWarnings("java:S107") // what a located match has
        LocatedUriMatchInfo(String uri, Map<String, Object> values, List<UriMatchVariable> variables, Object target,
                            List<GenericHttpFilter> filters, List<RouteAssembly.RouteGroup> errorScopes,
                            AnnotationMetadata annotationMetadata, String prefixTemplate) {
            this.uri = uri;
            this.values = values;
            this.variables = variables;
            this.target = target;
            this.filters = filters;
            this.errorScopes = errorScopes;
            this.annotationMetadata = annotationMetadata;
            this.prefixTemplate = prefixTemplate;
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

        /**
         * @return The annotations of the groups of the locator routes that located the route, the
         * outer locator first
         */
        @Override
        public AnnotationMetadata annotationMetadata() {
            return annotationMetadata;
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
