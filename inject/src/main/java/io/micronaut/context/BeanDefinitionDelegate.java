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
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.naming.NameResolver;
import io.micronaut.core.naming.Named;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.value.ValueResolver;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanFactory;
import io.micronaut.inject.DelegatingBeanDefinition;
import io.micronaut.inject.DisposableBeanDefinition;
import io.micronaut.inject.InitializingBeanDefinition;
import io.micronaut.inject.InjectionPoint;
import io.micronaut.inject.ParametrizedBeanFactory;
import io.micronaut.inject.ValidatedBeanDefinition;
import io.micronaut.inject.qualifiers.PrimaryQualifier;
import io.micronaut.inject.qualifiers.Qualifiers;

import java.util.Collections;
import java.util.HashMap;
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
class BeanDefinitionDelegate<T> extends AbstractBeanContextConditional implements DelegatingBeanDefinition<T>, BeanFactory<T>, NameResolver, ValueResolver<String> {

    static final String PRIMARY_ATTRIBUTE = Primary.class.getName();

    protected final BeanDefinition<T> definition;
    @Nullable
    protected Map<String, Object> attributes;
    @Nullable
    protected final Qualifier<T> qualifier;

    private BeanDefinitionDelegate(BeanDefinition<T> definition, @Nullable Qualifier<T> qualifier) {
        this.definition = definition;
        this.qualifier = qualifier;
    }

    @Override
    public Qualifier<T> getDeclaredQualifier() {
        if (qualifier != null) {
            return qualifier;
        } else {
            return DelegatingBeanDefinition.super.getDeclaredQualifier();
        }
    }

    /**
     * @return the qualifier
     */
    @Nullable
    public Qualifier<T> getQualifier() {
        return qualifier;
    }

    /**
     * @return the attributes
     */
    @Nullable
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Nullable
    @Override
    public Qualifier<T> resolveDynamicQualifier() {
        if (qualifier != null) {
            return qualifier;
        }
        if (attributes == null) {
            return null;
        }
        Object o = attributes.get(NAMED_ATTRIBUTE);
        if (o instanceof CharSequence) {
            return Qualifiers.byName(o.toString());
        }
        return null;
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
        if (attributes == null) {
            return false;
        }
        Object o = attributes.get(ConfigurationPath.ATTRIBUTE);
        if (o instanceof ConfigurationPath path) {
            return path.isPrimary();
        }
        return false;
    }

    @Override
    public T build(BeanResolutionContext resolutionContext, BeanContext context, BeanDefinition<T> definition) throws BeanInstantiationException {
        Map<CharSequence, Object> oldAttributes = null;
        if (CollectionUtils.isNotEmpty(attributes)) {
            oldAttributes = resolutionContext.getAttributes();
            Map<CharSequence, Object> newAttributes;
            if (oldAttributes == null) {
                newAttributes = new LinkedHashMap<>(attributes);
            } else {
                newAttributes = new LinkedHashMap<>(attributes.size() + oldAttributes.size(), 1);
                newAttributes.putAll(oldAttributes);
                newAttributes.putAll(attributes);
            }
            resolutionContext.setAttributes(newAttributes);
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
            resolutionContext.setAttributes(oldAttributes);
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
                Class<Object> type = argument.getType();
                if (isMapKeyCandidate(configurationPath, argumentName, type)) {
                    String simpleName = configurationPath.simpleName();
                    if (simpleName != null) {
                        fulfilled.put(argumentName, simpleName);
                    } else {
                        Qualifier<?> q = resolutionContext.getCurrentQualifier();
                        if (q instanceof Named named) {
                            fulfilled.put(argumentName, named.getName());
                        } else if (q == PrimaryQualifier.INSTANCE) {
                            fulfilled.put(argumentName, "Primary");
                        }
                    }
                } else if (isIndexCandidate(configurationPath, argumentName, type)) {
                    fulfilled.put(argumentName, context.getConversionService().convertRequired(configurationPath.index(), argument));
                } else if (qualifier != null && hasDeclaredAnnotation(EachBean.class) && String.class.equals(type) && "name".equals(argumentName)) {
                    if (isLocalQualifierPrimary()) {
                        fulfilled.put(argumentName, "Primary");
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
                            Object old = resolutionContext.removeAttribute(ConfigurationPath.ATTRIBUTE);// reset
                            try {
                                Qualifier<Object> q = qualifier != null ? (Qualifier<Object>) qualifier : configurationPath.beanQualifier();
                                Object bean = context.findBean(resolutionContext, argument, q).orElse(null);
                                fulfilled.put(argumentName, bean);
                            } finally {
                                resolutionContext.setAttribute(ConfigurationPath.ATTRIBUTE, old);
                            }
                        }
                    }
                }
            }
        }
        return fulfilled;
    }

    private static boolean isIndexCandidate(ConfigurationPath configurationPath, String argumentName, Class<Object> type) {
        return Number.class.isAssignableFrom(type) && "index".equals(argumentName);
    }

    private static boolean isMapKeyCandidate(ConfigurationPath configurationPath, String argumentName, Class<Object> type) {
        return CharSequence.class.isAssignableFrom(type) && "name".equals(argumentName);
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
            Objects.equals(resolveName().orElse(null), that.resolveName().orElse(null));
    }

    @Override
    public int hashCode() {
        return Objects.hash(definition, resolveName().orElse(null));
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
        } else {
            return get(Named.class.getName(), String.class);
        }
    }

    /**
     * Adds a new attribute.
     *
     * @param name  The name
     * @param value The value
     */
    public void put(String name, Object value) {
        if (attributes == null) {
            attributes = new HashMap<>(2, 1);
        }
        this.attributes.put(name, value);
    }

    @Override
    public <K> Optional<K> get(String name, ArgumentConversionContext<K> conversionContext) {
        if (attributes == null) {
            return Optional.empty();
        }
        Object value = attributes.get(name);
        if (value != null && conversionContext.getArgument().getType().isInstance(value)) {
            return Optional.of((K) value);
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
        if (definition instanceof InitializingBeanDefinition || definition instanceof DisposableBeanDefinition) {
            if (definition instanceof ValidatedBeanDefinition) {
                return new LifeCycleValidatingDelegate<>(definition, qualifier);
            } else {
                return new LifeCycleDelegate<>(definition, qualifier);
            }
        } else if (definition instanceof ValidatedBeanDefinition) {
            return new ValidatingDelegate<>(definition, qualifier);
        }
        return new BeanDefinitionDelegate<>(definition, qualifier);
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
        private LifeCycleDelegate(BeanDefinition<T> definition, Qualifier qualifier) {
            super(definition, qualifier);
        }
    }

    /**
     * @param <T> The bean definition type
     */
    private static final class ValidatingDelegate<T> extends BeanDefinitionDelegate<T> implements ProxyValidatingBeanDefinition<T> {
        private ValidatingDelegate(BeanDefinition<T> definition, Qualifier qualifier) {
            super(definition, qualifier);
        }
    }

    /**
     * @param <T> The bean definition type
     */
    private static final class LifeCycleValidatingDelegate<T> extends BeanDefinitionDelegate<T> implements ProxyValidatingBeanDefinition<T>, ProxyInitializingBeanDefinition<T>, ProxyDisposableBeanDefinition<T> {
        private LifeCycleValidatingDelegate(BeanDefinition<T> definition, Qualifier qualifier) {
            super(definition, qualifier);
        }
    }
}
