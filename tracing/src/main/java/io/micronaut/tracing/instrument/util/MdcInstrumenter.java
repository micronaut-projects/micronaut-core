package io.micronaut.tracing.instrument.util;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import javax.inject.Singleton;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.scheduling.instrument.ReactiveInstrumenter;
import io.micronaut.scheduling.instrument.RunnableInstrumenter;
import org.slf4j.MDC;

/**
 * A function that instruments an existing Runnable with the Mapped Diagnostic Context for Slf4j.
 *
 * @author graemerocher
 * @author LarsEckart
 * @since 1.1
 */
@Singleton
@Requires(classes = MDC.class)
@Internal
public final class MdcInstrumenter implements Function<Runnable, Runnable>, RunnableInstrumenter, ReactiveInstrumenter {

    @Override
    public Runnable apply(Runnable runnable) {
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        if (contextMap != null && !contextMap.isEmpty()) {
            return passMdcTo(runnable, contextMap);
        } else {
            return runnable;
        }
    }

    @Override
    public Runnable instrument(Runnable command) {
        return apply(command);
    }

    @Override
    public Optional<RunnableInstrumenter> newInstrumentation() {
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        if (contextMap != null && !contextMap.isEmpty()) {
            return Optional.of(new RunnableInstrumenter() {
                @Override
                public Runnable instrument(Runnable runnable) {
                    return passMdcTo(runnable, contextMap);
                }
            });
        }
        return Optional.empty();
    }

    private Runnable passMdcTo(Runnable runnable, Map<String, String> contextMap) {
        return () -> {
            try {
                MDC.setContextMap(contextMap);
                runnable.run();
            } finally {
                MDC.clear();
            }
        };
    }
}
