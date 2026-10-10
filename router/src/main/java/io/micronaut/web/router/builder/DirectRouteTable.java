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

import io.micronaut.context.exceptions.ConfigurationException;
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
import io.micronaut.web.router.direct.DirectMatch;
import io.micronaut.web.router.direct.DirectRequest;
import io.micronaut.web.router.direct.DirectRouteLookup;
import io.micronaut.web.router.direct.InvalidDirectRequestException;
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
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The direct routes of the {@link HttpDirectRoutes} beans: the
 * routes of each method sorted like the routes of the router, the most specific URI template
 * first, with their conditions normalized and checked, see {@link DirectConditions}.
 *
 * <p>A request is matched against the routes of its method. A {@code HEAD} request is matched
 * against the {@code HEAD} routes and the {@code GET} routes together, as the router adds an
 * implicit {@code HEAD} route to a {@code GET} route and ranks it with the others. Among the
 * routes whose URI template, constraints and conditions accept the request, the most specific
 * wins, then, like the router, an explicit {@code HEAD} route over the implicit one of a
 * {@code GET} route, then the lowest order: routes left make the request ambiguous, answered with
 * {@code 400} like the router does. Routes that would make every request of their URI template
 * ambiguous, of the same method, URI template and order, without a condition or a constraint,
 * fail to build.</p>
 *
 * <p>{@link #match} matches a request once, and runs no route: a route that answers with a value
 * is matched by a {@link DirectMatch.Sync} allocated once, and a route whose function composes the
 * response by a {@link FunctionMatch}, allocated for the request, which is the
 * {@link DirectContext} of its function too. The runtime then runs the route: a route that
 * declines the request ends it, and the request continues to the ordinary routes, whether the
 * route is synchronous or asynchronous.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class DirectRouteTable implements DirectRouteLookup {

    private static final Logger LOG = LoggerFactory.getLogger(DirectRouteTable.class);
    private static final Route[] NONE = new Route[0];
    /**
     * The match of a request that routes match equally well: answered with {@code 400}, like the
     * router does.
     */
    private static final DirectMatch.Sync AMBIGUOUS = responses -> responses.status(HttpStatus.BAD_REQUEST);
    private static final Comparator<Route> BY_SPECIFICITY = Comparator
        .comparingInt((Route route) -> -route.rawLength)
        .thenComparingInt(route -> route.variableCount)
        .thenComparingInt(route -> route.patternVariableCount);

    private final Map<String, Route[]> routesByMethod;
    private final ConversionService conversionService;
    private final RouteConditionContext conditionContext;
    private final boolean empty;

    private DirectRouteTable(Map<String, Route[]> routesByMethod,
                             ConversionService conversionService,
                             RouteConditionContext conditionContext) {
        this.routesByMethod = routesByMethod;
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
     * @param discard           Releases a response an asynchronous route completed after its
     *                          response was cancelled, which the runtime never writes
     * @return The routes
     * @throws ConfigurationException if two routes would make every request of their URI template ambiguous
     */
    static DirectRouteTable build(List<DirectRouteDeclaration> declarations,
                                  ConversionService conversionService,
                                  RouteConditionContext conditionContext,
                                  ExecutorLookup executors,
                                  Consumer<HttpResponse<?>> discard) {
        Map<String, List<Route>> byMethod = new HashMap<>();
        Map<Unconditional, DirectRouteDeclaration> unconditional = new HashMap<>();
        for (DirectRouteDeclaration declaration : declarations) {
            Route route = route(declaration, executors, discard);
            if (route.unconditional && route.constraints.length == 0) {
                DirectRouteDeclaration same = unconditional.putIfAbsent(
                    new Unconditional(declaration.httpMethodName, declaration.uriTemplate, declaration.order), declaration);
                if (same != null) {
                    throw new ConfigurationException("The " + same.describe() + " and the " + declaration.describe()
                        + " have the same URI template and order, and no condition or constraint: every request they match "
                        + "would be ambiguous. Give one of them another order, a condition or a constraint, or remove one");
                }
            }
            byMethod.computeIfAbsent(declaration.httpMethodName, name -> new ArrayList<>()).add(route);
            if (HttpMethod.GET.name().equals(declaration.httpMethodName)) {
                // the implicit HEAD route of a GET route, ranked with the HEAD routes
                byMethod.computeIfAbsent(HttpMethod.HEAD.name(), name -> new ArrayList<>()).add(route.asImplicitHead());
            }
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

    private static Route route(DirectRouteDeclaration declaration, ExecutorLookup executors, Consumer<HttpResponse<?>> discard) {
        // checked when they were given to the route
        RouteCondition condition = RouteConditions.normalizeAll(declaration.conditions);
        String executorName = declaration.executorName;
        Executor executor = executorName == null ? null : executors.executor(executorName, declaration);
        return new Route(declaration, new UriTemplateMatcher(declaration.uriTemplate), condition,
            declaration.constraints.toArray(new Predicate[0]), declaration.order, executor, discard);
    }

    @Override
    public boolean isEmpty() {
        return empty;
    }

    @Override
    public @Nullable DirectMatch match(DirectRequest request) {
        if (empty) {
            return null;
        }
        Route[] routes = routesByMethod.getOrDefault(request.methodName(), NONE);
        if (routes.length == 0) {
            // no direct route of the method: the path is not parsed
            return null;
        }
        try {
            return lookup(routes, request.path(), request);
        } catch (InvalidDirectRequestException e) {
            // the target or the query is not valid: the runtime answers the request on its ordinary path
            if (LOG.isDebugEnabled()) {
                LOG.debug("The direct routes do not match the invalid request {}: {}", request.methodName(), e.getMessage());
            }
            return null;
        }
    }

    /**
     * @return The match of the route that answers the request, {@link #AMBIGUOUS}, or
     * {@code null} if no route matched
     */
    private @Nullable DirectMatch lookup(Route[] routes, String path, DirectRequest request) {
        Route best = null;
        UriMatchInfo bestMatch = null;
        PathVariables bestVariables = null;
        boolean ambiguous = false;
        for (Route route : routes) {
            if (best != null && !best.equallySpecific(route)) {
                // the routes are sorted: the others are less specific
                break;
            }
            UriMatchInfo match = route.matcher.tryMatch(path);
            if (match == null) {
                continue;
            }
            // the constraints read the variables the function of the route is given
            PathVariables variables = route.constraints.length == 0 ? null : new DefaultPathVariables(match.getVariableValues(), conversionService);
            if (!route.accepts(match, variables, request, conditionContext)) {
                continue;
            }
            if (best == null || route.preferredTo(best)) {
                best = route;
                bestMatch = match;
                bestVariables = variables;
                ambiguous = false;
            } else if (route.implicitHead == best.implicitHead && route.order == best.order) {
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
            return AMBIGUOUS;
        }
        return best.match(request, bestMatch, bestVariables, conversionService);
    }

    private static HttpResponse<?> serverError(DirectRouteDeclaration declaration, Throwable error, HttpResponseFactory responses) {
        LOG.error("The {} failed to create its response: {}", declaration, error.getMessage(), error);
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
     * What makes two routes without a condition or a constraint match the same requests equally
     * well.
     *
     * @param httpMethodName The method
     * @param uriTemplate    The URI template
     * @param order          The order
     */
    private record Unconditional(String httpMethodName, String uriTemplate, int order) {
    }

    /**
     * The match of a route whose function composes the response, allocated for a request: the
     * {@link DirectContext} of the function too, so that a request costs one object besides its
     * response. Its path variables are created when they are first read, unless the constraints
     * of the route created them. It is used by one thread at a time: the thread that received the
     * request, then the executor of the route, which the runtime hands it over to.
     */
    abstract static sealed class FunctionMatch implements DirectContext {

        final Route route;
        private final DirectRequest request;
        private final UriMatchInfo match;
        private final ConversionService conversionService;
        private @Nullable PathVariables variables;

        FunctionMatch(Route route, DirectRequest request, UriMatchInfo match, @Nullable PathVariables variables, ConversionService conversionService) {
            this.route = route;
            this.request = request;
            this.match = match;
            this.variables = variables;
            this.conversionService = conversionService;
        }

        @Override
        public DirectRequest request() {
            return request;
        }

        @Override
        public PathVariables pathVariables() {
            PathVariables pathVariables = variables;
            if (pathVariables == null) {
                pathVariables = new DefaultPathVariables(match.getVariableValues(), conversionService);
                variables = pathVariables;
            }
            return pathVariables;
        }

        @Override
        public String toString() {
            return "match of the " + route.declaration + ": " + match.getVariableValues();
        }
    }

    /**
     * The match of a synchronous route whose function composes the response.
     */
    static final class SyncMatch extends FunctionMatch implements DirectMatch.Sync {

        SyncMatch(Route route, DirectRequest request, UriMatchInfo match, @Nullable PathVariables variables, ConversionService conversionService) {
            super(route, request, match, variables, conversionService);
        }

        @Override
        public @Nullable HttpResponse<?> respond(HttpResponseFactory responses) {
            return route.respond(this, responses);
        }
    }

    /**
     * The match of an asynchronous route: on an executor, or completed later.
     */
    static final class AsyncMatch extends FunctionMatch implements DirectMatch.Async {

        AsyncMatch(Route route, DirectRequest request, UriMatchInfo match, @Nullable PathVariables variables, ConversionService conversionService) {
            super(route, request, match, variables, conversionService);
        }

        @Override
        public CompletableFuture<@Nullable HttpResponse<?>> respondAsync(HttpResponseFactory responses) {
            return route.respondAsync(this, responses);
        }
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
        /**
         * The match of a route that answers with a value, allocated once, or {@code null}.
         */
        final DirectMatch.@Nullable Sync constant;
        /**
         * Releases a response completed after the response of the route was cancelled.
         */
        final Consumer<HttpResponse<?>> discard;
        /**
         * Whether this is the implicit {@code HEAD} route of a {@code GET} route.
         */
        final boolean implicitHead;

        Route(DirectRouteDeclaration declaration, UriTemplateMatcher matcher, RouteCondition condition,
              Predicate<? super PathVariables>[] constraints, int order, @Nullable Executor executor,
              Consumer<HttpResponse<?>> discard) {
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
            ResponseTemplate template = declaration.constant;
            // a value is never on an executor, see DefaultDirectRouteSpec#respond(HttpResponse)
            this.constant = template == null ? null : template::create;
            this.discard = discard;
            this.implicitHead = false;
        }

        private Route(Route get) {
            this.declaration = get.declaration;
            this.matcher = get.matcher;
            this.condition = get.condition;
            this.constraints = get.constraints;
            this.order = get.order;
            this.rawLength = get.rawLength;
            this.variableCount = get.variableCount;
            this.patternVariableCount = get.patternVariableCount;
            this.unconditional = get.unconditional;
            this.executor = get.executor;
            this.async = get.async;
            this.constant = get.constant;
            this.discard = get.discard;
            this.implicitHead = true;
        }

        /**
         * @return The implicit {@code HEAD} route of this {@code GET} route: the server writes
         * the headers of its response only
         */
        Route asImplicitHead() {
            return new Route(this);
        }

        /**
         * Whether this route wins over an equally specific one, like in the router: an explicit
         * {@code HEAD} route over the implicit one of a {@code GET} route, then the lowest order.
         */
        boolean preferredTo(Route other) {
            if (implicitHead != other.implicitHead) {
                return !implicitHead;
            }
            return order < other.order;
        }

        boolean equallySpecific(Route other) {
            return rawLength == other.rawLength && variableCount == other.variableCount && patternVariableCount == other.patternVariableCount;
        }

        /**
         * The constraints first, as in the router, then the conditions.
         *
         * @param variables The path variables, {@code null} if the route has no constraint
         */
        boolean accepts(UriMatchInfo match, @Nullable PathVariables variables, DirectRequest request, RouteConditionContext context) {
            if (variables != null) {
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
         * @return The match of this route for a request
         */
        DirectMatch match(DirectRequest request, UriMatchInfo match, @Nullable PathVariables variables, ConversionService conversionService) {
            DirectMatch.Sync value = constant;
            if (value != null) {
                return value;
            }
            return async
                ? new AsyncMatch(this, request, match, variables, conversionService)
                : new SyncMatch(this, request, match, variables, conversionService);
        }

        /**
         * The response of a request, composed for it by the function of the route.
         *
         * @return The response, or {@code null} if the function declined the request
         */
        @Nullable HttpResponse<?> respond(DirectContext context, HttpResponseFactory responses) {
            try {
                // null declines: the request continues as if no direct route matched it
                return Objects.requireNonNull(declaration.response, "response").apply(context);
            } catch (Throwable t) {
                // an Error too: the request is answered, like an ordinary route's
                return failed(t, responses);
            }
        }

        /**
         * Start an asynchronous route: the function runs on the executor of the route, or on the
         * calling thread without one.
         *
         * @return The response, completed later
         */
        CompletableFuture<@Nullable HttpResponse<?>> respondAsync(DirectContext context, HttpResponseFactory responses) {
            CompletableFuture<@Nullable HttpResponse<?>> result = new CompletableFuture<>();
            Executor routeExecutor = executor;
            if (routeExecutor == null) {
                run(context, responses, result);
                return result;
            }
            try {
                routeExecutor.execute(() -> {
                    // cancelled, e.g. as the connection closed, before it started
                    if (!result.isDone()) {
                        try {
                            run(context, responses, result);
                        } catch (Throwable t) {
                            // the task never ends without completing the response
                            complete(result, serverError(declaration, t, responses));
                        }
                    }
                });
            } catch (RejectedExecutionException e) {
                result.complete(serverError(declaration, e, responses));
            }
            return result;
        }

        private void run(DirectContext context,
                         HttpResponseFactory responses,
                         CompletableFuture<@Nullable HttpResponse<?>> result) {
            Function<DirectContext, ? extends CompletionStage<? extends @Nullable HttpResponse<?>>> asyncResponse = declaration.asyncResponse;
            if (asyncResponse == null) {
                // a function on an executor
                complete(result, respond(context, responses));
                return;
            }
            CompletionStage<? extends @Nullable HttpResponse<?>> stage;
            try {
                stage = Objects.requireNonNull(asyncResponse.apply(context),
                    "The function of an asynchronous direct route returned no stage");
            } catch (Throwable t) {
                complete(result, failed(t, responses));
                return;
            }
            stage.whenComplete((response, error) -> {
                if (error == null) {
                    // null declines
                    complete(result, response);
                } else if (!result.isDone()) {
                    result.complete(failed(error, responses));
                }
            });
            result.whenComplete((response, error) -> {
                if (error instanceof CancellationException) {
                    cancel(stage);
                }
            });
        }

        /**
         * Complete the response, or release it if the response was cancelled meanwhile, e.g. as
         * the connection closed: nothing writes it then.
         */
        private void complete(CompletableFuture<@Nullable HttpResponse<?>> result, @Nullable HttpResponse<?> response) {
            if (!result.complete(response) && response != null) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("The {} completed its response after it was cancelled: the response is released", declaration);
                }
                discard.accept(response);
            }
        }

        /**
         * The response of a function that failed: {@code null}, which declines the request, if
         * the function read the target or the query of an invalid request, otherwise {@code 500}.
         */
        private @Nullable HttpResponse<?> failed(Throwable error, HttpResponseFactory responses) {
            Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
            if (cause instanceof InvalidDirectRequestException) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("The {} read an invalid request, which it declines: {}", declaration, cause.getMessage());
                }
                return null;
            }
            return serverError(declaration, cause, responses);
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
