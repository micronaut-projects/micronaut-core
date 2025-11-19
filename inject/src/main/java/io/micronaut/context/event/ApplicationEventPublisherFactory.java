/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.context.event;

import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.exceptions.BeanInstantiationException;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Indexes;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.ArgumentCoercible;
import io.micronaut.core.util.SupplierUtil;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanDefinitionReference;
import io.micronaut.inject.InjectionPoint;
import io.micronaut.inject.InstantiatableBeanDefinition;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.function.Supplier;

/**
 * Constructs instances of {@link io.micronaut.context.event.ApplicationEventPublisher}.
 *
 * @param <T> The generic type
 * @author graemerocher
 * @author Denis
 */
@Internal
public final class ApplicationEventPublisherFactory<T>
        implements InstantiatableBeanDefinition<ApplicationEventPublisher<T>>,
                   BeanDefinitionReference<ApplicationEventPublisher<T>> {

    private static final Argument<Object> TYPE_VARIABLE = Argument.ofTypeVariable(Object.class, "T");
    private final AnnotationMetadata annotationMetadata;
    private ApplicationEventPublisher applicationObjectEventPublisher;
    private final Map<Argument, Supplier<ApplicationEventPublisher>> publishers = new ConcurrentHashMap<>();
    private Supplier<Executor> executorSupplier;

    public ApplicationEventPublisherFactory() {
        MutableAnnotationMetadata metadata = new MutableAnnotationMetadata();
        metadata.addDeclaredAnnotation(BootstrapContextCompatible.class.getName(), Collections.emptyMap());
        try {
            metadata.addDeclaredAnnotation(Indexes.class.getName(), Collections.singletonMap(AnnotationMetadata.VALUE_MEMBER, getBeanType()));
        } catch (NoClassDefFoundError e) {
            // ignore, might happen if jakarta.inject is not the classpath
        }
        annotationMetadata = metadata;
    }

    @Override
    public int getOrder() {
        return 0;
    }

    @Override
    public boolean isParallel() {
        return false;
    }

    @Override
    public boolean isPrimary() {
        return false;
    }

    @Override
    public @NonNull Class<?>[] getIndexes() {
        return new Class<?>[]{getBeanType()};
    }

    @Override
    public Set<Class<?>> getExposedTypes() {
        return Set.of(getBeanType());
    }

    @Override
    public boolean isAbstract() {
        return false;
    }

    @Override
    public boolean isCandidateBean(Argument<?> beanType) {
        return beanType.isAssignableFrom(ApplicationEventPublisher.class);
    }

    @Override
    public boolean isConfigurationProperties() {
        return false;
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return annotationMetadata;
    }

    @Override
    public boolean isContainerType() {
        return false;
    }

    @Override
    public boolean isEnabled(BeanContext context, BeanResolutionContext resolutionContext) {
        return true;
    }

    @Override
    public boolean isSingleton() {
        return false;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public Class<ApplicationEventPublisher<T>> getBeanType() {
        return (Class) ApplicationEventPublisher.class;
    }

    @Override
    public String getBeanDefinitionName() {
        return getClass().getName();
    }

    @Override
    public BeanDefinition<ApplicationEventPublisher<T>> load() {
        return this;
    }

    @Override
    public boolean isPresent() {
        return true;
    }

    @Override
    public ApplicationEventPublisher<T> instantiate(BeanResolutionContext resolutionContext, BeanContext context) throws BeanInstantiationException {
        if (executorSupplier == null) {
            executorSupplier = SupplierUtil.memoized(() ->
                 context.findBean(Executor.class, Qualifiers.byName("scheduled")).orElseGet(ForkJoinPool::commonPool)
            );
        }
        Argument<?> eventType = Argument.OBJECT_ARGUMENT;
        final BeanResolutionContext.Segment<?, ?> segment = resolutionContext.getPath().currentSegment().orElse(null);
        if (segment != null) {
            final InjectionPoint<?> injectionPoint = segment.getInjectionPoint();
            if (injectionPoint instanceof ArgumentCoercible coercible) {
                Argument<?> injectionPointArgument = coercible
                        .asArgument();

                eventType =
                        injectionPointArgument
                                .getFirstTypeVariable()
                                .orElse(Argument.OBJECT_ARGUMENT);

            }
        }
        if (eventType.getType().equals(Object.class)) {
            if (applicationObjectEventPublisher == null) {
                applicationObjectEventPublisher = createObjectEventPublisher(context);
            }
            return applicationObjectEventPublisher;
        }
        return getTypedEventPublisher(eventType, context);
    }

    @Override
    @NonNull
    public List<Argument<?>> getTypeArguments(Class<?> type) {
        if (type == getBeanType()) {
            return getTypeArguments();
        }
        return Collections.emptyList();
    }

    @Override
    @NonNull
    public List<Argument<?>> getTypeArguments() {
        return Collections.singletonList(TYPE_VARIABLE);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o != null && getClass() == o.getClass();
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    private ApplicationEventPublisher<Object> createObjectEventPublisher(BeanContext beanContext) {
        return new ApplicationEventPublisher<>() {
            @Override
            public void publishEvent(Object event) {
                getTypedEventPublisher(Argument.of(event.getClass()), beanContext).publishEvent(event);
            }

            @Override
            public Future<Void> publishEventAsync(Object event) {
                return getTypedEventPublisher(Argument.of(event.getClass()), beanContext).publishEventAsync(event);
            }
        };
    }

    private ApplicationEventPublisher getTypedEventPublisher(Argument eventType, BeanContext beanContext) {
        return publishers.computeIfAbsent(eventType, argument -> SupplierUtil.memoized(() -> createEventPublisher(argument, beanContext))).get();
    }

    private ApplicationEventPublisher<Object> createEventPublisher(Argument<?> eventType, BeanContext beanContext) {
        return new ApplicationEventPublisher<>() {

            private final Supplier<ApplicationEventListener[]> lazyListeners = SupplierUtil.memoized(() -> beanContext.getBeansOfType(ApplicationEventListener.class, Qualifiers.byTypeArguments(eventType.getType()))
                .stream()
                .sorted(OrderUtil.COMPARATOR_ZERO)
                .toArray(ApplicationEventListener[]::new));

            @Override
            public void publishEvent(Object event) {
                if (event != null) {
                    if (EventLogger.LOG.isDebugEnabled()) {
                        EventLogger.LOG.debug("Publishing event: {}", event);
                    }
                    notifyEventListeners(event, lazyListeners.get());
                }
            }

            @Override
            public Future<Void> publishEventAsync(Object event) {
                Objects.requireNonNull(event, "Event cannot be null");
                CompletableFuture<Void> future = new CompletableFuture<>();
                ApplicationEventListener[] eventListeners = lazyListeners.get();
                executorSupplier.get().execute(() -> {
                    try {
                        notifyEventListeners(event, eventListeners);
                        future.complete(null);
                    } catch (Exception e) {
                        future.completeExceptionally(e);
                    }
                });
                return future;
            }

            @Override
            public boolean isEmpty() {
                return lazyListeners.get().length == 0;
            }
        };
    }

    private void notifyEventListeners(@NonNull Object event, ApplicationEventListener[] eventListeners) {
        if (eventListeners.length == 0) {
            return;
        }
        if (EventLogger.LOG.isTraceEnabled()) {
            EventLogger.LOG.trace("Established event listeners {} for event: {}", eventListeners, event);
        }
        for (ApplicationEventListener listener : eventListeners) {
            if (listener.supports(event)) {
                try {
                    if (EventLogger.LOG.isTraceEnabled()) {
                        EventLogger.LOG.trace("Invoking event listener [{}] for event: {}", listener, event);
                    }
                    listener.onApplicationEvent(event);
                } catch (ClassCastException ex) {
                    String msg = ex.getMessage();
                    if (msg == null || msg.startsWith(event.getClass().getName())) {
                        if (EventLogger.LOG.isDebugEnabled()) {
                            EventLogger.LOG.debug("Incompatible listener for event: {}", listener, ex);
                        }
                    } else {
                        throw ex;
                    }
                }
            }
        }
    }

    private static final class EventLogger {
        private static final Logger LOG = LoggerFactory.getLogger(ApplicationEventPublisher.class);

        private EventLogger() {
        }
    }
}
