package io.micronaut.reactive.rxjava2;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.scheduling.instrument.ReactiveInstrumenter;
import io.micronaut.scheduling.instrument.RunnableInstrumenter;
import org.reactivestreams.Subscription;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Helper methods for instrumented components.
 *
 * @author graemerocher
 * @since 1.1
 */
@Internal
interface InstrumentedComponent {

    /**
     * Validate the throwable.
     * @param t The throwable
     */
    void onStateError(Throwable t);

    /**
     * Validates the subscriptions.
     *
     * @param upstream The upstream
     * @param subscription The downstream
     * @return True if they are valid
     */
    boolean validate(Subscription upstream, Subscription subscription);

    /**
     * Convert reactive to runnable instrumenters.
     * @param instrumentations The instrumentations
     * @return The runnable instrumenters
     */
    default List<RunnableInstrumenter> toRunnableInstrumenters(Collection<ReactiveInstrumenter> instrumentations) {
        if (CollectionUtils.isEmpty(instrumentations)) {
            return Collections.emptyList();
        }
        return instrumentations.stream()
                .map(ReactiveInstrumenter::newInstrumentation)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }
}
