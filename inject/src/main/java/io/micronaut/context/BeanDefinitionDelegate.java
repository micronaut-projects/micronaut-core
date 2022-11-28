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
package io.micronaut.context;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.env.ConfigurationPath;
import io.micronaut.context.exceptions.BeanInstantiationException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.naming.NameResolver;
import io.micronaut.core.naming.Named;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanFactory;
import io.micronaut.inject.DelegatingBeanDefinition;
import io.micronaut.inject.DisposableBeanDefinition;
import io.micronaut.inject.InitializingBeanDefinition;
import io.micronaut.inject.InjectionPoint;
import io.micronaut.inject.ParametrizedBeanFactory;
import io.micronaut.inject.ValidatedBeanDefinition;
import io.micronaut.inject.qualifiers.PrimaryQualifier;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A delegate bean definition.
 *
 * @param <T> The bean type
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
sealed class BeanDefinitionDelegate<T> extends AbstractBeanContextConditional
                                       implements DelegatingBeanDefinition<T>, BeanFactory<T>, NameResolver {

    protected final BeanDefinition<T> definition;
    @Nullable
    protected final Qualifier<T> qualifier;

    private final ConfigurationPath configurationPath;


    private BeanDefinitionDelegate(BeanDefinition<T> definition, @Nullable Qualifier<T> qualifier, ConfigurationPath configurationPath) {
        this.definition = definition;
        this.qualifier = qualifier;
        this.configurationPath = configurationPath;
    }

    public Optional<ConfigurationPath> getConfigurationPath() {
        return Optional.ofNullable(configurationPath);
    }

    @Override
    public Qualifier<T> getDeclaredQualifier() {
        return qualifier;
    }

    /**
     * @return the qualifier
     */
    @Nullable
    public Qualifier<T> getQualifier() {
        return qualifier;
    }

    @Nullable
    @Override
    public Qualifier<T> resolveDynamicQualifier() {
        return qualifier;
    }

    /**
     * @return The bean definition type
     */
    BeanDefinition<T> getDelegate() {
        return definition;
    }

    @Override
    public boolean isProxy() {
        return definition.isProxy();
    }

    @Override
    public boolean isIterable() {
        return definition.isIterable();
    }

    @Override
    public boolean isPrimary() {
        return isLocalQualifierPrimary() || definition.isPrimary() || isPrimaryThroughAttribute();
    }

    private boolean isLocalQualifierPrimary() {
        return qualifier != null && (qualifier == PrimaryQualifier.INSTANCE || qualifier.contains(PrimaryQualifier.INSTANCE));
    }

    private boolean isPrimaryThroughAttribute() {
        if (configurationPath != null) {
            return configurationPath.isPrimary();
        }
        return false;
    }

    @Override
    public T build(BeanResolutionContext resolutionContext, BeanContext context, BeanDefinition<T> definition) throws BeanInstantiationException {
        ConfigurationPath oldPath = null;
        if (configurationPath != null) {
            oldPath = resolutionContext.getConfigurationPath();
            resolutionContext.setConfigurationPath(configurationPath);
        }

        try {
            if (this.definition instanceof ParametrizedBeanFactory) {
                ParametrizedBeanFactory<T> parametrizedBeanFactory = (ParametrizedBeanFactory<T>) this.definition;
                Map<String, Object> fulfilled = getParametersValues(resolutionContext, (DefaultBeanContext) context, definition, parametrizedBeanFactory);
                return parametrizedBeanFactory.build(resolutionContext, context, definition, fulfilled);
            }
            if (this.definition instanceof BeanFactory) {
                return ((BeanFactory<T>) this.definition).build(resolutionContext, context, definition);
            } else {
                throw new IllegalStateException("Cannot construct a dynamically registered singleton");
            }
        } finally {
            resolutionContext.setConfigurationPath(oldPath);
        }
    }

    @Nullable
    private Map<String, Object> getParametersValues(BeanResolutionContext resolutionContext,
                                                    DefaultBeanContext context,
                                                    BeanDefinition<T> definition,
                                                    ParametrizedBeanFactory<T> parametrizedBeanFactory) {
        Argument<Object>[] requiredArguments = (Argument<Object>[]) parametrizedBeanFactory.getRequiredArguments();
        if (requiredArguments.length == 0) {
            return Collections.emptyMap();
        }
        Map<String, Object> fulfilled = new LinkedHashMap<>(requiredArguments.length, 1);
        ConfigurationPath configurationPath = resolutionContext.getConfigurationPath();
        for (Argument<Object> argument : requiredArguments) {
            String argumentName = argument.getName();
            if (argument.isAnnotationPresent(Parameter.class)) {
                Class<Object> type = (Class<Object>) argument.getWrapperType();
                if (CharSequence.class.isAssignableFrom(type)) {
                    String simpleName = configurationPath.simpleName();
                    if (simpleName != null) {
                        fulfilled.put(argumentName, simpleName);
                    } else {
                        Qualifier<?> q = resolutionContext.getCurrentQualifier();
                        if (q instanceof Named named) {
                            fulfilled.put(argumentName, named.getName());
                        } else if (q == PrimaryQualifier.INSTANCE) {
                            fulfilled.put(argumentName, Primary.SIMPLE_NAME);
                        }
                    }
                } else if (Number.class.isAssignableFrom(type)) {
                    fulfilled.put(argumentName, context.getConversionService().convertRequired(configurationPath.index(), argument));
                } else if (qualifier != null && hasDeclaredAnnotation(EachBean.class) && String.class.equals(type) && "name".equals(argumentName)) {
                    if (isLocalQualifierPrimary()) {
                        fulfilled.put(argumentName, Primary.SIMPLE_NAME);
                    } else if (qualifier instanceof Named named) {
                        fulfilled.put(argumentName, named.getName());
                    }
                } else {
                    if (argument.isProvider()) {
                        Argument<?> pt = argument.getFirstTypeVariable().orElse(null);
                        if (pt != null) {
                            type = (Class<Object>) pt.getType();
                        }
                    }

                    try (BeanResolutionContext.Path ignored = resolutionContext.getPath().pushConstructorResolve(definition, argument)) {
                        if (type.equals(configurationPath.configurationType())) {
                            Object bean = context.findBean(resolutionContext, argument, configurationPath.beanQualifier()).orElse(null);
                            fulfilled.put(argumentName, bean);
                        } else {
                            ConfigurationPath old = resolutionContext.setConfigurationPath(null);// reset
                            try {
                                Qualifier<Object> q = qualifier != null ? (Qualifier<Object>) qualifier : configurationPath.beanQualifier();
                                Object bean = context.findBean(resolutionContext, argument, q).orElse(null);
                                fulfilled.put(argumentName, bean);
                            } finally {
                                resolutionContext.setConfigurationPath(old);
                            }
                        }
                    }
                }
            }
        }
        return fulfilled;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BeanDefinitionDelegate<?> that = (BeanDefinitionDelegate<?>) o;
        return Objects.equals(definition, that.definition) &&
            Objects.equals(qualifier, that.qualifier);
    }

    @Override
    public int hashCode() {
        return Objects.hash(definition, qualifier);
    }

    /**
     * @return The bean definition type
     */
    @Override
    public BeanDefinition<T> getTarget() {
        return definition;
    }

    @Override
    public Optional<String> resolveName() {
        if (qualifier instanceof Named named) {
            return Optional.of(named.getName());
        }
        return Optional.empty();
    }

    @Override
    public String toString() {
        return definition.toString();
    }

    /**
     * @param definition The bean definition type
     * @param <T>        The type
     * @return The new bean definition
     */
    static <T> BeanDefinitionDelegate<T> create(BeanDefinition<T> definition) {
        return create(definition, null);
    }

    /**
     * @param definition The bean definition type
     * @param qualifier The bean qualifier
     * @param <T>        The type
     * @return The new bean definition
     */
    static <T> BeanDefinitionDelegate<T> create(BeanDefinition<T> definition, Qualifier<T> qualifier) {
        return create(definition, qualifier, null);
    }

    /**
     * @param definition The bean definition type
     * @param qualifier The bean qualifier
     * @param path The configuration path.
     * @param <T>        The type
     * @return The new bean definition
     */
    static <T> BeanDefinitionDelegate<T> create(BeanDefinition<T> definition, Qualifier<T> qualifier, ConfigurationPath path) {
        if (definition instanceof InitializingBeanDefinition || definition instanceof DisposableBeanDefinition) {
            if (definition instanceof ValidatedBeanDefinition) {
                return new LifeCycleValidatingDelegate<>(definition, qualifier, path);
            } else {
                return new LifeCycleDelegate<>(definition, qualifier, path);
            }
        } else if (definition instanceof ValidatedBeanDefinition) {
            return new ValidatingDelegate<>(definition, qualifier, path);
        }
        return new BeanDefinitionDelegate<>(definition, qualifier, path);
    }

    @Override
    @NonNull
    public String getName() {
        return definition.getName();
    }

    /**
     * @param <T> The bean definition type
     */
    interface ProxyInitializingBeanDefinition<T> extends DelegatingBeanDefinition<T>, InitializingBeanDefinition<T> {
        @Override
        default T initialize(BeanResolutionContext resolutionContext, BeanContext context, T bean) {
            BeanDefinition<T> definition = getTarget();
            if (definition instanceof InitializingBeanDefinition) {
                return ((InitializingBeanDefinition<T>) definition).initialize(resolutionContext, context, bean);
            }
            return bean;
        }
    }

    /**
     * @param <T> The bean definition type
     */
    interface ProxyDisposableBeanDefinition<T> extends DelegatingBeanDefinition<T>, DisposableBeanDefinition<T> {
        @Override
        default T dispose(BeanResolutionContext resolutionContext, BeanContext context, T bean) {
            BeanDefinition<T> definition = getTarget();
            if (definition instanceof DisposableBeanDefinition) {
                return ((DisposableBeanDefinition<T>) definition).dispose(resolutionContext, context, bean);
            }
            return bean;
        }
    }

    /**
     * @param <T> The bean definition type
     */
    interface ProxyValidatingBeanDefinition<T> extends DelegatingBeanDefinition<T>, ValidatedBeanDefinition<T> {
        @Override
        default T validate(BeanResolutionContext resolutionContext, T instance) {
            BeanDefinition<T> definition = getTarget();
            if (definition instanceof ValidatedBeanDefinition) {
                return ((ValidatedBeanDefinition<T>) definition).validate(resolutionContext, instance);
            }
            return instance;
        }

        @Override
        default <V> void validateBeanArgument(@NonNull BeanResolutionContext resolutionContext, @NonNull InjectionPoint injectionPoint, @NonNull Argument<V> argument, int index, @Nullable V value) {
            BeanDefinition<T> definition = getTarget();
            if (definition instanceof ValidatedBeanDefinition) {
                ((ValidatedBeanDefinition<T>) definition).validateBeanArgument(
                        resolutionContext,
                        injectionPoint,
                        argument,
                        index,
                        value
                );
            }
        }
    }

    /**
     * @param <T> The bean definition type
     */
    private static final class LifeCycleDelegate<T> extends BeanDefinitionDelegate<T> implements ProxyInitializingBeanDefinition<T>, ProxyDisposableBeanDefinition<T> {
        private LifeCycleDelegate(BeanDefinition<T> definition, Qualifier qualifier, ConfigurationPath path) {
            super(definition, qualifier, path);
        }
    }

    /**
     * @param <T> The bean definition type
     */
    private static final class ValidatingDelegate<T> extends BeanDefinitionDelegate<T> implements ProxyValidatingBeanDefinition<T> {
        private ValidatingDelegate(BeanDefinition<T> definition, Qualifier qualifier, ConfigurationPath path) {
            super(definition, qualifier, path);
        }
    }

    /**
     * @param <T> The bean definition type
     */
    private static final class LifeCycleValidatingDelegate<T> extends BeanDefinitionDelegate<T> implements ProxyValidatingBeanDefinition<T>, ProxyInitializingBeanDefinition<T>, ProxyDisposableBeanDefinition<T> {
        private LifeCycleValidatingDelegate(BeanDefinition<T> definition, Qualifier qualifier, ConfigurationPath path) {
            super(definition, qualifier, path);
        }
    }
}
