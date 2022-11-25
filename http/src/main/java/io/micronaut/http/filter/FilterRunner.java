package io.micronaut.http.filter;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.reactive.execution.ReactiveExecutionFlow;
import org.reactivestreams.Publisher;

import java.util.List;

@Internal
public class FilterRunner {
    private final List<InternalFilter> filters;

    public FilterRunner(List<InternalFilter> filters) {
        this.filters = filters;
    }

    public static void sort(List<InternalFilter> filters, boolean reverse) {
        // todo
    }

    protected ExecutionFlow<? extends HttpResponse<?>> postProcess(HttpRequest<?> request, ExecutionFlow<? extends HttpResponse<?>> flow) {
        return flow;
    }

    public final ExecutionFlow<? extends HttpResponse<?>> run(HttpRequest<?> request) {
        return run(request, 0);
    }

    private ExecutionFlow<? extends HttpResponse<?>> run(HttpRequest<?> request, int index) {
        // todo: make this iterative
        InternalFilter filter = filters.get(index);
        if (filter instanceof InternalFilter.Before<?> before) {
            return postProcess(request, invoke(before, request).flatMap(r -> run(r, index + 1)));
        } else if (filter instanceof InternalFilter.After<?> after) {
            // todo: in an iterative version, the invoke call should probably get the last version
            //  of the request
            return postProcess(request, run(request, index + 1).flatMap(r -> invoke(after, request, r)));
        } else if (filter instanceof InternalFilter.AroundLegacy around) {
            Publisher<? extends HttpResponse<?>> result = around.bean().doFilter(request, (ClientFilterChain) r -> ReactiveExecutionFlow.toPublisher(() -> run(r, index + 1)));
            return postProcess(request, ReactiveExecutionFlow.fromPublisher(result));
        } else if (filter instanceof InternalFilter.TerminalReactive tr) {
            return postProcess(request, ReactiveExecutionFlow.fromPublisher(tr.responsePublisher()));
        } else if (filter instanceof InternalFilter.Terminal t) {
            return postProcess(request, t.responseFlowSupplier().get());
        } else {
            throw new IllegalStateException("Unknown filter type");
        }
    }

    private <T> ExecutionFlow<HttpRequest<?>> invoke(InternalFilter.Before<T> before, HttpRequest<?> request) {
        // todo: better param binding, handle return value, errors
        before.method().invoke(before.bean(), request);
        return ExecutionFlow.just(request);
    }

    private <T> ExecutionFlow<HttpResponse<?>> invoke(InternalFilter.After<T> after, HttpRequest<?> request, HttpResponse<?> response) {
        // todo: better param binding, handle return value, errors
        after.method().invoke(after.bean(), request, response);
        return ExecutionFlow.just(response);
    }
}
