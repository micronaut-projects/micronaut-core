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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpResponseFactory;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.PathVariables;
import io.micronaut.http.uri.UriMatchInfo;
import io.micronaut.http.uri.UriTemplateMatcher;
import io.micronaut.web.router.RouteConditionContext;
import io.micronaut.web.router.direct.DirectRequest;
import io.micronaut.web.router.direct.DirectRouteLookup;
import io.micronaut.web.router.direct.PendingResponse;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The direct routes of the {@link HttpDirectRoutes} beans: the
 * routes of each method sorted like the routes of the router, the most specific URI template
 * first, with their conditions normalized and checked, see {@link DirectConditions}.
 *
 * <p>A request is matched against the routes of its method, and a {@code HEAD} request that no
 * {@code HEAD} route matches against the {@code GET} routes, as the router adds an implicit
 * {@code HEAD} route to a {@code GET} route. Among the routes whose URI template, constraints and
 * conditions accept the request, the most specific wins, then the lowest order: routes left with
 * the same order make the request ambiguous, answered with {@code 400} like the router does. A
 * route that matched and declined the request ends the lookup: the request continues to the
 * ordinary routes, whether the route is synchronous or asynchronous.</p>
 *
 * <p>{@link #find} matches a request once: it answers a synchronous route, and starts an
 * asynchronous route, on its executor, or on the calling thread without one, and returns a
 * {@link PendingResponse} with the stage of its response.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class DirectRouteTable implements DirectRouteLookup {

    private static final Logger LOG = LoggerFactory.getLogger(DirectRouteTable.class);
    private static final Route[] NONE = new Route[0];
    /**
     * What {@link #lookup(Route[], String, DirectRequest, HttpResponseFactory)} returns when the
     * route that matched declined the request: unlike no match, it ends the lookup.
     */
    private static final Object DECLINED = new Object();
    private static final Comparator<Route> BY_SPECIFICITY = Comparator
        .comparingInt((Route route) -> -route.rawLength)
        .thenComparingInt(route -> route.variableCount)
        .thenComparingInt(route -> route.patternVariableCount);

    private final Map<String, Route[]> routesByMethod;
    private final Route[] getRoutes;
    private final Route[] headRoutes;
    private final ConversionService conversionService;
    private final RouteConditionContext conditionContext;
    private final boolean empty;

    private DirectRouteTable(Map<String, Route[]> routesByMethod,
                             ConversionService conversionService,
                             RouteConditionContext conditionContext) {
        this.routesByMethod = routesByMethod;
        this.getRoutes = routesByMethod.getOrDefault(HttpMethod.GET.name(), NONE);
        this.headRoutes = routesByMethod.getOrDefault(HttpMethod.HEAD.name(), NONE);
        this.conversionService = conversionService;
        this.conditionContext = conditionContext;
        this.empty = routesByMethod.isEmpty();
    }

    /**
     * Build the direct routes, once they are declared.
     *
     * @param declarations      The declared routes, in the order they were declared
     * @param conversionService Converts the path variables
     * @param conditionContext  Gives the clock of the time conditions
     * @param executors         The executor of a name
     * @return The routes
     */
    static DirectRouteTable build(List<DirectRouteDeclaration> declarations,
                                  ConversionService conversionService,
                                  RouteConditionContext conditionContext,
                                  ExecutorLookup executors) {
        Map<String, List<Route>> byMethod = new HashMap<>();
        for (DirectRouteDeclaration declaration : declarations) {
            Route route = route(declaration, executors);
            byMethod.computeIfAbsent(declaration.httpMethodName, name -> new ArrayList<>()).add(route);
        }
        Map<String, Route[]> routesByMethod = HashMap.newHashMap(byMethod.size());
        for (Map.Entry<String, List<Route>> entry : byMethod.entrySet()) {
            List<Route> routes = entry.getValue();
            // stable: equally specific routes keep the order they were declared in
            routes.sort(BY_SPECIFICITY);
            routesByMethod.put(entry.getKey(), routes.toArray(NONE));
        }
        return new DirectRouteTable(routesByMethod, conversionService, conditionContext);
    }

    private static Route route(DirectRouteDeclaration declaration, ExecutorLookup executors) {
        // checked when they were given to the route
        RouteCondition condition = RouteConditions.normalizeAll(declaration.conditions);
        String executorName = declaration.executorName;
        Executor executor = executorName == null ? null : executors.executor(executorName, declaration);
        return new Route(declaration, new UriTemplateMatcher(declaration.uriTemplate), condition,
            declaration.constraints.toArray(new Predicate[0]), declaration.order, executor);
    }

    @Override
    public boolean isEmpty() {
        return empty;
    }

    @Override
    public @Nullable HttpResponse<?> find(DirectRequest request, HttpResponseFactory responses) {
        if (empty) {
            return null;
        }
        String method = request.methodName();
        Route[] routes;
        boolean head = false;
        if (HttpMethod.HEAD.name().equals(method)) {
            routes = headRoutes;
            head = true;
        } else if (HttpMethod.GET.name().equals(method)) {
            routes = getRoutes;
        } else {
            routes = routesByMethod.getOrDefault(method, NONE);
        }
        if (routes.length == 0 && !(head && getRoutes.length > 0)) {
            // no direct route of the method: the path is not parsed
            return null;
        }
        String path = request.path();
        Object response = lookup(routes, path, request, responses);
        if (response == null && head) {
            // the implicit HEAD route of a GET route, when no HEAD route matched: the server writes the headers only
            response = lookup(getRoutes, path, request, responses);
        }
        return response == DECLINED ? null : (HttpResponse<?>) response;
    }

    /**
     * @return The response, a {@link PendingResponse} for an asynchronous route, {@link #DECLINED}
     * if the route that matched declined the request, or {@code null} if no route matched
     */
    private @Nullable Object lookup(Route[] routes, String path, DirectRequest request, HttpResponseFactory responses) {
        Route best = null;
        UriMatchInfo bestMatch = null;
        boolean ambiguous = false;
        for (Route route : routes) {
            if (best != null && !best.equallySpecific(route)) {
                // the routes are sorted: the others are less specific
                break;
            }
            UriMatchInfo match = route.matcher.tryMatch(path);
            if (match == null || !route.accepts(match, request, conversionService, conditionContext)) {
                continue;
            }
            if (best == null || route.order < best.order) {
                best = route;
                bestMatch = match;
                ambiguous = false;
            } else if (route.order == best.order) {
                ambiguous = true;
            }
        }
        if (best == null || bestMatch == null) {
            return null;
        }
        if (ambiguous) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Several direct routes match {} {} equally well, e.g. the {}", request.methodName(), path, best.declaration);
            }
            // like the router
            return responses.status(HttpStatus.BAD_REQUEST);
        }
        HttpResponse<?> response = best.async
            ? best.respondAsync(bestMatch, conversionService, responses)
            : best.respond(bestMatch, conversionService, responses);
        return response == null ? DECLINED : response;
    }

    private static HttpResponse<?> serverError(DirectRouteDeclaration declaration, Throwable error, HttpResponseFactory responses) {
        Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
        LOG.error("The {} failed to create its response: {}", declaration, cause.getMessage(), cause);
        return responses.status(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * Finds the executor of a direct route when the routes are built.
     */
    @FunctionalInterface
    interface ExecutorLookup {
        /**
         * @param executorName The name of the executor
         * @param route        The route, for the message
         * @return The executor
         * @throws RuntimeException if there is no executor of the name
         */
        Executor executor(String executorName, DirectRouteDeclaration route);
    }

    /**
     * A built direct route.
     */
    private static final class Route {
        final DirectRouteDeclaration declaration;
        final UriTemplateMatcher matcher;
        final RouteCondition condition;
        final Predicate<? super PathVariables>[] constraints;
        final int order;
        final int rawLength;
        final int variableCount;
        final int patternVariableCount;
        final boolean unconditional;
        final @Nullable Executor executor;
        final boolean async;

        Route(DirectRouteDeclaration declaration, UriTemplateMatcher matcher, RouteCondition condition,
              Predicate<? super PathVariables>[] constraints, int order, @Nullable Executor executor) {
            this.declaration = declaration;
            this.matcher = matcher;
            this.condition = condition;
            this.constraints = constraints;
            this.order = order;
            this.rawLength = matcher.getRawLength();
            this.variableCount = matcher.getPathVariableCount();
            this.patternVariableCount = matcher.getPatternVariableCount();
            this.unconditional = condition.equals(RouteConditions.ALWAYS);
            this.executor = executor;
            this.async = declaration.isAsync();
        }

        boolean equallySpecific(Route other) {
            return rawLength == other.rawLength && variableCount == other.variableCount && patternVariableCount == other.patternVariableCount;
        }

        /**
         * The constraints first, as in the router, then the conditions.
         */
        boolean accepts(UriMatchInfo match, DirectRequest request, ConversionService conversionService, RouteConditionContext context) {
            if (constraints.length > 0) {
                PathVariables variables = new DefaultPathVariables(match.getVariableValues(), conversionService);
                for (Predicate<? super PathVariables> constraint : constraints) {
                    try {
                        if (!constraint.test(variables)) {
                            return false;
                        }
                    } catch (RuntimeException e) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("A constraint of the {} rejected the path variables {}: {}", declaration, match.getVariableValues(), e.getMessage(), e);
                        }
                        return false;
                    }
                }
            }
            return unconditional || DirectConditions.matches(condition, request, context);
        }

        /**
         * The response of a request, composed for it: a response given as a value is copied.
         *
         * @return The response, or {@code null} if the function of the route declined the request
         */
        @Nullable HttpResponse<?> respond(UriMatchInfo match, ConversionService conversionService, HttpResponseFactory responses) {
            ResponseTemplate constant = declaration.constant;
            Function<DirectContext, ? extends @Nullable HttpResponse<?>> response = declaration.response;
            if (constant != null) {
                return constant.create(responses);
            }
            try {
                // null declines: the request continues as if no direct route matched it
                return Objects.requireNonNull(response, "response")
                    .apply(new DefaultDirectContext(responses, new DefaultPathVariables(match.getVariableValues(), conversionService)));
            } catch (RuntimeException e) {
                return serverError(declaration, e, responses);
            }
        }

        /**
         * Start an asynchronous route: the function runs on the executor of the route, or on the
         * calling thread without one.
         *
         * @return The pending response, with the stage of the response
         */
        PendingResponse respondAsync(UriMatchInfo match, ConversionService conversionService, HttpResponseFactory responses) {
            CompletableFuture<@Nullable HttpResponse<?>> result = new CompletableFuture<>();
            Executor routeExecutor = executor;
            if (routeExecutor == null) {
                run(match, conversionService, responses, result);
                return new PendingResponse(result);
            }
            try {
                routeExecutor.execute(() -> {
                    // cancelled, e.g. as the connection closed, before it started
                    if (!result.isDone()) {
                        run(match, conversionService, responses, result);
                    }
                });
            } catch (RejectedExecutionException e) {
                result.complete(serverError(declaration, e, responses));
            }
            return new PendingResponse(result);
        }

        private void run(UriMatchInfo match,
                         ConversionService conversionService,
                         HttpResponseFactory responses,
                         CompletableFuture<@Nullable HttpResponse<?>> result) {
            Function<DirectContext, ? extends CompletionStage<? extends @Nullable HttpResponse<?>>> asyncResponse = declaration.asyncResponse;
            if (asyncResponse == null) {
                // a function or a value on an executor
                result.complete(respond(match, conversionService, responses));
                return;
            }
            CompletionStage<? extends @Nullable HttpResponse<?>> stage;
            try {
                stage = Objects.requireNonNull(
                    asyncResponse.apply(new DefaultDirectContext(responses, new DefaultPathVariables(match.getVariableValues(), conversionService))),
                    "The function of an asynchronous direct route returned no stage");
            } catch (RuntimeException e) {
                result.complete(serverError(declaration, e, responses));
                return;
            }
            stage.whenComplete((response, error) -> {
                if (error == null) {
                    // null declines
                    result.complete(response);
                } else if (!result.isDone()) {
                    result.complete(serverError(declaration, error, responses));
                }
            });
            result.whenComplete((response, error) -> {
                if (error instanceof CancellationException) {
                    cancel(stage);
                }
            });
        }

        private void cancel(CompletionStage<?> stage) {
            try {
                stage.toCompletableFuture().cancel(false);
            } catch (UnsupportedOperationException e) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("The stage of the {} cannot be cancelled: {}", declaration, e.getMessage());
                }
            }
        }
    }
}
