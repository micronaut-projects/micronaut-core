/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.scheduling.instrument;

import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.core.annotation.Internal;

import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadFactory;

/**
 * Wraps {@link ThreadFactory} to instrument {@link Runnable}.
 *
 * @author Denis Stepanov
 * @since 1.3
 */
@Singleton
@Internal
final class ThreadFactoryInstrumenter implements BeanCreatedEventListener<ThreadFactory> {

    private final List<InvocationInstrumenterFactory> invocationInstrumenterFactories;

    /**
     * Creates new instance.
     *
     * @param invocationInstrumenterFactories invocation instrumentation factories
     */
    ThreadFactoryInstrumenter(List<InvocationInstrumenterFactory> invocationInstrumenterFactories) {
        this.invocationInstrumenterFactories = invocationInstrumenterFactories;
    }

    /**
     * Wraps {@link ThreadFactory}.
     *
     * @param event The bean created event
     * @return wrapped instance
     */
    @Override
    public ThreadFactory onCreated(BeanCreatedEvent<ThreadFactory> event) {
        Class<ThreadFactory> beanType = event.getBeanDefinition().getBeanType();
        // Don't wrap sub interfaces for ThreadFactory
        if (beanType == ThreadFactory.class) {
            if (invocationInstrumenterFactories.isEmpty()) {
                return event.getBean();
            }
            ThreadFactory original = event.getBean();
            return r -> original.newThread(instrument(r));
        } else {
            return event.getBean();
        }
    }

    private Runnable instrument(Runnable runnable) {
        return InvocationInstrumenter.instrument(runnable, getInvocationInstrumenterList());
    }

    private List<InvocationInstrumenter> getInvocationInstrumenterList() {
        List<InvocationInstrumenter> instrumenters = new ArrayList<>(invocationInstrumenterFactories.size());
        for (InvocationInstrumenterFactory instrumenterFactory : invocationInstrumenterFactories) {
            final InvocationInstrumenter instrumenter = instrumenterFactory.newInvocationInstrumenter();
            if (instrumenter != null) {
                instrumenters.add(instrumenter);
            }
        }
        return instrumenters;
    }
}
