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
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ObjectUtils;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.DelegatingBeanDefinition;
import io.micronaut.inject.DisposableBeanDefinition;
import io.micronaut.inject.InitializingBeanDefinition;
import io.micronaut.inject.InjectableBeanDefinition;
import io.micronaut.inject.InjectionPoint;
import io.micronaut.inject.InstantiatableBeanDefinition;
import io.micronaut.inject.ParametrizedInstantiatableBeanDefinition;
import io.micronaut.inject.ValidatedBeanDefinition;
import io.micronaut.inject.qualifiers.PrimaryQualifier;
import io.micronaut.inject.qualifiers.Qualifiers;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
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
    implements DelegatingBeanDefinition<T>, InstantiatableBeanDefinition<T>,
    InjectableBeanDefinition<T>, NameResolver {

    protected final BeanDefinition<T> definition;
    @Nullable
    protected final Qualifier<T> qualifier;

    @Nullable
    private final ConfigurationPath configurationPath;

    private final Map<String, List<Argument<?>>> typeArgumentsMap;

    private BeanDefinitionDelegate(BeanDefinition<T> definition, @Nullable Qualifier<T> qualifier,
                                   @Nullable ConfigurationPath configurationPath,
                                   @NonNull Map<String, List<Argument<?>>> typeArgumentsMap) {
        this.definition = definition;
        this.qualifier = qualifier;
        this.configurationPath = configurationPath;
        this.typeArgumentsMap = typeArgumentsMap;
    }

    public Optional<ConfigurationPath> getConfigurationPath() {
        return Optional.ofNullable(configurationPath);
    }

    @Override
    public List<Argument<?>> getTypeArguments(String type) {
        List<Argument<?>> arguments = typeArgumentsMap.get(type);
        return arguments == null ? getTarget().getTypeArguments(type) : arguments;
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
        return isQualifiedAsPrimary(qualifier) || definition.isPrimary() || isPrimaryThroughAttribute();
    }

    private boolean isQualifiedAsPrimary(Qualifier<?> q) {
        return q != null && (q == PrimaryQualifier.INSTANCE || q.contains(PrimaryQualifier.INSTANCE));
    }

    private boolean isPrimaryThroughAttribute() {
        if (configurationPath != null) {
            return configurationPath.isPrimary();
        }
        return false;
    }

    @Override
    public T inject(BeanContext context, T bean) {
        if (definition instanceof InjectableBeanDefinition<T>) {
            try (DefaultBeanResolutionContext resolutionContext = new DefaultBeanResolutionContext(context, this)) {
                return inject(resolutionContext, context, bean);
            }
        }
        return bean;
    }

    @Override
    public T inject(BeanResolutionContext resolutionContext, BeanContext context, T bean) {
        ConfigurationPath oldPath = null;
        if (configurationPath != null && resolutionContext != null) {
            oldPath = resolutionContext.getConfigurationPath();
            resolutionContext.setConfigurationPath(configurationPath);
        }
        if (definition instanceof InjectableBeanDefinition<T> injectableBeanDefinition) {
            try {
                return injectableBeanDefinition.inject(resolutionContext, context, bean);
            } finally {
                if (resolutionContext != null) {
                    resolutionContext.setConfigurationPath(oldPath);
                }
            }
        }
        return bean;
    }

    @Override
    public T instantiate(BeanResolutionContext resolutionContext, BeanContext context) throws BeanInstantiationException {
        ConfigurationPath oldPath = null;
        if (configurationPath != null) {
            oldPath = resolutionContext.getConfigurationPath();
            resolutionContext.setConfigurationPath(configurationPath);
        }
        try {
            if (this.definition instanceof ParametrizedInstantiatableBeanDefinition<T> parametrizedInstantiatableBeanDefinition) {
                Argument<Object>[] requiredArguments = parametrizedInstantiatableBeanDefinition.getRequiredArguments();
                Map<String, Object> fulfilled = getParametersValues(resolutionContext, (DefaultBeanContext) context, definition, requiredArguments);
                return parametrizedInstantiatableBeanDefinition.instantiate(resolutionContext, context, fulfilled);
            }
            if (this.definition instanceof InstantiatableBeanDefinition<T> instantiatableBeanDefinition) {
                return instantiatableBeanDefinition.instantiate(resolutionContext, context);
            }
            throw new IllegalStateException("Cannot construct a dynamically registered singleton");
        } finally {
            resolutionContext.setConfigurationPath(oldPath);
        }
    }

    @Nullable
    private Map<String, Object> getParametersValues(BeanResolutionContext resolutionContext,
                                                    DefaultBeanContext context,
                                                    BeanDefinition<T> definition,
                                                    Argument<Object>[] requiredArguments) {
        if (requiredArguments.length == 0) {
            return Collections.emptyMap();
        }
        Map<String, Object> fulfilled = new LinkedHashMap<>(requiredArguments.length, 1);
        ConfigurationPath configurationPath = resolutionContext.getConfigurationPath();
        for (Argument<Object> argument : requiredArguments) {
            String argumentName = argument.getName();
            if (argument.isAnnotationPresent(Parameter.class)) {
                Class<?> type = argument.getWrapperType();
                boolean isEnum = Enum.class.isAssignableFrom(type);
                if (CharSequence.class.isAssignableFrom(type) || isEnum) {
                    String simpleName = configurationPath.simpleName();
                    if (simpleName != null) {
                        Object value = isEnum ? context.getConversionService().convertRequired(simpleName, type) : simpleName;
                        fulfilled.put(argumentName, value);
                    } else {
                        String name = findName(resolutionContext.getCurrentQualifier());
                        if (name != null) {
                            Object value = isEnum ? context.getConversionService().convertRequired(name, type) : name;
                            fulfilled.put(argumentName, value);
                        }
                    }
                } else if (Number.class.isAssignableFrom(type)) {
                    fulfilled.put(argumentName, context.getConversionService().convertRequired(configurationPath.index(), argument));
                } else if (qualifier != null && hasDeclaredAnnotation(EachBean.class) && String.class.equals(type) && "name".equals(argumentName)) {
                    String name = findName(qualifier);
                    if (name != null) {
                        fulfilled.put(argumentName, name);
                    }
                } else {
                    if (argument.isProvider()) {
                        Argument<?> pt = argument.getFirstTypeVariable().orElse(null);
                        if (pt != null) {
                            type = pt.getType();
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

    @Nullable
    private String findName(@Nullable Qualifier<?> q) {
        if (q == null) {
            return null;
        }
        String name = Qualifiers.findName(q);
        if (name != null) {
            return name;
        }
        if (isQualifiedAsPrimary(q)) {
            return Primary.SIMPLE_NAME;
        }
        return null;
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
        return ObjectUtils.hash(definition, qualifier);
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
        return Optional.ofNullable(findName(qualifier));
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
        return create(definition, qualifier, null, Map.of());
    }

    /**
     * @param definition       The bean definition type
     * @param qualifier        The bean qualifier
     * @param typeArgumentsMap The type arguments
     * @param <T>              The type
     * @return The new bean definition
     * @since 4.6
     */
    static <T> BeanDefinitionDelegate<T> create(BeanDefinition<T> definition,
                                                Qualifier<T> qualifier,
                                                @NonNull Map<String, List<Argument<?>>> typeArgumentsMap) {
        return create(definition, qualifier, null, typeArgumentsMap);
    }

    /**
     * @param definition       The bean definition type
     * @param qualifier        The bean qualifier
     * @param path             The configuration path.
     * @param typeArgumentsMap The type arguments
     * @param <T>              The type
     * @return The new bean definition
     */
    static <T> BeanDefinitionDelegate<T> create(BeanDefinition<T> definition,
                                                Qualifier<T> qualifier,
                                                ConfigurationPath path,
                                                @NonNull Map<String, List<Argument<?>>> typeArgumentsMap) {
        if (definition instanceof InitializingBeanDefinition || definition instanceof DisposableBeanDefinition) {
            if (definition instanceof ValidatedBeanDefinition) {
                return new LifeCycleValidatingDelegate<>(definition, qualifier, path, typeArgumentsMap);
            } else {
                return new LifeCycleDelegate<>(definition, qualifier, path, typeArgumentsMap);
            }
        } else if (definition instanceof ValidatedBeanDefinition) {
            return new ValidatingDelegate<>(definition, qualifier, path, typeArgumentsMap);
        }
        return new BeanDefinitionDelegate<>(definition, qualifier, path, typeArgumentsMap);
    }

    /**
     * @param definition The bean definition type
     * @param qualifier The bean qualifier
     * @param path The configuration path.
     * @param <T>        The type
     * @return The new bean definition
     */
    static <T> BeanDefinitionDelegate<T> create(BeanDefinition<T> definition,
                                                Qualifier<T> qualifier,
                                                ConfigurationPath path) {
        return create(definition, qualifier, path, Map.of());
    }

    @Override
    @NonNull
    public String getName() {
        return definition.getName();
    }

    /**
     * @param <T> The bean definition type
     */
    sealed interface ProxyInitializingBeanDefinition<T> extends DelegatingBeanDefinition<T>, InitializingBeanDefinition<T> {
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
    sealed interface ProxyDisposableBeanDefinition<T> extends DelegatingBeanDefinition<T>, DisposableBeanDefinition<T> {
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
    sealed interface ProxyValidatingBeanDefinition<T> extends DelegatingBeanDefinition<T>, ValidatedBeanDefinition<T> {
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
        private LifeCycleDelegate(BeanDefinition<T> definition, Qualifier qualifier, ConfigurationPath path, @NonNull Map<String, List<Argument<?>>> typeArgumentsMap) {
            super(definition, qualifier, path, typeArgumentsMap);
        }
    }

    /**
     * @param <T> The bean definition type
     */
    private static final class ValidatingDelegate<T> extends BeanDefinitionDelegate<T> implements ProxyValidatingBeanDefinition<T> {
        private ValidatingDelegate(BeanDefinition<T> definition, Qualifier qualifier, ConfigurationPath path, @NonNull Map<String, List<Argument<?>>> typeArgumentsMap) {
            super(definition, qualifier, path, typeArgumentsMap);
        }
    }

    /**
     * @param <T> The bean definition type
     */
    private static final class LifeCycleValidatingDelegate<T> extends BeanDefinitionDelegate<T> implements ProxyValidatingBeanDefinition<T>, ProxyInitializingBeanDefinition<T>, ProxyDisposableBeanDefinition<T> {
        private LifeCycleValidatingDelegate(BeanDefinition<T> definition, Qualifier qualifier, ConfigurationPath path, @NonNull Map<String, List<Argument<?>>> typeArgumentsMap) {
            super(definition, qualifier, path, typeArgumentsMap);
        }
    }
}
