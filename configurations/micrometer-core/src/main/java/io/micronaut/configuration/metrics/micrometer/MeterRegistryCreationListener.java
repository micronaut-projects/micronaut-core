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

package io.micronaut.configuration.metrics.micrometer;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micronaut.configuration.metrics.aggregator.MeterRegistryConfigurer;
import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;

import javax.inject.Singleton;

/**
 * Bean used to configure meter registries.  This bean is required to compose any sub type
 * of registries that are created, like statsd, graphite, etc.
 *
 * @author Christian Oestreich
 * @since 1.0
 */
@Singleton
public class MeterRegistryCreationListener implements BeanCreatedEventListener<MeterRegistry> {

    @Override
    public MeterRegistry onCreated(BeanCreatedEvent<MeterRegistry> event) {
        MeterRegistry meterRegistry = event.getBean();
        BeanContext ctx = event.getSource();
        if (!(meterRegistry instanceof CompositeMeterRegistry)) {
            ctx.streamOfType(MeterRegistryConfigurer.class).forEach(meterRegistryConfigurer -> {
                if (meterRegistryConfigurer.supports(meterRegistry)) {
                    meterRegistryConfigurer.configure(meterRegistry);
                }
            });

            ctx.findBean(CompositeMeterRegistry.class)
                    .ifPresent(compositeMeterRegistry -> compositeMeterRegistry.add(meterRegistry));
        }

        return meterRegistry;
    }
}
