/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.context;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.naming.NameUtils;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Allows enabling more verbose debugging on bean resolution.
 */
public enum BeanResolutionTraceMode {
    /**
     * No debug enabled (the default).
     */
    NONE(null),

    /**
     * When log mode is enabled output will be
     * written to a logger named {@code io.micronaut.context.resolution} at DEBUG level.
     */
    LOG(new ConsoleBeanResolutionTracer.LoggingBeanResolutionTracer()),

    /**
     * With standard out debug output will be written to {@link System#out} avoiding any log formatting.
     */
    STANDARD_OUT(new ConsoleBeanResolutionTracer.SystemOutBeanResolutionTracer());

    static final Logger LOGGER = LoggerFactory.getLogger("io.micronaut.inject");
    private static final String MODE_SYS_PROP = "micronaut.inject.trace.mode";
    private static final String MODE_ENV_VAR = "MICRONAUT_INJECT_TRACE_MODE";
    private static final String CLASSES_SYS_PROP = "micronaut.inject.trace";
    private static final String CLASSES_ENV_VAR = "MICRONAUT_INJECT_TRACE";

    private final BeanResolutionTracer resolutionTracer;

    BeanResolutionTraceMode(BeanResolutionTracer resolutionTracer) {
        this.resolutionTracer = resolutionTracer;
    }

    /**
     * Obtain the tracer for the mode.
     * @return The tracer.
     */
    Optional<BeanResolutionTracer> getTracer() {
        return Optional.ofNullable(resolutionTracer);
    }

    /**
     * Obtains the default mode.
     *
     * @return The default mode
     */
    @Internal
    static BeanResolutionTraceMode getDefaultMode(Set<String> traceClasses) {
        String mode = Optional
            .ofNullable(System.getProperty(MODE_SYS_PROP))
            .orElseGet(() -> System.getenv(MODE_ENV_VAR));
        if (mode != null) {
            return BeanResolutionTraceMode
                .valueOf(NameUtils.environmentName(mode));
        }
        if (traceClasses.isEmpty()) {
            return LOGGER.isTraceEnabled() ? BeanResolutionTraceMode.LOG : BeanResolutionTraceMode.NONE;
        } else {
            return LOGGER.isTraceEnabled() ? BeanResolutionTraceMode.LOG : BeanResolutionTraceMode.STANDARD_OUT;
        }
    }

    /**
     * The default set of classes to trace.
     * @return The class names
     */
    @Internal
    static @NonNull Set<String> getDefaultTraceClasses() {
        String classes = Optional
            .ofNullable(System.getProperty(CLASSES_SYS_PROP))
            .orElseGet(() -> System.getenv(CLASSES_ENV_VAR));
        if (classes != null) {
            return Set.of(classes.split(","));
        }
        return Set.of();
    }
}
