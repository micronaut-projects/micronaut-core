/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.runtime.event;

import io.micronaut.context.BeanLocator;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.SupplierUtil;
import io.micronaut.inject.ArgumentInjectionPoint;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.function.Supplier;

/**
 * The factory of {@link ApplicationEventPublisher}.
 *
 * @author Denis Stepanov
 * @since 3.0
 */
@Internal
@Factory
final class ApplicationEventPublisherFactory {

    private static final Logger EVENT_LOGGER = LoggerFactory.getLogger(ApplicationEventPublisher.class);

    private final BeanLocator beanLocator;
    private final Supplier<Executor> executorSupplier = SupplierUtil.memoized(new Supplier<Executor>() {
        @Override
        public Executor get() {
            return beanLocator.findBean(Executor.class, Qualifiers.byName("scheduled")).orElseGet(ForkJoinPool::commonPool);
        }
    });
    private final Map<Argument, Supplier<ApplicationEventPublisher>> publishers = new ConcurrentHashMap<>();
    private ApplicationEventPublisher applicationObjectEventPublisher;

    ApplicationEventPublisherFactory(BeanLocator beanLocator) {
        this.beanLocator = beanLocator;
    }

    @Prototype
    public <T> ApplicationEventPublisher<T> build(@Nullable ArgumentInjectionPoint<?, ?> argumentInjectionPoint) {
        Argument eventType = Argument.OBJECT_ARGUMENT;
        if (argumentInjectionPoint != null) {
            Argument argument = argumentInjectionPoint.asArgument();
            Argument[] typeParameters = argument.getTypeParameters();
            if (typeParameters.length != 0) {
                eventType = typeParameters[0];
            }
        }
        if (eventType.getType().equals(Object.class)) {
            if (applicationObjectEventPublisher == null) {
                applicationObjectEventPublisher = createObjectEventPublisher();
            }
            return applicationObjectEventPublisher;
        }
        return getTypedEventPublisher(eventType);
    }

    private ApplicationEventPublisher<Object> createObjectEventPublisher() {
        return new ApplicationEventPublisher<Object>() {
            @Override
            public void publishEvent(Object event) {
                getTypedEventPublisher(Argument.of(event.getClass())).publishEvent(event);
            }

            @Override
            public Future<Void> publishEventAsync(Object event) {
                return getTypedEventPublisher(Argument.of(event.getClass())).publishEventAsync(event);
            }
        };
    }

    private ApplicationEventPublisher getTypedEventPublisher(Argument eventType) {
        return publishers.computeIfAbsent(eventType, argument -> SupplierUtil.memoized(() -> createEventPublisher(argument))).get();
    }

    private ApplicationEventPublisher<Object> createEventPublisher(Argument<?> eventType) {
        return new ApplicationEventPublisher<Object>() {

            private final Supplier<List<ApplicationEventListener>> lazyListeners = SupplierUtil.memoizedNonEmpty(() -> {
                List<ApplicationEventListener> listeners = new ArrayList<>(
                        beanLocator.getBeansOfType(ApplicationEventListener.class, Qualifiers.byTypeArguments(eventType.getType()))
                );
                listeners.sort(OrderUtil.COMPARATOR);
                return listeners;
            });

            @Override
            public void publishEvent(Object event) {
                if (event != null) {
                    if (EVENT_LOGGER.isDebugEnabled()) {
                        EVENT_LOGGER.debug("Publishing event: {}", event);
                    }
                    notifyEventListeners(event, lazyListeners.get());
                }
            }

            @Override
            public Future<Void> publishEventAsync(Object event) {
                Objects.requireNonNull(event, "Event cannot be null");
                CompletableFuture<Void> future = new CompletableFuture<>();
                executorSupplier.get().execute(() -> {
                    try {
                        notifyEventListeners(event, lazyListeners.get());
                        future.complete(null);
                    } catch (Exception e) {
                        future.completeExceptionally(e);
                    }
                });
                return future;
            }
        };
    }

    private void notifyEventListeners(@NonNull Object event, Collection<ApplicationEventListener> eventListeners) {
        if (!eventListeners.isEmpty()) {
            if (EVENT_LOGGER.isTraceEnabled()) {
                EVENT_LOGGER.trace("Established event listeners {} for event: {}", eventListeners, event);
            }
            for (ApplicationEventListener listener : eventListeners) {
                if (listener.supports(event)) {
                    try {
                        if (EVENT_LOGGER.isTraceEnabled()) {
                            EVENT_LOGGER.trace("Invoking event listener [{}] for event: {}", listener, event);
                        }
                        listener.onApplicationEvent(event);
                    } catch (ClassCastException ex) {
                        String msg = ex.getMessage();
                        if (msg == null || msg.startsWith(event.getClass().getName())) {
                            if (EVENT_LOGGER.isDebugEnabled()) {
                                EVENT_LOGGER.debug("Incompatible listener for event: " + listener, ex);
                            }
                        } else {
                            throw ex;
                        }
                    }
                }

            }
        }
    }

}
