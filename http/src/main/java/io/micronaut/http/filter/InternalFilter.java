package io.micronaut.http.filter;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.util.Toggleable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.inject.ExecutableMethod;
import org.reactivestreams.Publisher;

import java.util.function.Supplier;

@Internal
public sealed interface InternalFilter {
    record Before<T>(
        T bean,
        ExecutableMethod<T, ?> method
    ) implements InternalFilter {
    }

    record After<T>(
        T bean,
        ExecutableMethod<T, ?> method
    ) implements InternalFilter {
    }

    record AroundLegacy(HttpFilter bean) implements InternalFilter {
        public boolean isEnabled() {
            return !(bean instanceof Toggleable t) || t.isEnabled();
        }
    }

    record TerminalReactive(Publisher<? extends HttpResponse<?>> responsePublisher) implements InternalFilter{}

    record Terminal(Supplier<ExecutionFlow<MutableHttpResponse<?>>> responseFlowSupplier) implements InternalFilter{}
}
