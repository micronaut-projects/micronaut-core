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
package io.micronaut.http.server;

import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.filter.ReactiveFilterChainElement;
import io.micronaut.http.reactive.execution.ReactiveExecutionFlow;
import io.micronaut.web.router.RouteInfo;
import io.micronaut.web.router.RouteMatch;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.concurrent.ExecutorService;

/**
 * Executes a route: binds the arguments that are bound after the filters, invokes the route on
 * the thread its route selects, and turns its result into the one response of the route, with
 * {@link RouteResult}. The response completes when it is available; a streamed body is
 * subscribed to when it is written.
 *
 * <p>The {@link #compatibility compatibility execution} reads the route on every request: it
 * executes any route, e.g. of a custom router. An execution is prepared for a route once, see
 * {@link #prepare}: it selected the thread and the adapter of the result of the route, and
 * decides at runtime only what depends on the request, e.g. a filter that subscribes to the
 * response publisher. It captures the facts of the route and the services of the server, never
 * a request, a route match, an argument or a bean.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
abstract sealed class RouteExecution {

    final RouteExecutor owner;

    RouteExecution(RouteExecutor owner) {
        this.owner = owner;
    }

    /**
     * Execute the route of a match.
     *
     * @param propagatedContext The propagated context
     * @param routeMatch        The route match
     * @param request           The request, filtered
     * @return The response
     */
    abstract ExecutionFlow<HttpResponse<?>> execute(PropagatedContext propagatedContext, RouteMatch<?> routeMatch, HttpRequest<?> request);

    /**
     * @param owner The route executor
     * @return The execution of any route, which reads the route on every request
     */
    static RouteExecution compatibility(RouteExecutor owner) {
        return new Compatibility(owner, RouteResult.dynamic(owner));
    }

    /**
     * Prepare the execution of a route.
     *
     * @param owner     The route executor
     * @param routeInfo The route, with its effective settings
     * @return The execution
     */
    static RouteExecution prepare(RouteExecutor owner, RouteInfo<?> routeInfo) {
        RouteResult result = RouteResult.prepare(owner, routeInfo);
        ExecutorService executor = routeInfo.getExecutor(owner.serverConfiguration.getThreadSelection());
        boolean suspended = routeInfo.isSuspended();
        boolean reactorContext = owner.suspendedRoutesNeedReactorContext;
        if (executor == null) {
            if (suspended && !reactorContext) {
                return new DirectSuspend(owner, result);
            }
            if (suspended) {
                return new DirectSuspendInReactorContext(owner, result);
            }
            return new Direct(owner, result);
        }
        if (suspended && !reactorContext) {
            return new OffloadedSuspend(owner, result, executor);
        }
        if (suspended) {
            return new OffloadedSuspendInReactorContext(owner, result, executor);
        }
        if (routeInfo.isReactive()) {
            return new OffloadedReactive(owner, result, executor);
        }
        return new Offloaded(owner, result, executor);
    }

    /**
     * The execution of any route, which reads the route on every request.
     */
    private static final class Compatibility extends RouteExecution {
        private final RouteResult result;

        Compatibility(RouteExecutor owner, RouteResult result) {
            super(owner);
            this.result = result;
        }

        @Override
        ExecutionFlow<HttpResponse<?>> execute(PropagatedContext propagatedContext, RouteMatch<?> routeMatch, HttpRequest<?> request) {
            RouteInfo<?> routeInfo = routeMatch.getRouteInfo();
            ExecutorService executorService = routeInfo.getExecutor(owner.serverConfiguration.getThreadSelection());
            boolean suspendedRoutesNeedReactorContext = owner.suspendedRoutesNeedReactorContext;
            if (executorService != null) {
                if (routeInfo.isSuspended() && !suspendedRoutesNeedReactorContext) {
                    return OffloadedSuspend.execute(owner, result, executorService, propagatedContext, routeMatch, request);
                } else if (routeInfo.isSuspended()) {
                    return OffloadedSuspendInReactorContext.execute(owner, result, executorService, propagatedContext, routeMatch, request);
                } else if (routeInfo.isReactive()) {
                    return OffloadedReactive.execute(owner, result, executorService, propagatedContext, routeMatch, request);
                }
                return Offloaded.execute(owner, result, executorService, propagatedContext, routeMatch, request);
            }
            if (routeInfo.isSuspended() && !suspendedRoutesNeedReactorContext) {
                return DirectSuspend.execute(owner, result, propagatedContext, routeMatch, request);
            } else if (routeInfo.isSuspended()) {
                return DirectSuspendInReactorContext.execute(owner, result, propagatedContext, routeMatch, request);
            }
            return owner.executeRouteAndConvertBody(propagatedContext, routeMatch, request, false, null, null, result, null);
        }
    }

    /**
     * A route that runs on the thread of the request.
     */
    private static final class Direct extends RouteExecution {
        private final RouteResult result;

        Direct(RouteExecutor owner, RouteResult result) {
            super(owner);
            this.result = result;
        }

        @Override
        ExecutionFlow<HttpResponse<?>> execute(PropagatedContext propagatedContext, RouteMatch<?> routeMatch, HttpRequest<?> request) {
            return owner.executeRouteAndConvertBody(propagatedContext, routeMatch, request, false, null, null, result, null);
        }
    }

    /**
     * A suspend function without {@code kotlinx-coroutines-reactor} that runs on the thread of the
     * request: the Reactor context never reaches the coroutine.
     */
    private static final class DirectSuspend extends RouteExecution {
        private final RouteResult result;

        DirectSuspend(RouteExecutor owner, RouteResult result) {
            super(owner);
            this.result = result;
        }

        @Override
        ExecutionFlow<HttpResponse<?>> execute(PropagatedContext propagatedContext, RouteMatch<?> routeMatch, HttpRequest<?> request) {
            return execute(owner, result, propagatedContext, routeMatch, request);
        }

        static ExecutionFlow<HttpResponse<?>> execute(RouteExecutor owner, RouteResult result, PropagatedContext propagatedContext,
                                                       RouteMatch<?> routeMatch, HttpRequest<?> request) {
            return owner.executeRouteAndConvertBody(propagatedContext, routeMatch, request, true, Context.empty(), null, result, null);
        }
    }

    /**
     * A suspend function that runs on the thread of the request: the Reactor context of the
     * subscriber becomes the coroutine's {@code ReactorContext}.
     */
    private static final class DirectSuspendInReactorContext extends RouteExecution {
        private final RouteResult result;

        DirectSuspendInReactorContext(RouteExecutor owner, RouteResult result) {
            super(owner);
            this.result = result;
        }

        @Override
        ExecutionFlow<HttpResponse<?>> execute(PropagatedContext propagatedContext, RouteMatch<?> routeMatch, HttpRequest<?> request) {
            return execute(owner, result, propagatedContext, routeMatch, request);
        }

        static ExecutionFlow<HttpResponse<?>> execute(RouteExecutor owner, RouteResult result, PropagatedContext propagatedContext,
                                                       RouteMatch<?> routeMatch, HttpRequest<?> request) {
            return ReactiveExecutionFlow.fromPublisher(Mono.deferContextual(contextView -> Mono.from(
                ReactiveExecutionFlow.toPublisher(owner.executeRouteAndConvertBody(propagatedContext, routeMatch, request, true, contextView, null, result, null))
            )));
        }
    }

    /**
     * A route that runs on its executor, e.g. a blocking route: a reactive result is subscribed
     * to on the executor thread as well, the result is converted in the task.
     */
    private static final class Offloaded extends RouteExecution {
        private final RouteResult result;
        private final ExecutorService executor;

        Offloaded(RouteExecutor owner, RouteResult result, ExecutorService executor) {
            super(owner);
            this.result = result;
            this.executor = executor;
        }

        @Override
        ExecutionFlow<HttpResponse<?>> execute(PropagatedContext propagatedContext, RouteMatch<?> routeMatch, HttpRequest<?> request) {
            return execute(owner, result, executor, propagatedContext, routeMatch, request);
        }

        static ExecutionFlow<HttpResponse<?>> execute(RouteExecutor owner, RouteResult result, ExecutorService executor,
                                                       PropagatedContext propagatedContext, RouteMatch<?> routeMatch, HttpRequest<?> request) {
            return ExecutionFlow.async(executor, () -> owner.executeRouteAndConvertBody(propagatedContext, routeMatch, request, false, null, null, result, executor));
        }
    }

    /**
     * A reactive route that runs on its executor: when a filter subscribes to the response
     * publisher, it may write to its Reactor context, and the route runs on the executor when
     * that filter subscribes.
     */
    private static final class OffloadedReactive extends RouteExecution {
        private final RouteResult result;
        private final ExecutorService executor;

        OffloadedReactive(RouteExecutor owner, RouteResult result, ExecutorService executor) {
            super(owner);
            this.result = result;
            this.executor = executor;
        }

        @Override
        ExecutionFlow<HttpResponse<?>> execute(PropagatedContext propagatedContext, RouteMatch<?> routeMatch, HttpRequest<?> request) {
            return execute(owner, result, executor, propagatedContext, routeMatch, request);
        }

        static ExecutionFlow<HttpResponse<?>> execute(RouteExecutor owner, RouteResult result, ExecutorService executor,
                                                       PropagatedContext propagatedContext, RouteMatch<?> routeMatch, HttpRequest<?> request) {
            if (ReactiveFilterChainElement.isPresent(propagatedContext)) {
                return ReactiveExecutionFlow.async(executor, () -> owner.executeRouteAndConvertBody(propagatedContext, routeMatch, request, false, null, null, result, executor));
            }
            return Offloaded.execute(owner, result, executor, propagatedContext, routeMatch, request);
        }
    }

    /**
     * A suspend function without {@code kotlinx-coroutines-reactor} on an executor: the Reactor
     * context never reaches the coroutine, so the route is dispatched like a blocking one, and the
     * coroutine context keeps the continuation on the executor.
     */
    private static final class OffloadedSuspend extends RouteExecution {
        private final RouteResult result;
        private final ExecutorService executor;

        OffloadedSuspend(RouteExecutor owner, RouteResult result, ExecutorService executor) {
            super(owner);
            this.result = result;
            this.executor = executor;
        }

        @Override
        ExecutionFlow<HttpResponse<?>> execute(PropagatedContext propagatedContext, RouteMatch<?> routeMatch, HttpRequest<?> request) {
            return execute(owner, result, executor, propagatedContext, routeMatch, request);
        }

        static ExecutionFlow<HttpResponse<?>> execute(RouteExecutor owner, RouteResult result, ExecutorService executor,
                                                       PropagatedContext propagatedContext, RouteMatch<?> routeMatch, HttpRequest<?> request) {
            return ExecutionFlow.async(executor, () -> owner.executeRouteAndConvertBody(propagatedContext, routeMatch, request, true, Context.empty(), executor, result, executor));
        }
    }

    /**
     * A suspend function on an executor: it runs synchronously on the caller until its first
     * suspension point, and its coroutine context decides where it resumes, so honouring the
     * executor needs both: applying it to the publisher moves the body off the event loop, and
     * passing it on to the coroutine context keeps the continuation there. publishOn is
     * deliberate - it holds the same "the response is assembled off the event loop" invariant
     * the blocking routes get from {@link ExecutionFlow#async}.
     */
    private static final class OffloadedSuspendInReactorContext extends RouteExecution {
        private final RouteResult result;
        private final ExecutorService executor;

        OffloadedSuspendInReactorContext(RouteExecutor owner, RouteResult result, ExecutorService executor) {
            super(owner);
            this.result = result;
            this.executor = executor;
        }

        @Override
        ExecutionFlow<HttpResponse<?>> execute(PropagatedContext propagatedContext, RouteMatch<?> routeMatch, HttpRequest<?> request) {
            return execute(owner, result, executor, propagatedContext, routeMatch, request);
        }

        static ExecutionFlow<HttpResponse<?>> execute(RouteExecutor owner, RouteResult result, ExecutorService executor,
                                                       PropagatedContext propagatedContext, RouteMatch<?> routeMatch, HttpRequest<?> request) {
            return ReactiveExecutionFlow.fromPublisher(
                owner.applyExecutorToPublisher(
                    Mono.deferContextual(contextView -> Mono.from(
                        ReactiveExecutionFlow.toPublisher(owner.executeRouteAndConvertBody(propagatedContext, routeMatch, request, true, contextView, executor, result, executor))
                    )),
                    executor,
                    propagatedContext
                )
            );
        }
    }
}
