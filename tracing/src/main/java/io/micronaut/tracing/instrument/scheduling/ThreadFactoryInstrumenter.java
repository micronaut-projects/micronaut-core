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

package io.micronaut.tracing.instrument.scheduling;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.tracing.instrument.util.TracingRunnable;
import io.opentracing.Tracer;

import javax.inject.Singleton;
import java.util.concurrent.ThreadFactory;

/**
 * Instruments thread creation for {@link Tracer}.
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
@Requires(beans = Tracer.class)
public class ThreadFactoryInstrumenter implements BeanCreatedEventListener<ThreadFactory> {

    private final Tracer tracer;

    /**
     *
     * @param tracer For span creation and propagation across arbitrary transports
     */
    public ThreadFactoryInstrumenter(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public ThreadFactory onCreated(BeanCreatedEvent<ThreadFactory> event) {
        ThreadFactory threadFactory = event.getBean();
        return r ->
            threadFactory.newThread(new TracingRunnable(r, tracer)
        );
    }
}
