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
import io.micronaut.http.MutableHttpResponse;

import java.util.List;
import java.util.ListIterator;
import java.util.Objects;

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
    private final PropagatedContext initialPropagatedContext = PropagatedContext.getOrEmpty();

    /**
     * Create a new filter runner, to be used only once.
     *
     * @param filters The filters to run
     */
    public FilterRunner(List<GenericHttpFilter> filters) {
        this.filters = Objects.requireNonNull(filters, "filters").stream().map(f -> {
            if (f instanceof InternalHttpFilter internalHttpFilter) {
                return internalHttpFilter;
            }
            throw new IllegalStateException("Unrecognized filter implementation: " + f);
        }).toList();
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
    protected ExecutionFlow<? extends HttpResponse<?>> processResponse(HttpRequest<?> request, HttpResponse<?> response, PropagatedContext propagatedContext) {
        return ExecutionFlow.just(response);
    }

    /**
     * Transform a failure, e.g. by replacing an exception with an error response. Called before
     * every filter.
     *
     * @param request           The current request
     * @param failure           The failure
     * @param propagatedContext The propagated context
     * @return A flow that will be passed on to the next filter
     */
    @SuppressWarnings("java:S1452")
    protected ExecutionFlow<? extends HttpResponse<?>> processFailure(HttpRequest<?> request, Throwable failure, PropagatedContext propagatedContext) {
        return ExecutionFlow.error(failure);
    }

    /**
     * Execute the filters for the given request. May only be called once
     *
     * @param request The request
     * @return The flow that completes after all filters and the terminal operation, with the final
     * response
     */
    @SuppressWarnings("java:S1452")
    public final ExecutionFlow<MutableHttpResponse<?>> run(HttpRequest<?> request) {
        return (ExecutionFlow) filterRequest(new FilterContext(request, initialPropagatedContext), filters.listIterator());
    }

    private ExecutionFlow<HttpResponse<?>> filterRequest(FilterContext context,
                                                         ListIterator<InternalHttpFilter> iterator) {
        return filterRequest0(context, iterator)
            .flatMap(newContext -> {
                if (newContext.response() != null) {
                    return filterResponse(newContext, iterator, null);
                }
                return ExecutionFlow.error(new IllegalStateException("Request filters didn't produce any response!"));
            });
    }

    private ExecutionFlow<FilterContext> filterRequest0(FilterContext context,
                                                        ListIterator<InternalHttpFilter> iterator) {
        if (context.response() != null) {
            return ExecutionFlow.just(context);
        }
        if (iterator.hasNext()) {
            InternalHttpFilter filter = iterator.next();
            return (filter.isFiltersRequest() ? filter.processRequestFilter(context, newContext -> filterRequest0(newContext, iterator)) : filterRequest0(context, iterator))
                .onErrorResume(throwable -> {
                    return processFailure(context.request(), throwable, context.propagatedContext()).map(context::withResponse)
                        .onErrorResume(throwable2 -> {
                            // Exception filtering scenario of the http client
                            return filterResponse(context, iterator, throwable2).map(context::withResponse);
                        });
                });
        } else {
            return ExecutionFlow.just(context);
        }
    }

    private ExecutionFlow<HttpResponse<?>> filterResponse(FilterContext context,
                                                          ListIterator<InternalHttpFilter> iterator,
                                                          @Nullable
                                                          Throwable exception) {
        if (iterator.hasPrevious()) {
            // Walk backwards and execute response filters
            InternalHttpFilter filter = iterator.previous();
            return (filter.isFiltersResponse() ? filter.processResponseFilter(context, exception) : ExecutionFlow.just(context))
                .flatMap(newContext -> {
                    if (context != newContext) {
                        return processResponse(newContext.request(), newContext.response(), newContext.propagatedContext()).map(newContext::withResponse);
                    }
                    return ExecutionFlow.just(newContext);
                })
                .onErrorResume(throwable -> processFailure(context.request(), throwable, context.propagatedContext()).map(context::withResponse))
                .flatMap(newContext -> filterResponse(newContext, iterator, newContext.response() == null ? exception : null));
        } else if (context.response() != null) {
            return ExecutionFlow.just(context.response());
        } else if (exception != null) {
            // This scenario only applies for client filters
            // Filters didn't remap the exception to any response
            return ExecutionFlow.error(exception);
        } else {
            return ExecutionFlow.error(new IllegalStateException("No response after response filters completed!"));
        }
    }

}
