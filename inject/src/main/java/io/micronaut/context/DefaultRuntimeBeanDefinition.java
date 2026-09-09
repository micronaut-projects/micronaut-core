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
import io.micronaut.context.exceptions.DependencyInjectionException;
import io.micronaut.context.exceptions.NoSuchBeanException;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Experimental;
import org.jspecify.annotations.Nullable;
import io.micronaut.core.naming.Named;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ConstructorInjectionPoint;
import io.micronaut.inject.InjectionPoint;
import io.micronaut.inject.DisposableBeanDefinition;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import io.micronaut.inject.qualifiers.ClosestTypeArgumentQualifier;
import io.micronaut.inject.qualifiers.PrimaryQualifier;
import io.micronaut.inject.qualifiers.TypeArgumentQualifier;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Default implementation of {@link RuntimeBeanDefinition<T>}.
 *
 * <p>A definition built with a disposer is a {@link Disposable}, which is the only subclass, so that a definition
 * built without one is not a {@link DisposableBeanDefinition} and the context never has to dispose of it.</p>
 *
 * @param <T> The bean type
 * @author graemerocher
 * @since 3.6.0
 */
@Experimental
sealed class DefaultRuntimeBeanDefinition<T> extends AbstractBeanContextConditional implements RuntimeBeanDefinition<T> {
    private static final AtomicInteger REF_COUNT = new AtomicInteger(0);
    private static final String MSG_BEAN_TYPE_CANNOT_BE_NULL = "Bean type cannot be null";
    private static final InjectionPointSpec[] NO_INJECTION_POINTS = new InjectionPointSpec[0];
    private final Argument<T> beanType;
    private final Function<RuntimeBeanDefinition.CreationContext, T> beanFactory;
    private final InjectionPointSpec[] injectionPoints;
    private final AnnotationMetadata annotationMetadata;
    private final String beanName;
    @Nullable
    private final Qualifier<T> qualifier;
    private final boolean isSingleton;
    @Nullable
    private final Class<? extends Annotation> scope;
    private final Class<?>[] exposedTypes;
    @Nullable
    private Map<Class<?>, List<Argument<?>>> typeArguments;
    private final int order;

    DefaultRuntimeBeanDefinition(Argument<T> beanType,
                                 Function<RuntimeBeanDefinition.CreationContext, T> beanFactory,
                                 @Nullable Qualifier<T> qualifier,
                                 @Nullable AnnotationMetadata annotationMetadata,
                                 boolean isSingleton,
                                 @Nullable Class<? extends Annotation> scope,
                                 Class<?>[] exposedTypes,
                                 @Nullable
                                 Map<Class<?>, List<Argument<?>>> typeArguments,
                                 InjectionPointSpec[] injectionPoints) {
        Objects.requireNonNull(beanType, MSG_BEAN_TYPE_CANNOT_BE_NULL);
        Objects.requireNonNull(beanFactory, "Bean factory cannot be null");

        this.beanType = beanType;
        this.beanFactory = beanFactory;
        this.injectionPoints = injectionPoints;
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
    public Argument<T> asArgument() {
        return beanType;
    }

    @Override
    public boolean isPrimary() {
        return qualifier == PrimaryQualifier.INSTANCE || RuntimeBeanDefinition.super.isPrimary();
    }

    @Override
    @Nullable
    public Qualifier<T> getDeclaredQualifier() {
        return this.qualifier != null ? this.qualifier :
            RuntimeBeanDefinition.super.getDeclaredQualifier();
    }

    @Override
    @Nullable
    public Qualifier<T> resolveDynamicQualifier() {
        return qualifier;
    }

    /**
     * Generates the bean name for the give ntype.
     * @param beanType The bean type
     * @return The bean name
     */
    static String generateBeanName(Class<?> beanType) {
        Objects.requireNonNull(beanType, MSG_BEAN_TYPE_CANNOT_BE_NULL);
        return beanType.getName() + "$DynamicDefinition" + REF_COUNT.incrementAndGet();
    }

    @Override
    public String getBeanDefinitionName() {
        return beanName;
    }

    @Override
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
    public List<Argument<?>> getTypeArguments() {
        return Arrays.asList(beanType.getTypeParameters());
    }

    @Override
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
    public ConstructorInjectionPoint<T> getConstructor() {
        if (injectionPoints.length == 0) {
            return RuntimeBeanDefinition.super.getConstructor();
        }
        return new RuntimeConstructorInjectionPoint();
    }

    @Override
    public Collection<Class<?>> getRequiredComponents() {
        if (injectionPoints.length == 0) {
            return Collections.emptyList();
        }
        Collection<Class<?>> requiredComponents = new ArrayList<>(injectionPoints.length);
        for (InjectionPointSpec injectionPoint : injectionPoints) {
            requiredComponents.add(injectionPoint.argument().getType());
        }
        return Collections.unmodifiableCollection(requiredComponents);
    }

    @Override
    public T instantiate(BeanResolutionContext resolutionContext, BeanContext context) throws BeanInstantiationException {
        Object[] resolved;
        if (injectionPoints.length == 0) {
            resolved = ArrayUtils.EMPTY_OBJECT_ARRAY;
        } else {
            resolved = new Object[injectionPoints.length];
            for (int i = 0; i < injectionPoints.length; i++) {
                resolved[i] = resolveInjectionPoint(resolutionContext, injectionPoints[i]);
            }
        }
        return beanFactory.apply(new DefaultCreationContext(resolutionContext, resolved));
    }

    /**
     * Resolves a declared injection point through the given resolution context, so that the bean it resolves
     * to becomes a dependent of the bean being created and the failure to resolve it is reported the way it is
     * for a compiled bean's constructor argument.
     *
     * @param resolutionContext The resolution context
     * @param injectionPoint    The declared injection point
     * @return The resolved bean, {@code null} only for a nullable injection point that could not be satisfied
     */
    @Nullable
    private Object resolveInjectionPoint(BeanResolutionContext resolutionContext, InjectionPointSpec injectionPoint) {
        Argument<Object> argument = (Argument<Object>) injectionPoint.argument();
        Qualifier<Object> pointQualifier = (Qualifier<Object>) injectionPoint.qualifier();
        try (BeanResolutionContext.Path ignored = resolutionContext.getPath().pushConstructorResolve(this, argument)) {
            try {
                if (argument.isDeclaredNullable()) {
                    return resolutionContext.findBean(argument, pointQualifier).orElse(null);
                }
                return resolutionContext.getBean(argument, pointQualifier);
            } catch (NoSuchBeanException e) {
                throw new DependencyInjectionException(resolutionContext, e);
            }
        }
    }

    /**
     * Creates the lookup context handed to a disposer that takes one.
     *
     * @param resolutionContext The resolution context of the disposal
     * @return The disposal context
     */
    RuntimeBeanDefinition.DisposalContext newDisposalContext(BeanResolutionContext resolutionContext) {
        return new ResolutionLookupContext(resolutionContext);
    }

    /**
     * A declared injection point of a runtime bean definition.
     *
     * @param argument  The type to inject
     * @param qualifier The qualifier, or {@code null} for none
     */
    record InjectionPointSpec(Argument<?> argument, @Nullable Qualifier<?> qualifier) {
        InjectionPointSpec {
            Objects.requireNonNull(argument, "Injection point type cannot be null");
        }

        boolean matches(Argument<?> otherArgument, @Nullable Qualifier<?> otherQualifier) {
            return argument.equalsType(otherArgument) && Objects.equals(qualifier, otherQualifier);
        }
    }

    /**
     * The constructor injection point of a definition that declared injection points, so that the declared
     * points are described the way a compiled bean's constructor arguments are.
     */
    private final class RuntimeConstructorInjectionPoint implements ConstructorInjectionPoint<T> {

        @Override
        public Argument<?>[] getArguments() {
            Argument<?>[] arguments = new Argument<?>[injectionPoints.length];
            for (int i = 0; i < injectionPoints.length; i++) {
                arguments[i] = injectionPoints[i].argument();
            }
            return arguments;
        }

        @Override
        public BeanDefinition<T> getDeclaringBean() {
            return DefaultRuntimeBeanDefinition.this;
        }

        @Override
        public String toString() {
            return getDeclaringBeanType().getName() + "(" + Argument.toString(getArguments()) + ")";
        }
    }

    /**
     * Implementation of {@link RuntimeBeanDefinition.LookupContext} over the resolution context of one creation
     * or of one disposal.
     *
     * <p>Each lookup is resolved behind a segment of that context's path, so that a lookup made while the bean is
     * being created participates in circularity detection and is reported as a dependency of this definition when
     * it cannot be satisfied. The segment is a constructor argument segment for lack of anything more accurate: an
     * undeclared lookup has no injection point of its own to name.</p>
     */
    private class ResolutionLookupContext implements RuntimeBeanDefinition.DisposalContext {
        protected final BeanResolutionContext resolutionContext;

        ResolutionLookupContext(BeanResolutionContext resolutionContext) {
            this.resolutionContext = resolutionContext;
        }

        @Override
        public BeanContext getBeanContext() {
            return resolutionContext.getContext();
        }

        @Override
        public <V> V getBean(Argument<V> type, @Nullable Qualifier<V> qualifier) {
            Objects.requireNonNull(type, MSG_BEAN_TYPE_CANNOT_BE_NULL);
            try (BeanResolutionContext.Path ignored = resolutionContext.getPath().pushConstructorResolve(DefaultRuntimeBeanDefinition.this, type)) {
                try {
                    return resolutionContext.getBean(type, qualifier);
                } catch (NoSuchBeanException e) {
                    throw new DependencyInjectionException(resolutionContext, e);
                }
            }
        }

        @Override
        public <V> Optional<V> findBean(Argument<V> type, @Nullable Qualifier<V> qualifier) {
            Objects.requireNonNull(type, MSG_BEAN_TYPE_CANNOT_BE_NULL);
            try (BeanResolutionContext.Path ignored = resolutionContext.getPath().pushConstructorResolve(DefaultRuntimeBeanDefinition.this, type)) {
                return resolutionContext.findBean(type, qualifier);
            }
        }

        @Override
        public <V> Collection<V> getBeansOfType(Argument<V> type, @Nullable Qualifier<V> qualifier) {
            Objects.requireNonNull(type, MSG_BEAN_TYPE_CANNOT_BE_NULL);
            try (BeanResolutionContext.Path ignored = resolutionContext.getPath().pushConstructorResolve(DefaultRuntimeBeanDefinition.this, type)) {
                return resolutionContext.getBeansOfType(type, qualifier);
            }
        }
    }

    /**
     * Implementation of {@link RuntimeBeanDefinition.CreationContext} over the resolution context of one
     * creation and the beans resolved for the declared injection points.
     */
    private final class DefaultCreationContext extends ResolutionLookupContext implements RuntimeBeanDefinition.CreationContext {
        private final Object[] resolved;

        DefaultCreationContext(BeanResolutionContext resolutionContext, Object[] resolved) {
            super(resolutionContext);
            this.resolved = resolved;
        }

        @Override
        public Optional<InjectionPoint<?>> getInjectionPoint() {
            return resolutionContext.getPath().currentSegment()
                // the segment of a top level lookup is the creation of this bean itself, not an injection point
                .filter(segment -> segment.getDeclaringType() != DefaultRuntimeBeanDefinition.this)
                .map(BeanResolutionContext.Segment::getInjectionPoint);
        }

        @Override
        public int getInjectedBeanCount() {
            return resolved.length;
        }

        @Override
        public <V> V getInjectedBean(int index) {
            return (V) resolved[index];
        }

        @Override
        public <V> V getInjectedBean(Argument<V> type, @Nullable Qualifier<V> qualifier) {
            Objects.requireNonNull(type, "Injection point type cannot be null");
            for (int i = 0; i < injectionPoints.length; i++) {
                if (injectionPoints[i].matches(type, qualifier)) {
                    return (V) resolved[i];
                }
            }
            throw new IllegalArgumentException("No injection point declared for type [" + type + "]"
                + (qualifier == null ? "" : " and qualifier [" + qualifier + "]"));
        }
    }

    /**
     * A runtime bean definition built with a disposer.
     *
     * <p>Only this subclass is a {@link DisposableBeanDefinition}: the context disposes of a bean only when its
     * definition is one, so a definition built without a disposer keeps the behaviour it had before disposers
     * existed.</p>
     *
     * <p>A disposer that takes only the {@link BeanContext} resolves nothing, so the disposal creates no
     * {@link BeanResolutionContext} for it. A disposer that takes a
     * {@link RuntimeBeanDefinition.DisposalContext} is given one created for the disposal alone rather than the
     * one the context may pass in: that one belongs to the creation of the bean being disposed of, and the
     * disposer is to share neither its dependents nor its instances. The dependent objects the disposer resolves
     * through it are destroyed as soon as it returns.</p>
     *
     * @param <T> The bean type
     * @since 5.2.0
     */
    static final class Disposable<T> extends DefaultRuntimeBeanDefinition<T> implements DisposableBeanDefinition<T> {
        @Nullable
        private final BiConsumer<BeanContext, T> disposer;
        @Nullable
        private final BiConsumer<RuntimeBeanDefinition.DisposalContext, T> injectedDisposer;

        Disposable(Argument<T> beanType,
                   Function<RuntimeBeanDefinition.CreationContext, T> beanFactory,
                   @Nullable Qualifier<T> qualifier,
                   @Nullable AnnotationMetadata annotationMetadata,
                   boolean isSingleton,
                   @Nullable Class<? extends Annotation> scope,
                   Class<?>[] exposedTypes,
                   @Nullable Map<Class<?>, List<Argument<?>>> typeArguments,
                   InjectionPointSpec[] injectionPoints,
                   @Nullable BiConsumer<BeanContext, T> disposer,
                   @Nullable BiConsumer<RuntimeBeanDefinition.DisposalContext, T> injectedDisposer) {
            super(beanType, beanFactory, qualifier, annotationMetadata, isSingleton, scope, exposedTypes, typeArguments, injectionPoints);
            if ((disposer == null) == (injectedDisposer == null)) {
                throw new IllegalArgumentException("Exactly one disposer form is required");
            }
            this.disposer = disposer;
            this.injectedDisposer = injectedDisposer;
        }

        @Override
        public T dispose(BeanContext context, T bean) {
            BiConsumer<BeanContext, T> contextDisposer = disposer;
            if (contextDisposer != null) {
                contextDisposer.accept(context, bean);
                return bean;
            }
            BiConsumer<RuntimeBeanDefinition.DisposalContext, T> resolvingDisposer = Objects.requireNonNull(injectedDisposer);
            try (DefaultBeanResolutionContext disposalContext = new DefaultBeanResolutionContext(context, this)) {
                try {
                    resolvingDisposer.accept(newDisposalContext(disposalContext), bean);
                } finally {
                    destroyDependents(context, disposalContext.getAndResetDependentBeans());
                }
            }
            return bean;
        }

        @Override
        public T dispose(BeanResolutionContext resolutionContext, BeanContext context, T bean) {
            return dispose(context, bean);
        }

        /**
         * Destroys the dependent objects a disposal resolved, in the reverse of the order they were resolved in
         * and as dependents, the way the bean context destroys the dependents of a bean: what the disposer
         * resolved is owned by the disposal, so a {@link LifeCycle} among them is no more stopped than one
         * resolved for an injection point is.
         *
         * @param context    The bean context
         * @param dependents The dependent registrations
         */
        private static void destroyDependents(BeanContext context, List<BeanRegistration<?>> dependents) {
            ListIterator<BeanRegistration<?>> i = dependents.listIterator(dependents.size());
            while (i.hasPrevious()) {
                BeanRegistration<?> dependent = i.previous();
                if (context instanceof DefaultBeanContext defaultBeanContext) {
                    defaultBeanContext.destroyDependentBean(dependent);
                } else {
                    context.destroyBean(dependent);
                }
            }
        }
    }

    /**
     * Implementation of {@link RuntimeBeanDefinition.Builder}.
     * @param <B> The bean
     */
    static final class RuntimeBeanBuilder<B> implements RuntimeBeanDefinition.Builder<B> {
        private Argument<B> beanType;
        private final Function<RuntimeBeanDefinition.CreationContext, B> beanFactory;
        private final List<InjectionPointSpec> injectionPoints = new ArrayList<>(3);
        @Nullable
        private Qualifier<B> qualifier;
        private boolean singleton;
        private AnnotationMetadata annotationMetadata;
        @Nullable
        private Class<? extends Annotation> scope;
        private Class<?>[] exposedTypes = ReflectionUtils.EMPTY_CLASS_ARRAY;

        @Nullable
        private Map<Class<?>, List<Argument<?>>> typeArguments;
        @Nullable
        private Class<? extends B> replacesType;
        @Nullable
        private BiConsumer<BeanContext, B> disposer;
        @Nullable
        private BiConsumer<RuntimeBeanDefinition.DisposalContext, B> injectedDisposer;

        RuntimeBeanBuilder(Argument<B> beanType, Supplier<B> supplier) {
            this.beanType = Objects.requireNonNull(beanType, MSG_BEAN_TYPE_CANNOT_BE_NULL);
            Objects.requireNonNull(supplier, "Bean supplier cannot be null");
            this.beanFactory = creationContext -> supplier.get();
            this.annotationMetadata = AnnotationMetadata.EMPTY_METADATA;
        }

        RuntimeBeanBuilder(Argument<B> beanType, Function<RuntimeBeanDefinition.CreationContext, B> beanFactory) {
            this.beanType = Objects.requireNonNull(beanType, MSG_BEAN_TYPE_CANNOT_BE_NULL);
            this.beanFactory = Objects.requireNonNull(beanFactory, "Bean factory cannot be null");
            this.annotationMetadata = AnnotationMetadata.EMPTY_METADATA;
        }

        @Override
        public Builder<B> qualifier(@Nullable Qualifier<B> qualifier) {
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
        public Builder<B> replaces(@Nullable Class<? extends B> otherType) {
            this.replacesType = otherType;
            return this;
        }

        @Override
        @SuppressWarnings("java:S1872")
        public Builder<B> scope(@Nullable Class<? extends Annotation> scope) {
            this.scope = scope;
            if (scope != null && scope.getSimpleName().equals("Singleton")) {
                this.singleton = true;
            }
            return this;
        }

        @Override
        public Builder<B> singleton(boolean isSingleton) {
            this.singleton = isSingleton;
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
        public Builder<B> annotationMetadata(@Nullable AnnotationMetadata annotationMetadata) {
            this.annotationMetadata = annotationMetadata == null ? AnnotationMetadata.EMPTY_METADATA : annotationMetadata;
            return this;
        }

        @Override
        public Builder<B> injectionPoint(Argument<?> type, @Nullable Qualifier<?> qualifier) {
            injectionPoints.add(new InjectionPointSpec(type, qualifier));
            return this;
        }

        @Override
        public Builder<B> disposer(@Nullable BiConsumer<BeanContext, B> disposer) {
            this.disposer = disposer;
            this.injectedDisposer = null;
            return this;
        }

        @Override
        public Builder<B> injectedDisposer(@Nullable BiConsumer<RuntimeBeanDefinition.DisposalContext, B> disposer) {
            this.injectedDisposer = disposer;
            this.disposer = null;
            return this;
        }

        @Override
        public RuntimeBeanDefinition<B> build() {
            if (replacesType != null) {
                MutableAnnotationMetadata mutableAnnotationMetadata;
                if (annotationMetadata instanceof MutableAnnotationMetadata mm) {
                    mutableAnnotationMetadata = mm;
                } else if (annotationMetadata == EMPTY_METADATA) {
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
            InjectionPointSpec[] declaredInjectionPoints = injectionPoints.isEmpty() ?
                NO_INJECTION_POINTS :
                injectionPoints.toArray(new InjectionPointSpec[0]);
            if (disposer != null || injectedDisposer != null) {
                return new Disposable<>(
                    beanType,
                    beanFactory,
                    qualifier,
                    annotationMetadata,
                    singleton,
                    scope,
                    exposedTypes,
                    typeArguments,
                    declaredInjectionPoints,
                    disposer,
                    injectedDisposer
                );
            }
            return new DefaultRuntimeBeanDefinition<>(
                beanType,
                beanFactory,
                qualifier,
                annotationMetadata,
                singleton,
                scope,
                exposedTypes,
                typeArguments,
                declaredInjectionPoints
            );
        }
    }
}

