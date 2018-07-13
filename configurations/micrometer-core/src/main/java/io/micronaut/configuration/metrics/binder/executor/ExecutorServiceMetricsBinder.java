/*
 * Copyright 2017-2018 original authors
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

package io.micronaut.configuration.metrics.binder.executor;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import io.micrometer.core.instrument.internal.TimedExecutorService;
import io.micronaut.configuration.metrics.annotation.RequiresMetrics;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.inject.BeanIdentifier;
import io.micronaut.scheduling.instrument.InstrumentedExecutorService;

import javax.inject.Singleton;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_BINDERS;

/**
 * Instruments Micronaut related thread pools via Micrometer.
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
@RequiresMetrics
@Requires(property = MICRONAUT_METRICS_BINDERS + ".executor.enabled", value = "true", defaultValue = "true")
public class ExecutorServiceMetricsBinder implements BeanCreatedEventListener<ExecutorService> {

    private final MeterRegistry meterRegistry;

    /**
     * Constructs the default instance
     *
     * @param meterRegistry The meter registry.
     */
    public ExecutorServiceMetricsBinder(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public ExecutorService onCreated(BeanCreatedEvent<ExecutorService> event) {
        ExecutorService executorService = event.getBean();
        BeanIdentifier beanIdentifier = event.getBeanIdentifier();

        List<Tag> tags = Collections.emptyList(); // allow tags?

        // have to unwrap any Micronaut instrumentations to get the target
        ExecutorService unwrapped = executorService;
        while (unwrapped instanceof InstrumentedExecutorService) {
            InstrumentedExecutorService ies = (InstrumentedExecutorService) unwrapped;
            unwrapped = ies.getTarget();
        }

        // bind the service metrics
        new ExecutorServiceMetrics(unwrapped, beanIdentifier.getName(), tags).bindTo(meterRegistry);

        // allow timing
        return new TimedExecutorService(meterRegistry, executorService, beanIdentifier.getName(), tags);
    }
}
