/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.http.filter;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.function.BiFunction;

/**
 * The filter runner will start processing the filters in the forward order.
 * All the request filters are executed till one of them returns a response (bypasses the route execution for controllers or the client invocation),
 * or the terminal filter will produce the response from the route/client call.
 * After that, the filters are processed in the opposite order so response filters can be processed,
 * which can sometimes override the existing response.
 * There is a special case of response filters that needs to process the response; for those cases,
 * the filter needs to be suspended, and the next filter in the order needs to be executed.
 * When the response is committed, the filter will be resumed when it's processed again.
 * There is a special case for the client filters; those will process the exception,
 * which needs to be tracked during the response filtering phase.
 *
 * @author Jonas Konrad
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
public class FilterRunner {

    /**
     * All filters to run. Request filters are executed in order from first to last, response
     * filters in the reverse order.
     */
    private final List<InternalHttpFilter> filters;
    private final BiFunction<HttpRequest<?>, PropagatedContext, ExecutionFlow<HttpResponse<?>>> responseProvider;

    /**
     * Create a new filter runner, to be used only once.
     *
     * @param filters          The filters to run
     * @param responseProvider The response provider
     */
    public FilterRunner(List<GenericHttpFilter> filters, BiFunction<HttpRequest<?>, PropagatedContext, ExecutionFlow<HttpResponse<?>>> responseProvider) {
        this.filters = (List) filters; // GenericHttpFilter is sealed and all implementations implement InternalHttpFilter
        this.responseProvider = responseProvider;
    }

    private static void checkOrdered(List<? extends GenericHttpFilter> filters) {
        if (!filters.stream().allMatch(f -> f instanceof Ordered)) {
            throw new IllegalStateException("Some filters cannot be ordered: " + filters);
        }
    }

    /**
     * Sort filters according to their declared order (e.g. annotation,
     * {@link Ordered#getOrder()}...). List must not contain terminal filters.
     *
     * @param filters The list of filters to sort in place
     */
    public static void sort(@NonNull List<GenericHttpFilter> filters) {
        checkOrdered(filters);
        OrderUtil.sort(filters);
    }

    /**
     * Sort filters according to their declared order (e.g. annotation,
     * {@link Ordered#getOrder()}...). List must not contain terminal filters. Reverse order.
     *
     * @param filters The list of filters to sort in place
     */
    public static void sortReverse(@NonNull List<GenericHttpFilter> filters) {
        checkOrdered(filters);
        OrderUtil.reverseSort(filters);
    }

    /**
     * Transform a response, e.g. by replacing an error response with an exception. Called before
     * every filter.
     *
     * @param request           The current request
     * @param response          The current response
     * @param propagatedContext The propagated context
     * @return A flow that will be passed on to the next filter
     */
    @SuppressWarnings("java:S1452")
    protected ExecutionFlow<HttpResponse<?>> processResponse(HttpRequest<?> request, HttpResponse<?> response, PropagatedContext propagatedContext) {
        return ExecutionFlow.just(response);
    }

    /**
     * Transform a failure, e.g. by replacing an exception with an error response. Called before
     * every filter.
     *
     * @param request           The current request
     * @param failure           The failure
     * @param propagatedContext The propagated context
     * @return A flow that will be passed on to the next filter, or null if exception is not remapped
     */
    @Nullable
    @SuppressWarnings("java:S1452")
    protected ExecutionFlow<HttpResponse<?>> processFailure(HttpRequest<?> request, Throwable failure, PropagatedContext propagatedContext) {
        return null;
    }

    /**
     * Execute the filters for the given request. May only be called once
     *
     * @param request The request
     * @return The flow that completes after all filters and the terminal operation, with the final
     * response
     */
    public final ExecutionFlow<HttpResponse<?>> run(HttpRequest<?> request) {
        return run(request, PropagatedContext.getOrEmpty());
    }

    /**
     * Execute the filters for the given request. May only be called once
     *
     * @param request The request
     * @param propagatedContext The propagated context
     * @return The flow that completes after all filters and the terminal operation, with the final
     * response
     */
    public final ExecutionFlow<HttpResponse<?>> run(HttpRequest<?> request,
                                                    PropagatedContext propagatedContext) {
        List<InternalHttpFilter> filtersToRun = new ArrayList<>(filters.size());
        for (InternalHttpFilter filter : filters) {
            if (filter.isEnabled(request)) {
                filtersToRun.add(filter);
            }
        }
        if (filtersToRun.isEmpty()) {
            return responseProvider.apply(request, propagatedContext);
        }
        ListIterator<InternalHttpFilter> iterator = filtersToRun.listIterator();
        ExecutionFlow<FilterContext> flow = filterRequest(new FilterContext(request, propagatedContext), iterator);
        FilterContext flowContext = flow.tryCompleteValue();
        if (flowContext != null) {
            return filterResponse(flowContext, iterator, null);
        }
        return flow.flatMap(context -> filterResponse(context, iterator, null));
    }

    private ExecutionFlow<FilterContext> filterRequest(FilterContext context,
                                                       ListIterator<InternalHttpFilter> iterator) {
        while (iterator.hasNext()) {
            InternalHttpFilter filter = iterator.next();
            if (!filter.isFiltersRequest()) {
                continue;
            }
            // At-least one request filter
            ExecutionFlow<FilterContext> flow;
            if (filter.hasContinuation()) {
                flow = filter.processRequestFilter(context, newContext -> {
                    if (newContext.response() != null) {
                        return ExecutionFlow.just(newContext);
                    }
                    return filterRequest(newContext, iterator);
                });
            } else {
                flow = filter.processRequestFilter(context);
                FilterContext flowContext = flow.tryCompleteValue();
                if (flowContext != null) {
                    // Imperative flow: Unwrap the context and continue the loop
                    if (context != flowContext) {
                        if (flowContext.response() != null) {
                            return ExecutionFlow.just(flowContext);
                        }
                        context = flowContext;
                    }
                    continue;
                } else {
                    // Reactive/Async request filter
                    flow = flow.flatMap(newContext -> {
                        if (newContext.response() != null) {
                            return ExecutionFlow.just(newContext);
                        }
                        return filterRequest(newContext, iterator);
                    });
                }
            }
            FilterContext finalContext = context;
            return flow.onErrorResume(throwable -> processFailureFilterException(finalContext, iterator, throwable));
        }
        return provideResponse(context, iterator);
    }

    private ExecutionFlow<HttpResponse<?>> filterResponse(FilterContext context,
                                                          ListIterator<InternalHttpFilter> iterator,
                                                          @Nullable
                                                          Throwable exception) {
        // Walk backwards and execute response filters
        while (iterator.hasPrevious()) {
            InternalHttpFilter filter = iterator.previous();
            if (!filter.isFiltersResponse()) {
                continue;
            }
            ExecutionFlow<FilterContext> flow = filter.processResponseFilter(context, exception);
            FilterContext flowContext = flow.tryCompleteValue();
            if (flowContext != null) {
                // Imperative flow: Unwrap the context and continue the loop
                if (context != flowContext) {
                    // Response modified by the filter
                    flow = processResponse(flowContext.request(), flowContext.response(), flowContext.propagatedContext()).map(flowContext::withResponse);
                    exception = null;
                    flowContext = flow.tryCompleteValue();
                    if (flowContext != null) {
                        context = flowContext;
                        continue;
                    }
                } else {
                    continue;
                }
            }
            // Reactive/Async response filter or unwrap not allowed
            FilterContext finalContext = context;
            Throwable finalException = exception;
            return flow
                .flatMap(newContext -> {
                    if (finalContext != newContext) {
                        // Response modified by the filter
                        return processResponse(newContext.request(), newContext.response(), newContext.propagatedContext()).map(newContext::withResponse);
                    }
                    return ExecutionFlow.just(newContext);
                })
                .onErrorResume(throwable -> processFailurePropagateException(throwable, finalContext))
                .flatMap(newContext -> filterResponse(newContext, iterator, newContext.response() == null ? finalException : null));
        }
        if (context.response() != null) {
            return ExecutionFlow.just(context.response());
        }
        if (exception != null) {
            // This scenario only applies for client filters
            // Filters didn't remap the exception to any response
            return ExecutionFlow.error(exception);
        }
        return ExecutionFlow.error(new IllegalStateException("No response after response filters completed!"));
    }

    private ExecutionFlow<FilterContext> processFailurePropagateException(Throwable throwable, FilterContext context) {
        ExecutionFlow<HttpResponse<?>> flow = processFailure(context.request(), throwable, context.propagatedContext());
        if (flow == null) {
            return ExecutionFlow.error(throwable);
        }
        return flow.map(context::withResponse);
    }

    private ExecutionFlow<FilterContext> provideResponse(FilterContext context,
                                                         ListIterator<InternalHttpFilter> iterator) {
        ExecutionFlow<HttpResponse<?>> flow = responseProvider.apply(context.request(), context.propagatedContext());
        if (flow.tryCompleteValue() != null) {
            return flow.map(context::withResponse);
        }
        return flow.map(context::withResponse)
            .onErrorResume(throwable -> processFailureFilterException(context, iterator, throwable));
    }

    private ExecutionFlow<FilterContext> processFailureFilterException(FilterContext context,
                                                                       ListIterator<InternalHttpFilter> iterator,
                                                                       Throwable throwable) {
        ExecutionFlow<HttpResponse<?>> flow = processFailure(context.request(), throwable, context.propagatedContext());
        if (flow == null) {
            // Exception filtering scenario of the http client
            return filterResponse(context, iterator, throwable).map(context::withResponse);
        }
        return flow.map(context::withResponse);
    }

}
