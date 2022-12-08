package io.micronaut.http.filter;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.type.Executable;
import io.micronaut.core.util.Toggleable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import org.reactivestreams.Publisher;
import reactor.util.context.Context;

import java.util.concurrent.Executor;

/**
 * Base interface for different filter types. Note that while the base interface is exposed, so you
 * can pass around instances of these filters, the different implementations are internal only.
 * Only the framework should construct or call instances of this interface. The exception is the
 * {@link Terminal terminal filter}.
 */
public sealed interface InternalFilter {
    @Internal
    record Before<T>(
        T bean,
        Executable<T, ?> method,
        FilterOrder order
    ) implements InternalFilter, Ordered {
        @Override
        public int getOrder() {
            return order.getOrder(bean);
        }
    }

    @Internal
    record After<T>(
        T bean,
        Executable<T, ?> method,
        FilterOrder order
    ) implements InternalFilter, Ordered {
        @Override
        public int getOrder() {
            return order.getOrder(bean);
        }
    }

    @Internal
    record Async(
        InternalFilter actual,
        Executor executor
    ) implements InternalFilter, Ordered {
        @Override
        public int getOrder() {
            return ((Ordered) actual).getOrder();
        }
    }

    @Internal
    record AroundLegacy(
        HttpFilter bean,
        FilterOrder order
    ) implements InternalFilter, Ordered {
        public boolean isEnabled() {
            return !(bean instanceof Toggleable t) || t.isEnabled();
        }

        @Override
        public int getOrder() {
            return order.getOrder(bean);
        }
    }

    @Internal
    record TerminalReactive(Publisher<? extends HttpResponse<?>> responsePublisher) implements InternalFilter {}

    @Internal
    @FunctionalInterface
    non-sealed interface TerminalWithReactorContext extends InternalFilter {
        ExecutionFlow<? extends HttpResponse<?>> execute(HttpRequest<?> request, Context context) throws Exception;
    }

    /**
     * Last item in a filter chain, called when all other filters are done. Basically, this runs
     * the actual request.
     */
    @FunctionalInterface
    non-sealed interface Terminal extends InternalFilter {
        ExecutionFlow<? extends HttpResponse<?>> execute(HttpRequest<?> request) throws Exception;
    }
}
