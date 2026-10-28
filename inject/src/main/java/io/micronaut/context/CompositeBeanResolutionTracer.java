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
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanDefinitionReference;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Hands every callback to several tracers in order. Every tracer receives each callback, even when another
 * tracer throws: the failures are rethrown once all of them received it, the first with the others suppressed.
 *
 * @since 5.3.0
 */
@SuppressWarnings("java:S1181") // every tracer must receive its callback, whatever another tracer throws
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
    static void discover(@Nullable ClassLoader classLoader, BeanResolutionTracerProvider.Activation activation, List<BeanResolutionTracer> tracers) {
        List<BeanResolutionTracerProvider> providers = SoftServiceLoader.load(BeanResolutionTracerProvider.class, classLoader)
            .collectAll(provider -> provider.isEnabled(activation));
        if (providers.isEmpty()) {
            return;
        }
        // the providers may be collected in parallel: their order decides the order of the callbacks
        List<BeanResolutionTracerProvider> ordered = new ArrayList<>(providers);
        OrderUtil.sort(ordered);
        for (BeanResolutionTracerProvider provider : ordered) {
            tracers.add(provider.createTracer(activation));
        }
    }

    /**
     * What the activation of discovered tracers is based on.
     *
     * @param environmentNames The active environment names
     * @param discoveryEnabled Whether discovery was enabled
     */
    record DefaultActivation(Set<String> environmentNames, boolean discoveryEnabled) implements BeanResolutionTracerProvider.Activation {

        @Override
        public Set<String> getEnvironmentNames() {
            return environmentNames;
        }

        @Override
        public boolean isDiscoveryEnabled() {
            return discoveryEnabled;
        }
    }

    @Override
    public void traceInitialConfiguration(Environment environment, Collection<BeanDefinitionReference<Object>> beanReferences, Collection<DisabledBean<?>> disabledBeans) {
        Throwable failure = null;
        for (BeanResolutionTracer tracer : tracers) {
            try {
                tracer.traceInitialConfiguration(environment, beanReferences, disabledBeans);
            } catch (RuntimeException | Error e) {
                failure = collect(failure, e);
            }
        }
        rethrow(failure);
    }

    @Override
    public void traceBeanCreation(Resolution resolution, BeanDefinition<?> beanDefinition, Argument<?> beanType) {
        Throwable failure = null;
        for (BeanResolutionTracer tracer : tracers) {
            try {
                tracer.traceBeanCreation(resolution, beanDefinition, beanType);
            } catch (RuntimeException | Error e) {
                failure = collect(failure, e);
            }
        }
        rethrow(failure);
    }

    @Override
    public <T> void traceBeanResolved(Resolution resolution, Argument<T> beanType, @Nullable Qualifier<T> qualifier, @Nullable T bean) {
        Throwable failure = null;
        for (BeanResolutionTracer tracer : tracers) {
            try {
                tracer.traceBeanResolved(resolution, beanType, qualifier, bean);
            } catch (RuntimeException | Error e) {
                failure = collect(failure, e);
            }
        }
        rethrow(failure);
    }

    @Override
    public <T> void traceBeanDisabled(Resolution resolution, Argument<T> beanType, @Nullable Qualifier<T> qualifier, String disabledBeanMessage) {
        Throwable failure = null;
        for (BeanResolutionTracer tracer : tracers) {
            try {
                tracer.traceBeanDisabled(resolution, beanType, qualifier, disabledBeanMessage);
            } catch (RuntimeException | Error e) {
                failure = collect(failure, e);
            }
        }
        rethrow(failure);
    }

    @Override
    public <T> void traceValueResolved(Resolution resolution, Argument<T> argument, String property, @Nullable T value) {
        Throwable failure = null;
        for (BeanResolutionTracer tracer : tracers) {
            try {
                tracer.traceValueResolved(resolution, argument, property, value);
            } catch (RuntimeException | Error e) {
                failure = collect(failure, e);
            }
        }
        rethrow(failure);
    }

    @Override
    public void traceBeanCreated(Resolution resolution, BeanDefinition<?> beanDefinition) {
        Throwable failure = null;
        // in reverse, so that the tracers nest like the creations they trace
        for (int i = tracers.length - 1; i >= 0; i--) {
            try {
                tracers[i].traceBeanCreated(resolution, beanDefinition);
            } catch (RuntimeException | Error e) {
                failure = collect(failure, e);
            }
        }
        rethrow(failure);
    }

    @Override
    public void traceBeanCreationFailed(Resolution resolution, BeanDefinition<?> beanDefinition, Throwable failure) {
        Throwable tracerFailure = null;
        for (int i = tracers.length - 1; i >= 0; i--) {
            try {
                tracers[i].traceBeanCreationFailed(resolution, beanDefinition, failure);
            } catch (RuntimeException | Error e) {
                tracerFailure = collect(tracerFailure, e);
            }
        }
        rethrow(tracerFailure);
    }

    @Override
    public void traceInjectBean(Resolution resolution, Injection injection) {
        Throwable failure = null;
        for (BeanResolutionTracer tracer : tracers) {
            try {
                tracer.traceInjectBean(resolution, injection);
            } catch (RuntimeException | Error e) {
                failure = collect(failure, e);
            }
        }
        rethrow(failure);
    }

    @Override
    public void traceInjectComplete(Resolution resolution, Injection injection) {
        Throwable failure = null;
        for (int i = tracers.length - 1; i >= 0; i--) {
            try {
                tracers[i].traceInjectComplete(resolution, injection);
            } catch (RuntimeException | Error e) {
                failure = collect(failure, e);
            }
        }
        rethrow(failure);
    }

    @Override
    public void traceContextShutdown(BeanContext beanContext) {
        Throwable failure = null;
        for (BeanResolutionTracer tracer : tracers) {
            try {
                tracer.traceContextShutdown(beanContext);
            } catch (RuntimeException | Error e) {
                failure = collect(failure, e);
            }
        }
        rethrow(failure);
    }

    /**
     * Collects the failure of a tracer, so that the other tracers still receive the callback.
     *
     * @param failure The failure collected so far, if any
     * @param next The failure of a tracer
     * @return The first failure, the others suppressed in it
     */
    private static Throwable collect(@Nullable Throwable failure, Throwable next) {
        if (failure == null) {
            return next;
        }
        if (failure != next) {
            failure.addSuppressed(next);
        }
        return failure;
    }

    /**
     * Rethrows the failures collected once every tracer received the callback.
     *
     * @param failure The first failure, if any
     */
    private static void rethrow(@Nullable Throwable failure) {
        if (failure instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (failure instanceof Error error) {
            throw error;
        }
    }
}
