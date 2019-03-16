/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
