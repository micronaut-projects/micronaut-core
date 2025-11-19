/*
 * Copyright 2017-2022 original authors
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

import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.exceptions.BeanInstantiationException;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.naming.Named;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import io.micronaut.inject.qualifiers.ClosestTypeArgumentQualifier;
import io.micronaut.inject.qualifiers.PrimaryQualifier;
import io.micronaut.inject.qualifiers.TypeArgumentQualifier;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Default implementation of {@link RuntimeBeanDefinition<T>}.
 * @param <T> The bean type
 * @author graemerocher
 * @since 3.6.0
 */
@Experimental
final class DefaultRuntimeBeanDefinition<T> extends AbstractBeanContextConditional implements RuntimeBeanDefinition<T> {
    private static final AtomicInteger REF_COUNT = new AtomicInteger(0);
    private static final String MSG_BEAN_TYPE_CANNOT_BE_NULL = "Bean type cannot be null";
    private final Argument<T> beanType;
    private final Supplier<T> supplier;
    private final AnnotationMetadata annotationMetadata;
    private final String beanName;
    private final Qualifier<T> qualifier;
    private final boolean isSingleton;
    private final Class<? extends Annotation> scope;
    private final Class<?>[] exposedTypes;
    private Map<Class<?>, List<Argument<?>>> typeArguments;
    private int order;

    DefaultRuntimeBeanDefinition(
        @NonNull Argument<T> beanType,
        @NonNull Supplier<T> supplier,
        @Nullable Qualifier<T> qualifier,
        @Nullable AnnotationMetadata annotationMetadata,
        boolean isSingleton,
        @Nullable Class<? extends Annotation> scope,
        Class<?>[] exposedTypes, Map<Class<?>,
        List<Argument<?>>> typeArguments) {
        Objects.requireNonNull(beanType, MSG_BEAN_TYPE_CANNOT_BE_NULL);
        Objects.requireNonNull(supplier, "Bean supplier cannot be null");

        this.beanType = beanType;
        this.supplier = supplier;
        this.beanName = generateBeanName(beanType.getType());
        this.qualifier = qualifier;
        this.annotationMetadata = annotationMetadata == null ? AnnotationMetadata.EMPTY_METADATA : annotationMetadata;
        this.isSingleton = isSingleton;
        this.scope = scope;
        this.exposedTypes = exposedTypes;
        this.typeArguments = typeArguments;
        this.order = annotationMetadata == null ? 0 : OrderUtil.getOrder(this.annotationMetadata);
    }

    @Override
    public int getOrder() {
        return order;
    }

    @Override
    public List<Argument<?>> getTypeArguments(Class<?> type) {
        Class<T> bt = getBeanType();
        if (type == bt) {
            return getTypeArguments();
        }
        if (type != null && type.isAssignableFrom(bt)) {
            if (typeArguments != null) {
                List<Argument<?>> args = typeArguments.get(type);
                if (args != null) {
                    return args;
                }
            }
            List<Argument<?>> list = RuntimeBeanDefinition.super.getTypeArguments(type);
            if (CollectionUtils.isNotEmpty(list)) {
                if (typeArguments == null) {
                    synchronized (this.beanType) {
                        typeArguments = new LinkedHashMap<>(3);
                    }
                }
                typeArguments.put(type, list);
            }
            return list;
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public boolean isAbstract() {
        return false;
    }

    @Override
    @NonNull
    public Set<Class<?>> getExposedTypes() {
        return ArrayUtils.isNotEmpty(exposedTypes) ?
            CollectionUtils.setOf(exposedTypes) :
            RuntimeBeanDefinition.super.getExposedTypes();
    }

    @Override
    public Optional<Class<? extends Annotation>> getScope() {
        return Optional.ofNullable(scope);
    }

    @Override
    public Optional<String> getScopeName() {
        return getScope().map(Class::getName);
    }

    @Override
    @NonNull
    public Argument<T> asArgument() {
        return beanType;
    }

    @Override
    public boolean isPrimary() {
        return qualifier == PrimaryQualifier.INSTANCE || RuntimeBeanDefinition.super.isPrimary();
    }

    @Override
    public Qualifier<T> getDeclaredQualifier() {
        return this.qualifier != null ? this.qualifier :
            RuntimeBeanDefinition.super.getDeclaredQualifier();
    }

    @Override
    public Qualifier<T> resolveDynamicQualifier() {
        return qualifier;
    }

    /**
     * Generates the bean name for the give ntype.
     * @param beanType The bean type
     * @return The bean name
     */
    static String generateBeanName(@NonNull Class<?> beanType) {
        Objects.requireNonNull(beanType, MSG_BEAN_TYPE_CANNOT_BE_NULL);
        return beanType.getName() + "$DynamicDefinition" + REF_COUNT.incrementAndGet();
    }

    @Override
    public String getBeanDefinitionName() {
        return beanName;
    }

    @Override
    @NonNull
    public AnnotationMetadata getAnnotationMetadata() {
        return annotationMetadata;
    }

    @Override
    public BeanDefinition<T> load() {
        return this;
    }

    @Override
    public boolean isPresent() {
        return true;
    }

    @Override
    public Class<T> getBeanType() {
        return beanType.getType();
    }

    @SuppressWarnings("unchecked")
    @Override
    @NonNull
    public List<Argument<?>> getTypeArguments() {
        return Arrays.asList(beanType.getTypeParameters());
    }

    @Override
    @NonNull
    public Class<?>[] getTypeParameters() {
        return getTypeArguments()
            .stream()
            .map(Argument::getType)
            .toArray(Class[]::new);
    }

    @Override
    public boolean isSingleton() {
        return isSingleton;
    }

    @Override
    public T instantiate(BeanResolutionContext resolutionContext, BeanContext context) throws BeanInstantiationException {
        return supplier.get();
    }

    /**
     * Implementation of {@link RuntimeBeanDefinition.Builder}.
     * @param <B> The bean
     */
    static final class RuntimeBeanBuilder<B> implements RuntimeBeanDefinition.Builder<B> {
        private Argument<B> beanType;
        private final Supplier<B> supplier;
        private Qualifier<B> qualifier;
        private boolean singleton;
        private AnnotationMetadata annotationMetadata;
        private Class<? extends Annotation> scope;
        private Class<?>[] exposedTypes = ReflectionUtils.EMPTY_CLASS_ARRAY;

        private Map<Class<?>, List<Argument<?>>> typeArguments;
        private Class<? extends B> replacesType;

        RuntimeBeanBuilder(Argument<B> beanType, Supplier<B> supplier) {
            this.beanType = Objects.requireNonNull(beanType, MSG_BEAN_TYPE_CANNOT_BE_NULL);
            this.supplier = Objects.requireNonNull(supplier, "Bean supplier cannot be null");
        }

        @Override
        public Builder<B> qualifier(Qualifier<B> qualifier) {
            this.qualifier = qualifier;
            if (qualifier instanceof TypeArgumentQualifier<B> typeArgumentQualifier) {
                Argument<?>[] arguments = Arrays.stream(typeArgumentQualifier.getTypeArguments())
                                                .map(Argument::of)
                                                .toArray(Argument[]::new);
                typeArguments(arguments);
            } else if (qualifier instanceof ClosestTypeArgumentQualifier<B> typeArgumentQualifier) {
                Argument<?>[] arguments = Arrays.stream(typeArgumentQualifier.getTypeArguments())
                                                .map(Argument::of)
                                                .toArray(Argument[]::new);
                typeArguments(arguments);
            }
            return this;
        }

        @Override
        public Builder<B> replaces(Class<? extends B> otherType) {
            this.replacesType = otherType;
            return this;
        }

        @Override
        @SuppressWarnings("java:S1872")
        public Builder<B> scope(Class<? extends Annotation> scope) {
            this.scope = scope;
            if (scope != null && scope.getSimpleName().equals("Singleton")) {
                this.singleton = true;
            }
            return this;
        }

        @Override
        public Builder<B> singleton(boolean isSingleton) {
            this.singleton = true;
            return this;
        }

        @Override
        public Builder<B> exposedTypes(Class<?>... types) {
            for (Class<?> type : types) {
                if (!type.isAssignableFrom(beanType.getType())) {
                    throw new IllegalArgumentException("Bean type doesn't implement: " + type.getName());
                }
            }
            this.exposedTypes = types;
            return this;
        }

        @Override
        public Builder<B> typeArguments(Argument<?>... arguments) {
            this.beanType = Argument.of(beanType.getType(), arguments);
            return this;
        }

        @Override
        public Builder<B> typeArguments(Class<?> implementedType, Argument<?>... arguments) {
            if (typeArguments == null) {
                typeArguments = new LinkedHashMap<>(5);
            }
            typeArguments.put(implementedType, List.of(arguments));
            return this;
        }

        @Override
        public Builder<B> annotationMetadata(AnnotationMetadata annotationMetadata) {
            this.annotationMetadata = annotationMetadata;
            return this;
        }

        @Override
        @NonNull
        public RuntimeBeanDefinition<B> build() {
            if (replacesType != null) {
                MutableAnnotationMetadata mutableAnnotationMetadata;
                if (this.annotationMetadata instanceof MutableAnnotationMetadata mm) {
                    mutableAnnotationMetadata = mm;
                } else if (this.annotationMetadata == null || this.annotationMetadata == EMPTY_METADATA) {
                    mutableAnnotationMetadata = new MutableAnnotationMetadata();
                    this.annotationMetadata = mutableAnnotationMetadata;
                } else {
                    throw new IllegalStateException("Previous non-mutable annotation metadata set");
                }

                Map<CharSequence, Object> values = new HashMap<>(3);
                values.put(AnnotationMetadata.VALUE_MEMBER, new AnnotationClassValue<>(replacesType));
                if (qualifier instanceof Named named) {
                    values.put("named", named.getName());
                }
                mutableAnnotationMetadata.addAnnotation(Replaces.class.getName(), values);
            }
            return new DefaultRuntimeBeanDefinition<>(
                beanType,
                supplier,
                qualifier,
                annotationMetadata,
                singleton,
                scope,
                exposedTypes,
                typeArguments
            );
        }
    }
}

