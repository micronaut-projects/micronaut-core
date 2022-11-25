package io.micronaut.http.filter;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.type.Executable;
import io.micronaut.core.util.Toggleable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import org.reactivestreams.Publisher;

import java.util.function.Function;

@Internal
public sealed interface InternalFilter {
    record Before<T>(
        T bean,
        Executable<T, ?> method,
        FilterOrder order
    ) implements InternalFilter {
    }

    record After<T>(
        T bean,
        Executable<T, ?> method,
        FilterOrder order
    ) implements InternalFilter {
    }

    record AroundLegacy(
        HttpFilter bean,
        FilterOrder order
    ) implements InternalFilter {
        public boolean isEnabled() {
            return !(bean instanceof Toggleable t) || t.isEnabled();
        }
    }

    record TerminalReactive(Publisher<? extends HttpResponse<?>> responsePublisher) implements InternalFilter {}

    record Terminal(Function<HttpRequest<?>, ExecutionFlow<MutableHttpResponse<?>>> responseFlow) implements InternalFilter {}
}
