/*
 * Copyright 2017-2026 original authors
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

import io.micronaut.context.env.Environment;
import io.micronaut.core.io.service.SoftServiceLoader;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanDefinitionReference;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * Hands every callback to several tracers in order.
 *
 * @since 5.3.0
 */
final class CompositeBeanResolutionTracer implements BeanResolutionTracer {

    private final BeanResolutionTracer[] tracers;

    private CompositeBeanResolutionTracer(BeanResolutionTracer[] tracers) {
        this.tracers = tracers;
    }

    /**
     * Combines tracers.
     *
     * @param tracers The tracers
     * @return The tracer that hands its callbacks to all of them, or {@code null} for none
     */
    static @Nullable BeanResolutionTracer of(List<BeanResolutionTracer> tracers) {
        return switch (tracers.size()) {
            case 0 -> null;
            case 1 -> tracers.get(0);
            default -> new CompositeBeanResolutionTracer(tracers.toArray(new BeanResolutionTracer[0]));
        };
    }

    /**
     * Adds the tracers discovered with {@link java.util.ServiceLoader} that activate.
     *
     * @param classLoader The class loader
     * @param activation The activation
     * @param tracers The tracers to add to
     */
    static void discover(@Nullable ClassLoader classLoader, Activation activation, List<BeanResolutionTracer> tracers) {
        SoftServiceLoader.load(BeanResolutionTracer.class, classLoader)
            .collectAll(tracers, tracer -> tracer.isEnabled(activation));
    }

    @Override
    public void traceInitialConfiguration(Environment environment, Collection<BeanDefinitionReference<Object>> beanReferences, Collection<DisabledBean<?>> disabledBeans) {
        for (BeanResolutionTracer tracer : tracers) {
            tracer.traceInitialConfiguration(environment, beanReferences, disabledBeans);
        }
    }

    @Override
    public void traceBeanCreation(Resolution resolution, BeanDefinition<?> beanDefinition, Argument<?> beanType) {
        for (BeanResolutionTracer tracer : tracers) {
            tracer.traceBeanCreation(resolution, beanDefinition, beanType);
        }
    }

    @Override
    public <T> void traceBeanResolved(Resolution resolution, Argument<T> beanType, @Nullable Qualifier<T> qualifier, @Nullable T bean) {
        for (BeanResolutionTracer tracer : tracers) {
            tracer.traceBeanResolved(resolution, beanType, qualifier, bean);
        }
    }

    @Override
    public <T> void traceBeanDisabled(Resolution resolution, Argument<T> beanType, @Nullable Qualifier<T> qualifier, String disabledBeanMessage) {
        for (BeanResolutionTracer tracer : tracers) {
            tracer.traceBeanDisabled(resolution, beanType, qualifier, disabledBeanMessage);
        }
    }

    @Override
    public <T> void traceValueResolved(Resolution resolution, Argument<T> argument, String property, @Nullable T value) {
        for (BeanResolutionTracer tracer : tracers) {
            tracer.traceValueResolved(resolution, argument, property, value);
        }
    }

    @Override
    public <T> void traceBeanCreated(Resolution resolution, BeanDefinition<T> beanDefinition) {
        // in reverse, so that the tracers nest like the creations they trace
        for (int i = tracers.length - 1; i >= 0; i--) {
            tracers[i].traceBeanCreated(resolution, beanDefinition);
        }
    }

    @Override
    public <T> void traceBeanCreationFailed(Resolution resolution, BeanDefinition<T> beanDefinition, Throwable failure) {
        for (int i = tracers.length - 1; i >= 0; i--) {
            tracers[i].traceBeanCreationFailed(resolution, beanDefinition, failure);
        }
    }

    @Override
    public void traceInjectBean(Resolution resolution, Injection injection) {
        for (BeanResolutionTracer tracer : tracers) {
            tracer.traceInjectBean(resolution, injection);
        }
    }

    @Override
    public void traceInjectComplete(Resolution resolution, Injection injection) {
        for (int i = tracers.length - 1; i >= 0; i--) {
            tracers[i].traceInjectComplete(resolution, injection);
        }
    }

    @Override
    public void traceContextShutdown(BeanContext beanContext) {
        for (BeanResolutionTracer tracer : tracers) {
            tracer.traceContextShutdown(beanContext);
        }
    }
}
