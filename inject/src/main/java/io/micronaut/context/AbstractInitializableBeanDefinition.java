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

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Value;
import io.micronaut.context.env.Environment;
import io.micronaut.context.event.BeanInitializedEventListener;
import io.micronaut.context.event.BeanInitializingEvent;
import io.micronaut.context.exceptions.BeanContextException;
import io.micronaut.context.exceptions.BeanInstantiationException;
import io.micronaut.context.exceptions.DependencyInjectionException;
import io.micronaut.context.exceptions.DisabledBeanException;
import io.micronaut.context.exceptions.NoSuchBeanException;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.naming.Named;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.DefaultArgument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.value.PropertyResolver;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ConstructorInjectionPoint;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.ExecutableMethodsDefinition;
import io.micronaut.inject.FieldInjectionPoint;
import io.micronaut.inject.MethodInjectionPoint;
import io.micronaut.inject.ValidatedBeanDefinition;
import io.micronaut.inject.annotation.AbstractEnvironmentAnnotationMetadata;
import io.micronaut.inject.qualifiers.InterceptorBindingQualifier;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.inject.qualifiers.TypeAnnotationQualifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * <p>Default implementation of the {@link BeanDefinition} interface. This class is generally not used directly in user
 * code.
 * Instead a build time tool does analysis of source code and dynamically produces subclasses of this class containing
 * information about the available injection points for a given class.</p>
 *
 * <p>For technical reasons the class has to be marked as public, but is regarded as internal and should be used by
 * compiler tools and plugins (such as AST transformation frameworks)</p>
 *
 * <p>The {@link io.micronaut.inject.writer.BeanDefinitionWriter} class can be used to produce bean definitions at
 * compile or runtime</p>
 *
 * @param <T> The Bean definition type
 * @author Graeme Rocher
 * @author Denis Stepanov
 * @see io.micronaut.inject.writer.BeanDefinitionWriter
 * @since 3.0
 */
@Internal
public class AbstractInitializableBeanDefinition<T> extends AbstractBeanContextConditional implements BeanDefinition<T>, EnvironmentConfigurable {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractInitializableBeanDefinition.class);
    private static final String NAMED_ATTRIBUTE = Named.class.getName();

    private final Class<T> type;
    private final AnnotationMetadata annotationMetadata;
    private final Optional<String> scope;
    private final boolean isProvided;
    private final boolean isIterable;
    private final boolean isSingleton;
    private final boolean isPrimary;
    private final boolean isAbstract;
    private final boolean isConfigurationProperties;
    private final boolean isContainerType;
    private final boolean requiresMethodProcessing;
    @Nullable
    private final MethodOrFieldReference constructor;
    @Nullable
    private final MethodReference[] methodInjection;
    @Nullable
    private final FieldReference[] fieldInjection;
    @Nullable
    private final ExecutableMethodsDefinition<T> executableMethodsDefinition;
    @Nullable
    private final Map<String, Argument<?>[]> typeArgumentsMap;
    @Nullable
    private AnnotationReference[] annotationInjection;
    @Nullable
    private Environment environment;
    @Nullable
    private Optional<Argument<?>> containerElement;
    @Nullable
    private ConstructorInjectionPoint<T> constructorInjectionPoint;
    @Nullable
    private List<MethodInjectionPoint<T, ?>> methodInjectionPoints;
    @Nullable
    private List<FieldInjectionPoint<T, ?>> fieldInjectionPoints;
    @Nullable
    private List<MethodInjectionPoint<T, ?>> postConstructMethods;
    @Nullable
    private List<MethodInjectionPoint<T, ?>> preDestroyMethods;
    @Nullable
    private Collection<Class<?>> requiredComponents;
    @Nullable
    private Argument<?>[] requiredParametrizedArguments;

    private Qualifier<T> declaredQualifier;

    @SuppressWarnings("ParameterNumber")
    @Internal
    @UsedByGeneratedCode
    protected AbstractInitializableBeanDefinition(
            Class<T> beanType,
            @Nullable MethodOrFieldReference constructor,
            @Nullable AnnotationMetadata annotationMetadata,
            @Nullable MethodReference[] methodInjection,
            @Nullable FieldReference[] fieldInjection,
            @Nullable ExecutableMethodsDefinition<T> executableMethodsDefinition,
            @Nullable Map<String, Argument<?>[]> typeArgumentsMap,
            Optional<String> scope,
            boolean isAbstract,
            boolean isProvided,
            boolean isIterable,
            boolean isSingleton,
            boolean isPrimary,
            boolean isConfigurationProperties,
            boolean isContainerType,
            boolean requiresMethodProcessing) {
        this.scope = scope;
        this.type = beanType;
        if (annotationMetadata == null || annotationMetadata == AnnotationMetadata.EMPTY_METADATA) {
            this.annotationMetadata = AnnotationMetadata.EMPTY_METADATA;
        } else {
            if (annotationMetadata.hasPropertyExpressions()) {
                // we make a copy of the result of annotation metadata which is normally a reference
                // to the class metadata
                this.annotationMetadata = new BeanAnnotationMetadata(annotationMetadata);
            } else {
                this.annotationMetadata = annotationMetadata;
            }
        }
        this.isProvided = isProvided;
        this.isIterable = isIterable;
        this.isSingleton = isSingleton;
        this.isPrimary = isPrimary;
        this.isAbstract = isAbstract;
        this.constructor = constructor;
        this.methodInjection = methodInjection;
        this.fieldInjection = fieldInjection;
        this.executableMethodsDefinition = executableMethodsDefinition;
        this.typeArgumentsMap = typeArgumentsMap;
        this.isConfigurationProperties = isConfigurationProperties;
        this.isContainerType = isContainerType;
        this.requiresMethodProcessing = requiresMethodProcessing;
    }

    @SuppressWarnings("ParameterNumber")
    @Internal
    @UsedByGeneratedCode
    protected AbstractInitializableBeanDefinition(
            Class<T> beanType,
            @Nullable MethodOrFieldReference constructor,
            @Nullable AnnotationMetadata annotationMetadata,
            @Nullable MethodReference[] methodInjection,
            @Nullable FieldReference[] fieldInjection,
            @Nullable AnnotationReference[] annotationInjection,
            @Nullable ExecutableMethodsDefinition<T> executableMethodsDefinition,
            @Nullable Map<String, Argument<?>[]> typeArgumentsMap,
            Optional<String> scope,
            boolean isAbstract,
            boolean isProvided,
            boolean isIterable,
            boolean isSingleton,
            boolean isPrimary,
            boolean isConfigurationProperties,
            boolean isContainerType,
            boolean requiresMethodProcessing) {
        this(beanType,
                constructor,
                annotationMetadata,
                methodInjection,
                fieldInjection,
                executableMethodsDefinition,
                typeArgumentsMap,
                scope, isAbstract,
                isProvided,
                isIterable,
                isSingleton,
                isPrimary,
                isConfigurationProperties,
                isContainerType,
                requiresMethodProcessing);
        this.annotationInjection = annotationInjection;
    }

    @Override
    public Qualifier<T> getDeclaredQualifier() {
        if (declaredQualifier == null) {
            declaredQualifier = BeanDefinition.super.getDeclaredQualifier();
        }
        return declaredQualifier;
    }

    @Override
    public final boolean isContainerType() {
        return isContainerType;
    }

    @Override
    public final Optional<Argument<?>> getContainerElement() {
        if (isContainerType) {
            if (containerElement != null) {
                return containerElement;
            }
            if (getBeanType().isArray()) {
                containerElement = Optional.of(Argument.of(getBeanType().getComponentType()));
            } else {
                final List<Argument<?>> iterableArguments = getTypeArguments(Iterable.class);
                if (!iterableArguments.isEmpty()) {
                    containerElement = Optional.of(iterableArguments.iterator().next());
                }
            }
            return containerElement;
        }
        return Optional.empty();
    }

    @Override
    public final boolean hasPropertyExpressions() {
        return getAnnotationMetadata().hasPropertyExpressions();
    }

    @Override
    public final @NonNull
    List<Argument<?>> getTypeArguments(String type) {
        if (type == null || typeArgumentsMap == null) {
            return Collections.emptyList();
        }
        Argument<?>[] arguments = typeArgumentsMap.get(type);
        if (arguments != null) {
            return Arrays.asList(arguments);
        }
        return Collections.emptyList();
    }

    @Override
    @NonNull
    public AnnotationMetadata getAnnotationMetadata() {
        return annotationMetadata;
    }

    @Override
    public boolean isAbstract() {
        return isAbstract;
    }

    @Override
    public boolean isIterable() {
        return isIterable;
    }

    @Override
    public boolean isPrimary() {
        return isPrimary;
    }

    @Override
    public boolean isProvided() {
        return isProvided;
    }

    @Override
    public boolean requiresMethodProcessing() {
        return requiresMethodProcessing;
    }

    @Override
    public final <R> Optional<ExecutableMethod<T, R>> findMethod(String name, Class<?>... argumentTypes) {
        if (executableMethodsDefinition == null) {
            return Optional.empty();
        }
        return executableMethodsDefinition.findMethod(name, argumentTypes);
    }

    @Override
    public final <R> Stream<ExecutableMethod<T, R>> findPossibleMethods(String name) {
        if (executableMethodsDefinition == null) {
            return Stream.empty();
        }
        return executableMethodsDefinition.findPossibleMethods(name);
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

    @Override
    public String toString() {
        Class declaringType = constructor == null ? type : constructor.declaringType;
        return "Definition: " + declaringType.getName();
    }

    @Override
    public boolean isSingleton() {
        return isSingleton;
    }

    @Override
    public final Optional<Class<? extends Annotation>> getScope() {
        return scope.flatMap(scopeClassName -> (Optional) ClassUtils.forName(scopeClassName, getClass().getClassLoader()));
    }

    @Override
    public final Optional<String> getScopeName() {
        return scope;
    }

    @Override
    public final Class<T> getBeanType() {
        return type;
    }

    @Override
    @NonNull
    public Set<Class<?>> getExposedTypes() {
        return Collections.EMPTY_SET;
    }

    @Override
    public final Optional<Class<?>> getDeclaringType() {
        if (constructor == null) {
            return Optional.of(type);
        }
        return Optional.of(constructor.declaringType);
    }

    @Override
    public final ConstructorInjectionPoint<T> getConstructor() {
        if (constructor == null) {
            constructorInjectionPoint = null;
        } else {
            if (constructor instanceof MethodReference) {
                MethodReference methodConstructor = (MethodReference) constructor;
                if ("<init>".equals(methodConstructor.methodName)) {
                    if (methodConstructor.requiresReflection) {
                        this.constructorInjectionPoint = new ReflectionConstructorInjectionPoint<>(
                                this,
                                methodConstructor.declaringType,
                                methodConstructor.annotationMetadata,
                                methodConstructor.arguments);
                    } else {
                        this.constructorInjectionPoint = new DefaultConstructorInjectionPoint<>(
                                this,
                                methodConstructor.declaringType,
                                methodConstructor.annotationMetadata,
                                methodConstructor.arguments
                        );
                    }
                } else {
                    if (methodConstructor.requiresReflection) {
                        this.constructorInjectionPoint = new ReflectionMethodConstructorInjectionPoint(
                                this,
                                methodConstructor.declaringType,
                                methodConstructor.methodName,
                                methodConstructor.arguments,
                                methodConstructor.annotationMetadata
                        );
                    } else {
                        this.constructorInjectionPoint = new DefaultMethodConstructorInjectionPoint(
                                this,
                                methodConstructor.declaringType,
                                methodConstructor.methodName,
                                methodConstructor.arguments,
                                methodConstructor.annotationMetadata
                        );
                    }
                }
            } else if (constructor instanceof FieldReference) {
                FieldReference fieldConstructor = (FieldReference) constructor;
                constructorInjectionPoint = new DefaultFieldConstructorInjectionPoint<>(
                        this,
                        fieldConstructor.declaringType,
                        type,
                        fieldConstructor.argument.getName(),
                        fieldConstructor.argument.getAnnotationMetadata()
                );
            }
            if (environment != null && constructorInjectionPoint instanceof EnvironmentConfigurable) {
                ((EnvironmentConfigurable) constructorInjectionPoint).configure(environment);
            }
        }
        return constructorInjectionPoint;
    }

    @Override
    public final Collection<Class<?>> getRequiredComponents() {
        if (requiredComponents != null) {
            return requiredComponents;
        }
        Set<Class<?>> requiredComponents = new HashSet<>();
        Consumer<Argument> argumentConsumer = argument -> {
            if (argument.isContainerType() || argument.isProvider()) {
                argument.getFirstTypeVariable()
                        .map(Argument::getType)
                        .ifPresent(requiredComponents::add);
            } else {
                requiredComponents.add(argument.getType());
            }
        };
        if (constructor != null) {
            if (constructor instanceof MethodReference) {
                MethodReference methodConstructor = (MethodReference) constructor;
                if (methodConstructor.arguments != null && methodConstructor.arguments.length > 0) {
                    for (Argument<?> argument : methodConstructor.arguments) {
                        argumentConsumer.accept(argument);
                    }
                }
            }
        }
        if (methodInjection != null) {
            for (MethodReference methodReference : methodInjection) {
                if (methodReference.arguments != null && methodReference.arguments.length > 0) {
                    for (Argument<?> argument : methodReference.arguments) {
                        argumentConsumer.accept(argument);
                    }
                }
            }
        }
        if (fieldInjection != null) {
            for (FieldReference fieldReference : fieldInjection) {
                if (annotationMetadata != null && annotationMetadata.hasDeclaredAnnotation(AnnotationUtil.INJECT)) {
                    argumentConsumer.accept(fieldReference.argument);
                }
            }
        }
        if (annotationInjection != null) {
            for (AnnotationReference annotationReference : annotationInjection) {
                if (annotationReference.argument != null) {
                    argumentConsumer.accept(annotationReference.argument);
                }
            }
        }
        this.requiredComponents = Collections.unmodifiableSet(requiredComponents);
        return this.requiredComponents;
    }

    @Override
    public final List<MethodInjectionPoint<T, ?>> getInjectedMethods() {
        if (methodInjection == null) {
            return Collections.emptyList();
        }
        if (methodInjectionPoints != null) {
            return methodInjectionPoints;
        }
        List<MethodInjectionPoint<T, ?>> methodInjectionPoints = new ArrayList<>(methodInjection.length);
        for (MethodReference methodReference : methodInjection) {
            MethodInjectionPoint<T, ?> methodInjectionPoint;
            if (methodReference.requiresReflection) {
                methodInjectionPoint = new ReflectionMethodInjectionPoint(
                        this,
                        methodReference.declaringType,
                        methodReference.methodName,
                        methodReference.arguments,
                        methodReference.annotationMetadata
                );
            } else {
                methodInjectionPoint = new DefaultMethodInjectionPoint<>(
                        this,
                        methodReference.declaringType,
                        methodReference.methodName,
                        methodReference.arguments,
                        methodReference.annotationMetadata
                );
            }
            methodInjectionPoints.add(methodInjectionPoint);
            if (environment != null) {
                ((EnvironmentConfigurable) methodInjectionPoint).configure(environment);
            }
        }
        this.methodInjectionPoints = Collections.unmodifiableList(methodInjectionPoints);
        return this.methodInjectionPoints;
    }

    @Override
    public final List<FieldInjectionPoint<T, ?>> getInjectedFields() {
        if (fieldInjection == null) {
            return Collections.emptyList();
        }
        if (fieldInjectionPoints != null) {
            return fieldInjectionPoints;
        }
        List<FieldInjectionPoint<T, ?>> fieldInjectionPoints = new ArrayList<>(fieldInjection.length);
        for (FieldReference fieldReference : fieldInjection) {
            FieldInjectionPoint<T, ?> fieldInjectionPoint;
            if (fieldReference.requiresReflection) {
                fieldInjectionPoint = new ReflectionFieldInjectionPoint<>(
                        this,
                        fieldReference.declaringType,
                        fieldReference.argument.getType(),
                        fieldReference.argument.getName(),
                        fieldReference.argument.getAnnotationMetadata(),
                        fieldReference.argument.getTypeParameters()
                );
            } else {
                fieldInjectionPoint = new DefaultFieldInjectionPoint<>(
                        this,
                        fieldReference.declaringType,
                        fieldReference.argument.getType(),
                        fieldReference.argument.getName(),
                        fieldReference.argument.getAnnotationMetadata(),
                        fieldReference.argument.getTypeParameters()
                );
            }
            if (environment != null) {
                ((EnvironmentConfigurable) fieldInjectionPoint).configure(environment);
            }
            fieldInjectionPoints.add(fieldInjectionPoint);
        }
        this.fieldInjectionPoints = Collections.unmodifiableList(fieldInjectionPoints);
        return this.fieldInjectionPoints;
    }

    @Override
    public final List<MethodInjectionPoint<T, ?>> getPostConstructMethods() {
        if (methodInjection == null) {
            return Collections.emptyList();
        }
        if (postConstructMethods != null) {
            return postConstructMethods;
        }
        List<MethodInjectionPoint<T, ?>> postConstructMethods = new ArrayList<>(1);
        for (MethodInjectionPoint<T, ?> methodInjectionPoint : getInjectedMethods()) {
            if (methodInjectionPoint.isPostConstructMethod()) {
                postConstructMethods.add(methodInjectionPoint);
            }
        }
        this.postConstructMethods = Collections.unmodifiableList(postConstructMethods);
        return this.postConstructMethods;
    }

    @Override
    public final List<MethodInjectionPoint<T, ?>> getPreDestroyMethods() {
        if (methodInjection == null) {
            return Collections.emptyList();
        }
        if (preDestroyMethods != null) {
            return preDestroyMethods;
        }
        List<MethodInjectionPoint<T, ?>> preDestroyMethods = new ArrayList<>(1);
        for (MethodInjectionPoint<T, ?> methodInjectionPoint : getInjectedMethods()) {
            if (methodInjectionPoint.isPreDestroyMethod()) {
                preDestroyMethods.add(methodInjectionPoint);
            }
        }
        this.preDestroyMethods = Collections.unmodifiableList(preDestroyMethods);
        return this.preDestroyMethods;
    }

    @Override
    @NonNull
    public final String getName() {
        return getBeanType().getName();
    }

    @Override
    public T inject(BeanContext context, T bean) {
        return (T) injectBean(new DefaultBeanResolutionContext(context, this), context, bean);
    }

    @Override
    public T inject(BeanResolutionContext resolutionContext, BeanContext context, T bean) {
        return (T) injectBean(resolutionContext, context, bean);
    }

    @Override
    public final Collection<ExecutableMethod<T, ?>> getExecutableMethods() {
        if (executableMethodsDefinition == null) {
            return Collections.emptyList();
        }
        return executableMethodsDefinition.getExecutableMethods();
    }

    /**
     * Configures the bean for the given {@link BeanContext}. If the context features an
     * {@link Environment} this method configures the annotation metadata such that
     * environment aware values are returned.
     *
     * @param environment The environment
     */
    @Internal
    @Override
    public final void configure(Environment environment) {
        if (environment != null) {
            this.environment = environment;
            if (constructorInjectionPoint instanceof EnvironmentConfigurable) {
                ((EnvironmentConfigurable) constructorInjectionPoint).configure(environment);
            }
            if (methodInjectionPoints != null) {
                for (MethodInjectionPoint<T, ?> methodInjectionPoint : methodInjectionPoints) {
                    if (methodInjectionPoint instanceof EnvironmentConfigurable) {
                        ((EnvironmentConfigurable) methodInjectionPoint).configure(environment);
                    }
                }
            }
            if (fieldInjectionPoints != null) {
                for (FieldInjectionPoint<T, ?> fieldInjectionPoint : fieldInjectionPoints) {
                    if (fieldInjectionPoint instanceof EnvironmentConfigurable) {
                        ((EnvironmentConfigurable) fieldInjectionPoint).configure(environment);
                    }
                }
            }
            if (executableMethodsDefinition instanceof EnvironmentConfigurable) {
                ((EnvironmentConfigurable) executableMethodsDefinition).configure(environment);
            }
        }
    }

    /**
     * Allows printing warning messages produced by the compiler.
     *
     * @param message The message
     */
    @Internal
    protected final void warn(String message) {
        if (LOG.isWarnEnabled()) {
            LOG.warn(message);
        }
    }

    /**
     * Allows printing warning messages produced by the compiler.
     *
     * @param type     The type
     * @param method   The method
     * @param property The property
     */
    @SuppressWarnings("unused")
    @Internal
    protected final void warnMissingProperty(Class type, String method, String property) {
        if (LOG.isWarnEnabled()) {
            LOG.warn("Configuration property [{}] could not be set as the underlying method [{}] does not exist on builder [{}]. This usually indicates the configuration option was deprecated and has been removed by the builder implementation (potentially a third-party library).", property, method, type);
        }
    }

    /**
     * Resolves the proxied bean instance for this bean.
     *
     * @param beanContext The {@link BeanContext}
     * @return The proxied bean
     */
    @SuppressWarnings({"unchecked", "unused"})
    @Internal
    protected final Object getProxiedBean(BeanContext beanContext) {
        DefaultBeanContext defaultBeanContext = (DefaultBeanContext) beanContext;
        Optional<String> qualifier = getAnnotationMetadata().getAnnotationNameByStereotype(AnnotationUtil.QUALIFIER);
        return defaultBeanContext.getProxyTargetBean(
                getBeanType(),
                (Qualifier<T>) qualifier.map(q -> Qualifiers.byAnnotation(getAnnotationMetadata(), q)).orElse(null)
        );
    }

    /**
     * Implementing possible {@link io.micronaut.inject.ParametrizedBeanFactory#getRequiredArguments()}.
     *
     * @return The arguments required to construct parametrized bean
     */
    public final Argument<?>[] getRequiredArguments() {
        if (requiredParametrizedArguments != null) {
            return requiredParametrizedArguments;
        }
        requiredParametrizedArguments = Arrays.stream(getConstructor().getArguments())
                .filter(arg -> {
                    Optional<String> qualifierType = arg.getAnnotationMetadata().getAnnotationNameByStereotype(AnnotationUtil.QUALIFIER);
                    return qualifierType.isPresent() && qualifierType.get().equals(Parameter.class.getName());
                })
                .toArray(Argument[]::new);
        return requiredParametrizedArguments;
    }

    /**
     * Implementing possible {@link io.micronaut.inject.ParametrizedBeanFactory#build(BeanResolutionContext, BeanContext, BeanDefinition)}.
     *
     * @param resolutionContext      The {@link BeanResolutionContext}
     * @param context                The {@link BeanContext}
     * @param definition             The {@link BeanDefinition}
     * @param requiredArgumentValues The required arguments values. The keys should match the names of the arguments
     *                               returned by {@link #getRequiredArguments()}
     * @return The instantiated bean
     * @throws BeanInstantiationException If the bean cannot be instantiated for the arguments supplied
     */
    public final T build(BeanResolutionContext resolutionContext,
                         BeanContext context,
                         BeanDefinition<T> definition,
                         Map<String, Object> requiredArgumentValues) throws BeanInstantiationException {

        requiredArgumentValues = requiredArgumentValues != null ? new LinkedHashMap<>(requiredArgumentValues) : Collections.emptyMap();
        Optional<Class> eachBeanType = null;
        for (Argument<?> requiredArgument : getRequiredArguments()) {
            try (BeanResolutionContext.Path ignored = resolutionContext.getPath().pushConstructorResolve(this, requiredArgument)) {
                String argumentName = requiredArgument.getName();
                Object value = requiredArgumentValues.get(argumentName);
                if (value == null && !requiredArgument.isNullable()) {
                    if (eachBeanType == null) {
                        eachBeanType = definition.classValue(EachBean.class);
                    }
                    if (eachBeanType.filter(type -> type == requiredArgument.getType()).isPresent()) {
                        throw new DisabledBeanException("@EachBean parameter disabled for argument: " + requiredArgument.getName());
                    }
                    throw new BeanInstantiationException(resolutionContext, "Missing bean argument value: " + argumentName);
                }
                boolean requiresConversion = value != null && !requiredArgument.getType().isInstance(value);
                if (requiresConversion) {
                    Optional<?> converted = ConversionService.SHARED.convert(value, requiredArgument.getType(), ConversionContext.of(requiredArgument));
                    Object finalValue = value;
                    value = converted.orElseThrow(() -> new BeanInstantiationException(resolutionContext, "Invalid value [" + finalValue + "] for argument: " + argumentName));
                    requiredArgumentValues.put(argumentName, value);
                }
            }
        }
        return doBuild(resolutionContext, context, definition, requiredArgumentValues);
    }

    /**
     * Method to be implemented by the generated code if the bean definition is implementing {@link io.micronaut.inject.ParametrizedBeanFactory}.
     *
     * @param resolutionContext      The resolution context
     * @param context                The bean context
     * @param definition             The bean definition
     * @param requiredArgumentValues The required arguments
     * @return The built instance
     */
    @Internal
    @UsedByGeneratedCode
    protected T doBuild(BeanResolutionContext resolutionContext, BeanContext context, BeanDefinition<T> definition, Map<String, Object> requiredArgumentValues) {
        throw new IllegalStateException("Method must be implemented for 'ParametrizedBeanFactory' instance!");
    }

    /**
     * The default implementation which provides no injection. To be overridden by compile time tooling.
     *
     * @param resolutionContext The resolution context
     * @param context           The bean context
     * @param bean              The bean
     * @return The injected bean
     */
    @Internal
    @SuppressWarnings({"WeakerAccess", "unused"})
    @UsedByGeneratedCode
    protected Object injectBean(BeanResolutionContext resolutionContext, BeanContext context, Object bean) {
        return bean;
    }

    /**
     * Inject another bean, for example one created via factory.
     *
     * @param resolutionContext The reslution context
     * @param context           The context
     * @param bean              The bean
     * @return The bean
     */
    @Internal
    @SuppressWarnings({"unused"})
    @UsedByGeneratedCode
    protected Object injectAnother(BeanResolutionContext resolutionContext, BeanContext context, Object bean) {
        if (bean == null) {
            throw new BeanInstantiationException(resolutionContext, "Bean factory returned null");
        }
        return resolutionContext.inject(this, bean);
    }

    /**
     * Default postConstruct hook that only invokes methods that require reflection. Generated subclasses should
     * override to call methods that don't require reflection.
     *
     * @param resolutionContext The resolution hook
     * @param context           The context
     * @param bean              The bean
     * @return The bean
     */
    @SuppressWarnings({"unused", "unchecked"})
    @Internal
    @UsedByGeneratedCode
    protected Object postConstruct(BeanResolutionContext resolutionContext, BeanContext context, Object bean) {
        final Set<Map.Entry<Class<?>, List<BeanInitializedEventListener>>> beanInitializedEventListeners
                = ((DefaultBeanContext) context).beanInitializedEventListeners;
        if (CollectionUtils.isNotEmpty(beanInitializedEventListeners)) {
            for (Map.Entry<Class<?>, List<BeanInitializedEventListener>> entry : beanInitializedEventListeners) {
                if (entry.getKey().isAssignableFrom(getBeanType())) {
                    for (BeanInitializedEventListener listener : entry.getValue()) {
                        bean = listener.onInitialized(new BeanInitializingEvent(context, this, bean));
                        if (bean == null) {
                            throw new BeanInstantiationException(resolutionContext, "Listener [" + listener + "] returned null from onInitialized event");
                        }
                    }
                }
            }
        }
        if (bean instanceof LifeCycle) {
            bean = ((LifeCycle) bean).start();
        }
        return bean;
    }

    /**
     * Default preDestroy hook that only invokes methods that require reflection. Generated subclasses should override
     * to call methods that don't require reflection.
     *
     * @param resolutionContext The resolution hook
     * @param context           The context
     * @param bean              The bean
     * @return The bean
     */
    @Internal
    @UsedByGeneratedCode
    protected Object preDestroy(BeanResolutionContext resolutionContext, BeanContext context, Object bean) {
        if (bean instanceof LifeCycle) {
            bean = ((LifeCycle) bean).stop();
        }
        return bean;
    }

    /**
     * Check if the class is an inner configuration.
     *
     * @param clazz The class to check
     * @return true if the inner configuration
     */
    @Internal
    @UsedByGeneratedCode
    protected boolean isInnerConfiguration(Class<?> clazz) {
        return false;
    }


    /**
     * Checks whether the bean should be loaded.
     *
     * @param resolutionContext - the resolution context
     * @param context           - the bean context
     */
    @Internal
    @UsedByGeneratedCode
    protected void checkIfShouldLoad(BeanResolutionContext resolutionContext, BeanContext context) {
    }

    /**
     * Check the value of the injected bean property to decide whether the
     * bean should be loaded.
     *
     * @param injectedBeanPropertyName the name of the injected bean property
     * @param beanPropertyValue        the value of injected bean property
     * @param requiredValue            the value which is required for the bean to be loaded
     * @param notEqualsValue           the value which bean property should not be equal to for the bean to be loaded
     */
    @Internal
    @UsedByGeneratedCode
    protected final void checkInjectedBeanPropertyValue(String injectedBeanPropertyName,
                                                        @Nullable Object beanPropertyValue,
                                                        @Nullable String requiredValue,
                                                        @Nullable String notEqualsValue) {
        if (beanPropertyValue instanceof Optional) {
            beanPropertyValue = ((Optional<?>) beanPropertyValue).orElse(null);
        }

        String convertedValue = ConversionService.SHARED.convert(beanPropertyValue, String.class).orElse(null);
        if (convertedValue == null && notEqualsValue == null) {
            throw new DisabledBeanException("Bean [" + getBeanType() + "] is disabled since required bean property [" + injectedBeanPropertyName + "] id not set");
        } else if (convertedValue != null) {
            if (requiredValue != null && !convertedValue.equals(requiredValue)) {
                throw new DisabledBeanException("Bean [" + getBeanType() + "] is disabled since bean property [" + injectedBeanPropertyName + "] " +
                        "value is not equal to [" + requiredValue + "]");
            } else if (requiredValue == null && convertedValue.equals(notEqualsValue)) {
                throw new DisabledBeanException("Bean [" + getBeanType() + "] is disabled since bean property [" + injectedBeanPropertyName + "] " +
                        "value is equal to [" + notEqualsValue + "]");
            }
        }
    }

    /**
     * Invoke a bean method that requires reflection.
     *
     * @param resolutionContext The resolution context
     * @param context           The bean context
     * @param methodIndex       The method index
     * @param bean              The bean
     * @param methodArgs        The method args
     */
    @Internal
    @SuppressWarnings("WeakerAccess")
    protected final void invokeMethodWithReflection(BeanResolutionContext resolutionContext, BeanContext context, int methodIndex, Object bean, Object[] methodArgs) {
        MethodReference methodRef = methodInjection[methodIndex];
        Argument[] methodArgumentTypes = methodRef.arguments == null ? Argument.ZERO_ARGUMENTS : methodRef.arguments;
        if (ClassUtils.REFLECTION_LOGGER.isDebugEnabled()) {
            ClassUtils.REFLECTION_LOGGER.debug("Bean of type [" + getBeanType() + "] uses reflection to inject method: '" + methodRef.methodName + "'");
        }
        try {
            Method method = ReflectionUtils.getMethod(
                    methodRef.declaringType,
                    methodRef.methodName,
                    Argument.toClassArray(methodArgumentTypes)
            ).orElseThrow(() -> ReflectionUtils.newNoSuchMethodError(methodRef.declaringType, methodRef.methodName, Argument.toClassArray(methodArgumentTypes)));
            method.setAccessible(true);
            ReflectionUtils.invokeMethod(bean, method, methodArgs);
        } catch (Throwable e) {
            if (e instanceof BeanContextException) {
                throw (BeanContextException) e;
            } else {
                throw new DependencyInjectionException(resolutionContext, "Error invoking method: " + methodRef.methodName, e);
            }
        }
    }

    /**
     * Sets the value of a field of a object that requires reflection.
     *
     * @param resolutionContext The resolution context
     * @param context           The object context
     * @param index             The index of the field
     * @param object            The object whose field should be modifie
     * @param value             The instance being set
     */
    @SuppressWarnings("unused")
    @Internal
    protected final void setFieldWithReflection(BeanResolutionContext resolutionContext, BeanContext context, int index, Object object, Object value) {
        FieldReference fieldRef = fieldInjection[index];
        try {
            if (ClassUtils.REFLECTION_LOGGER.isDebugEnabled()) {
                ClassUtils.REFLECTION_LOGGER.debug("Bean of type [" + getBeanType() + "] uses reflection to inject field: '" + fieldRef.argument.getName() + "'");
            }
            Field field = ReflectionUtils.getRequiredField(fieldRef.declaringType, fieldRef.argument.getName());
            field.setAccessible(true);
            field.set(object, value);
        } catch (Throwable e) {
            if (e instanceof BeanContextException) {
                throw (BeanContextException) e;
            } else {
                throw new DependencyInjectionException(resolutionContext, "Error setting field value: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Obtains a value for the given method argument.
     *
     * @param resolutionContext The resolution context
     * @param context           The bean context
     * @param methodIndex       The method index
     * @param argIndex          The argument index
     * @param qualifier         The qualifier
     * @return The value
     */
    @SuppressWarnings({"unused", "unchecked"})
    @Internal
    @Deprecated
    protected final Object getValueForMethodArgument(BeanResolutionContext resolutionContext, BeanContext context, int methodIndex, int argIndex, Qualifier qualifier) {
        MethodReference methodRef = methodInjection[methodIndex];
        Argument argument = methodRef.arguments[argIndex];
        try (BeanResolutionContext.Path ignored = resolutionContext.getPath()
                .pushMethodArgumentResolve(this, methodRef.methodName, argument, methodRef.arguments, methodRef.requiresReflection)) {
            return resolveValue(resolutionContext, context, methodRef.annotationMetadata, argument, qualifier);
        }
    }

    /**
     * Obtains a property value for the given method argument.
     *
     * @param resolutionContext The resolution context
     * @param context           The bean context
     * @param methodIndex       The method index
     * @param argIndex          The argument index
     * @param propertyValue     The property value
     * @param cliProperty       The cli property
     * @return The value
     */
    @SuppressWarnings({"unused"})
    @Internal
    protected final Object getPropertyValueForMethodArgument(BeanResolutionContext resolutionContext,
                                                             BeanContext context,
                                                             int methodIndex,
                                                             int argIndex,
                                                             String propertyValue,
                                                             String cliProperty) {
        MethodReference methodRef = methodInjection[methodIndex];
        Argument argument = methodRef.arguments[argIndex];
        try (BeanResolutionContext.Path ignored = resolutionContext.getPath()
                .pushMethodArgumentResolve(this, methodRef.methodName, argument, methodRef.arguments, methodRef.requiresReflection)) {
            return resolvePropertyValue(resolutionContext, context, argument, propertyValue, cliProperty, false);
        }
    }

    /**
     * Obtains a placeholder value for the given method argument.
     *
     * @param resolutionContext The resolution context
     * @param context           The bean context
     * @param methodIndex       The method index
     * @param argIndex          The argument index
     * @param value             The property value
     * @return The value
     */
    @SuppressWarnings({"unused"})
    @Internal
    protected final Object getPropertyPlaceholderValueForMethodArgument(BeanResolutionContext resolutionContext,
                                                                        BeanContext context,
                                                                        int methodIndex,
                                                                        int argIndex,
                                                                        String value) {
        MethodReference methodRef = methodInjection[methodIndex];
        Argument argument = methodRef.arguments[argIndex];
        try (BeanResolutionContext.Path ignored = resolutionContext.getPath()
                .pushMethodArgumentResolve(this, methodRef.methodName, argument, methodRef.arguments, methodRef.requiresReflection)) {
            return resolvePropertyValue(resolutionContext, context, argument, value, null, true);
        }
    }

    /**
     * Obtains a property value for the given method argument.
     *
     * @param resolutionContext The resolution context
     * @param context           The bean context
     * @param setterName        The setter name
     * @param argument          The argument
     * @param propertyValue     The property value
     * @param cliProperty       The cli property
     * @return The value
     */
    @SuppressWarnings({"unused"})
    @Internal
    protected final Object getPropertyValueForSetter(BeanResolutionContext resolutionContext,
                                                     BeanContext context,
                                                     String setterName,
                                                     Argument<?> argument,
                                                     String propertyValue,
                                                     String cliProperty) {
        try (BeanResolutionContext.Path ignored = resolutionContext.getPath()
                .pushMethodArgumentResolve(this, setterName, argument, new Argument[]{argument}, false)) {
            return resolvePropertyValue(resolutionContext, context, argument, propertyValue, cliProperty, false);
        }
    }

    /**
     * Obtains a placeholder value for the given method argument.
     *
     * @param resolutionContext The resolution context
     * @param context           The bean context
     * @param setterName        The setter name
     * @param argument          The argument
     * @param value             The value
     * @return The value
     */
    @SuppressWarnings({"unused"})
    @Internal
    protected final Object getPropertyPlaceholderValueForSetter(BeanResolutionContext resolutionContext,
                                                                BeanContext context,
                                                                String setterName,
                                                                Argument<?> argument,
                                                                String value) {
        try (BeanResolutionContext.Path ignored = resolutionContext.getPath()
                .pushMethodArgumentResolve(this, setterName, argument, new Argument[]{argument}, false)) {
            return resolvePropertyValue(resolutionContext, context, argument, value, null, true);
        }
    }

    /**
     * Obtains a value for the given method argument.
     *
     * @param resolutionContext The resolution context
     * @param context           The bean context
     * @param methodIndex       The method index
     * @param argIndex          The argument index
     * @param isValuePrefix     Is value prefix in cases when beans are requested
     * @return The value
     */
    @Internal
    @UsedByGeneratedCode
    @Deprecated
    protected final boolean containsValueForMethodArgument(BeanResolutionContext resolutionContext, BeanContext context, int methodIndex, int argIndex, boolean isValuePrefix) {
        MethodReference methodRef = methodInjection[methodIndex];
        AnnotationMetadata parentAnnotationMetadata = methodRef.annotationMetadata;
        Argument argument = methodRef.arguments[argIndex];
        return resolveContainsValue(resolutionContext, context, parentAnnotationMetadata, argument, isValuePrefix);
    }

    /**
     * Obtains a bean definition for the method at the given index and the argument at the given index
     * <p>
     * Warning: this method is used by internal generated code and should not be called by user code.
     *
     * @param resolutionContext The resolution context
     * @param context           The context
     * @param methodIndex       The method index
     * @param argIndex          The argument index
     * @param qualifier         The qualifier
     * @param <K>               The bean type
     * @return The resolved bean
     */
    @Internal
    @SuppressWarnings("WeakerAccess")
    @UsedByGeneratedCode
    protected final <K> K getBeanForMethodArgument(BeanResolutionContext resolutionContext, BeanContext context, int methodIndex, int argIndex, Qualifier<K> qualifier) {
        MethodReference methodRef = methodInjection[methodIndex];
        Argument<K> argument = resolveArgument(context, argIndex, methodRef.arguments);
        try (BeanResolutionContext.Path ignored = resolutionContext.getPath()
                .pushMethodArgumentResolve(this, methodRef.methodName, argument, methodRef.arguments, methodRef.requiresReflection)) {
            return resolveBean(resolutionContext, argument, qualifier, true);
        }
    }

    /**
     * Obtains all bean definitions for a method argument at the given index.
     *
     * @param resolutionContext The resolution context
     * @param context           The context
     * @param methodIndex       The method index
     * @param argumentIndex     The argument index
     * @param genericType       The generic type
     * @param qualifier         The qualifier
     * @param <K>               The bean type
     * @param <R>               The result collection type
     * @return The resolved bean
     */
    @Internal
    @UsedByGeneratedCode
    protected final <K, R extends Collection<K>> R getBeansOfTypeForMethodArgument(BeanResolutionContext resolutionContext, BeanContext context, int methodIndex, int argumentIndex, Argument<K> genericType, Qualifier<K> qualifier) {
        MethodReference methodRef = methodInjection[methodIndex];
        Argument<R> argument = resolveArgument(context, argumentIndex, methodRef.arguments);
        try (BeanResolutionContext.Path ignored =
                     resolutionContext.getPath().pushMethodArgumentResolve(this, methodRef.methodName, argument, methodRef.arguments, methodRef.requiresReflection)) {
            return resolveBeansOfType(resolutionContext, context, argument, resolveEnvironmentArgument(context, genericType), qualifier);
        }
    }

    /**
     * Obtains a bean definition for the method at the given index and the argument at the given index
     * <p>
     * Warning: this method is used by internal generated code and should not be called by user code.
     *
     * @param resolutionContext The resolution context
     * @param context           The context
     * @param setterName        The setter name
     * @param argument          The argument
     * @param qualifier         The qualifier
     * @return The resolved bean
     */
    @Internal
    @SuppressWarnings("WeakerAccess")
    @UsedByGeneratedCode
    protected final Object getBeanForSetter(BeanResolutionContext resolutionContext, BeanContext context, String setterName, Argument argument, Qualifier qualifier) {
        try (BeanResolutionContext.Path ignored = resolutionContext.getPath()
                .pushMethodArgumentResolve(this, setterName, argument, new Argument[]{argument}, false)) {
            return resolveBean(resolutionContext, argument, qualifier, true);
        }
    }

    /**
     * Obtains all bean definitions for a method argument at the given index.
     *
     * @param resolutionContext The resolution context
     * @param context           The context
     * @param setterName        The setter name
     * @param argument          The argument
     * @param genericType       The generic type
     * @param qualifier         The qualifier
     * @return The resolved bean
     */
    @Internal
    @UsedByGeneratedCode
    protected final Collection<Object> getBeansOfTypeForSetter(BeanResolutionContext resolutionContext, BeanContext context, String setterName, Argument argument, Argument genericType, Qualifier qualifier) {
        try (BeanResolutionContext.Path ignored =
                     resolutionContext.getPath().pushMethodArgumentResolve(this, setterName, argument, new Argument[]{argument}, false)) {
            return resolveBeansOfType(resolutionContext, context, argument, resolveEnvironmentArgument(context, genericType), qualifier);
        }
    }

    /**
     * Obtains an optional bean for the method at the given index and the argument at the given index
     * <p>
     * Warning: this method is used by internal generated code and should not be called by user code.
     *
     * @param resolutionContext The resolution context
     * @param context           The context
     * @param methodIndex       The method index
     * @param argIndex          The argument index
     * @param genericType       The generic type
     * @param qualifier         The qualifier
     * @param <K>               The bean type
     * @return The resolved bean
     */
    @Internal
    @UsedByGeneratedCode
    protected final <K> Optional<K> findBeanForMethodArgument(BeanResolutionContext resolutionContext, BeanContext context, int methodIndex, int argIndex, Argument<K> genericType, Qualifier<K> qualifier) {
        MethodReference methodRef = methodInjection[methodIndex];
        Argument<K> argument = resolveArgument(context, argIndex, methodRef.arguments);
        try (BeanResolutionContext.Path ignored =
                     resolutionContext.getPath().pushMethodArgumentResolve(this, methodRef.methodName, argument, methodRef.arguments, methodRef.requiresReflection)) {
            return resolveOptionalBean(resolutionContext, argument, resolveEnvironmentArgument(context, genericType), qualifier);
        }
    }

    /**
     * Obtains all bean definitions for the method at the given index and the argument at the given index
     * <p>
     * Warning: this method is used by internal generated code and should not be called by user code.
     *
     * @param resolutionContext The resolution context
     * @param context           The context
     * @param methodIndex       The method index
     * @param argIndex          The argument index
     * @param genericType       The generic type
     * @param qualifier         The qualifier
     * @return The resolved bean
     */
    @Internal
    @UsedByGeneratedCode
    protected final Stream<?> getStreamOfTypeForMethodArgument(BeanResolutionContext resolutionContext, BeanContext context, int methodIndex, int argIndex, Argument genericType, Qualifier qualifier) {
        MethodReference methodRef = methodInjection[methodIndex];
        Argument<?> argument = resolveArgument(context, argIndex, methodRef.arguments);
        try (BeanResolutionContext.Path ignored =
                     resolutionContext.getPath().pushMethodArgumentResolve(this, methodRef.methodName, argument, methodRef.arguments, methodRef.requiresReflection)) {
            return resolveStreamOfType(resolutionContext, argument, resolveEnvironmentArgument(context, genericType), qualifier);
        }
    }

    /**
     * Obtains a bean definition for a constructor at the given index
     * <p>
     * Warning: this method is used by internal generated code and should not be called by user code.
     *
     * @param resolutionContext The resolution context
     * @param context           The context
     * @param argIndex          The argument index
     * @param qualifier         The qualifier
     * @return The resolved bean
     */
    @Internal
    @UsedByGeneratedCode
    protected final Object getBeanForConstructorArgument(BeanResolutionContext resolutionContext, BeanContext context, int argIndex, Qualifier qualifier) {
        MethodReference constructorMethodRef = (MethodReference) constructor;
        Argument<?> argument = resolveArgument(context, argIndex, constructorMethodRef.arguments);
        if (argument.isDeclaredNullable()) {
            BeanResolutionContext.Segment current = resolutionContext.getPath().peek();
            if (current != null && current.getArgument().equals(argument)) {
                return null;
            }
        }
        try (BeanResolutionContext.Path ignored = resolutionContext.getPath()
                .pushConstructorResolve(this, argument)) {
            return resolveBean(resolutionContext, argument, qualifier, true);
        }
    }

    /**
     * Obtains a value for a bean definition for a constructor at the given index
     * <p>
     * Warning: this method is used by internal generated code and should not be called by user code.
     *
     * @param resolutionContext The resolution context
     * @param context           The context
     * @param argIndex          The argument index
     * @param qualifier         The qualifier
     * @return The resolved bean
     */
    @Internal
    @UsedByGeneratedCode
    @Deprecated
    protected final Object getValueForConstructorArgument(BeanResolutionContext resolutionContext, BeanContext context, int argIndex, Qualifier qualifier) {
        MethodReference constructorRef = (MethodReference) constructor;
        Argument<?> argument = constructorRef.arguments[argIndex];
        try (BeanResolutionContext.Path ignored = resolutionContext.getPath().pushConstructorResolve(this, argument)) {
            try {
                Object result = resolveValue(resolutionContext, context, constructorRef.annotationMetadata, argument, qualifier);

                if (this instanceof ValidatedBeanDefinition) {
                    ((ValidatedBeanDefinition) this).validateBeanArgument(
                            resolutionContext,
                            getConstructor(),
                            argument,
                            argIndex,
                            result
                    );
                }

                return result;
            } catch (NoSuchBeanException | BeanInstantiationException e) {
                throw new DependencyInjectionException(resolutionContext, argument, e);
            }
        }
    }

    /**
     * Obtains a property value for a bean definition for a constructor at the given index
     * <p>
     * Warning: this method is used by internal generated code and should not be called by user code.
     *
     * @param resolutionContext The resolution context
     * @param context           The context
     * @param argIndex          The argument index
     * @param propertyValue     The property value
     * @param cliProperty       The cli property
     * @return The resolved bean
     */
    @Internal
    @UsedByGeneratedCode
    protected final Object getPropertyValueForConstructorArgument(BeanResolutionContext resolutionContext,
                                                                  BeanContext context,
                                                                  int argIndex,
                                                                  String propertyValue,
                                                                  String cliProperty) {
        MethodReference constructorRef = (MethodReference) constructor;
        Argument<?> argument = constructorRef.arguments[argIndex];
        try (BeanResolutionContext.Path ignored = resolutionContext.getPath().pushConstructorResolve(this, argument)) {
            try {
                Object result = resolvePropertyValue(resolutionContext, context, argument, propertyValue, cliProperty, false);

                if (this instanceof ValidatedBeanDefinition) {
                    ((ValidatedBeanDefinition) this).validateBeanArgument(
                            resolutionContext,
                            getConstructor(),
                            argument,
                            argIndex,
                            result
                    );
                }

                return result;
            } catch (NoSuchBeanException | BeanInstantiationException e) {
                throw new DependencyInjectionException(resolutionContext, argument, e);
            }
        }
    }

    /**
     * Obtains a property value for a bean definition for a constructor at the given index
     * <p>
     * Warning: this method is used by internal generated code and should not be called by user code.
     *
     * @param resolutionContext The resolution context
     * @param context           The context
     * @param argIndex          The argument index
     * @param propertyValue     The property value
     * @return The resolved bean
     */
    @Internal
    @UsedByGeneratedCode
    protected final Object getPropertyPlaceholderValueForConstructorArgument(BeanResolutionContext resolutionContext,
                                                                             BeanContext context,
                                                                             int argIndex,
                                                                             String propertyValue) {
        MethodReference constructorRef = (MethodReference) constructor;
        Argument<?> argument = constructorRef.arguments[argIndex];
        try (BeanResolutionContext.Path ignored = resolutionContext.getPath().pushConstructorResolve(this, argument)) {
            try {
                Object result = resolvePropertyValue(resolutionContext, context, argument, propertyValue, null, true);

                if (this instanceof ValidatedBeanDefinition) {
                    ((ValidatedBeanDefinition) this).validateBeanArgument(
                            resolutionContext,
                            getConstructor(),
                            argument,
                            argIndex,
                            result
                    );
                }

                return result;
            } catch (NoSuchBeanException | BeanInstantiationException e) {
                throw new DependencyInjectionException(resolutionContext, argument, e);
            }
        }
    }

    /**
     * Obtains all bean definitions for a constructor argument at the given index.
     *
     * @param resolutionContext The resolution context
     * @param context           The context
     * @param argumentIndex     The argument index
     * @param genericType       The generic type
     * @param qualifier         The qualifier
     * @return The resolved bean
     */
    @Internal
    @UsedByGeneratedCode
    protected final Collection<Object> getBeansOfTypeForConstructorArgument(BeanResolutionContext resolutionContext, BeanContext context, int argumentIndex, Argument genericType, Qualifier qualifier) {
        MethodReference constructorMethodRef = (MethodReference) constructor;
        Argument argument = resolveArgument(context, argumentIndex, constructorMethodRef.arguments);
        try (BeanResolutionContext.Path ignored = resolutionContext.getPath().pushConstructorResolve(this, argument)) {
            return resolveBeansOfType(resolutionContext, context, argument, resolveEnvironmentArgument(context, genericType), qualifier);
        }
    }

    /**
     * Obtains all bean definitions for a constructor argument at the given index
     * <p>
     * Warning: this method is used by internal generated code and should not be called by user code.
     *
     * @param resolutionContext The resolution context
     * @param context           The context
     * @param argumentIndex     The argument index
     * @param genericType       The generic type
     * @param qualifier         The qualifier
     * @param <K>               The bean type
     * @param <R>               The result collection type
     * @return The resolved bean
     */
    @Internal
    @UsedByGeneratedCode
    protected final <K, R extends Collection<BeanRegistration<K>>> R getBeanRegistrationsForConstructorArgument(BeanResolutionContext resolutionContext, BeanContext context, int argumentIndex, Argument<K> genericType, Qualifier<K> qualifier) {
        MethodReference constructorMethodRef = (MethodReference) constructor;
        Argument<R> argument = resolveArgument(context, argumentIndex, constructorMethodRef.arguments);
        try (BeanResolutionContext.Path ignored = resolutionContext.getPath().pushConstructorResolve(this, argument)) {
            return resolveBeanRegistrations(resolutionContext, argument, resolveEnvironmentArgument(context, genericType), qualifier);
        }
    }

    /**
     * Obtains a bean registration for a method injection point.
     * <p>
     * Warning: this method is used by internal generated code and should not be called by user code.
     *
     * @param resolutionContext The resolution context
     * @param context           The context
     * @param argumentIndex     The arg index
     * @param genericType       The generic type
     * @param qualifier         The qualifier
     * @param <K>               The bean type
     * @return The resolved bean registration
     */
    @Internal
    @UsedByGeneratedCode
    protected final <K> BeanRegistration<K> getBeanRegistrationForConstructorArgument(BeanResolutionContext resolutionContext, BeanContext context, int argumentIndex, Argument<K> genericType, Qualifier<K> qualifier) {
        MethodReference constructorMethodRef = (MethodReference) constructor;
        Argument<K> argument = resolveArgument(context, argumentIndex, constructorMethodRef.arguments);
        try (BeanResolutionContext.Path ignored = resolutionContext.getPath().pushConstructorResolve(this, argument)) {
            return resolveBeanRegistration(resolutionContext, context, argument, resolveEnvironmentArgument(context, genericType), qualifier);
        }
    }

    /**
     * Obtains all bean definitions for a method injection point.
     * <p>
     * Warning: this method is used by internal generated code and should not be called by user code.
     *
     * @param resolutionContext The resolution context
     * @param context           The context
     * @param methodIndex       The method index
     * @param argIndex          The arg index
     * @param genericType       The generic type
     * @param qualifier         The qualifier
     * @param <K>               The bean type
     * @param <R>               The result collection type
     * @return The resolved bean
     */
    @Internal
    @UsedByGeneratedCode
    protected final <K, R extends Collection<BeanRegistration<K>>> R getBeanRegistrationsForMethodArgument(BeanResolutionContext resolutionContext, BeanContext context, int methodIndex, int argIndex, Argument<K> genericType, Qualifier<K> qualifier) {
        MethodReference methodReference = methodInjection[methodIndex];
        Argument<R> argument = resolveArgument(context, argIndex, methodReference.arguments);
        try (BeanResolutionContext.Path ignored = resolutionContext.getPath()
                .pushMethodArgumentResolve(this, methodReference.methodName, argument, methodReference.arguments, methodReference.requiresReflection)) {
            return resolveBeanRegistrations(resolutionContext, argument, resolveEnvironmentArgument(context, genericType), qualifier);
        }
    }

    /**
     * Obtains a bean registration for a method injection point.
     * <p>
     * Warning: this method is used by internal generated code and should not be called by user code.
     *
     * @param resolutionContext The resolution context
     * @param context           The context
     * @param methodIndex       The method index
     * @param argIndex          The arg index
     * @param genericType       The generic type
     * @param qualifier         The qualifier
     * @param <K>               The bean type
     * @return The resolved bean registration
     */
    @Internal
    @UsedByGeneratedCode
    protected final <K> BeanRegistration<K> getBeanRegistrationForMethodArgument(BeanResolutionContext resolutionContext, BeanContext context, int methodIndex, int argIndex, Argument<K> genericType, Qualifier<K> qualifier) {
        MethodReference methodRef = methodInjection[methodIndex];
        Argument<K> argument = resolveArgument(context, argIndex, methodRef.arguments);
        try (BeanResolutionContext.Path ignored = resolutionContext.getPath()
                .pushMethodArgumentResolve(this, methodRef.methodName, argument, methodRef.arguments, methodRef.requiresReflection)) {
            return resolveBeanRegistration(resolutionContext, context, argument, resolveEnvironmentArgument(context, genericType), qualifier);
        }
    }

    /**
     * Obtains all bean definitions for a constructor argument at the given index
     * <p>
     * Warning: this method is used by internal generated code and should not be called by user code.
     *
     * @param resolutionContext The resolution context
     * @param context           The context
     * @param argIndex          The argument index
     * @param genericType       The generic type
     * @param qualifier         The qualifier
     * @param <K>               The bean type
     * @return The resolved bean
     */
    @Internal
    @UsedByGeneratedCode
    protected final <K> Stream<K> getStreamOfTypeForConstructorArgument(BeanResolutionContext resolutionContext, BeanContext context, int argIndex, Argument<K> genericType, Qualifier<K> qualifier) {
        MethodReference constructorMethodRef = (MethodReference) constructor;
        Argument<K> argument = resolveArgument(context, argIndex, constructorMethodRef.arguments);
        try (BeanResolutionContext.Path ignored = resolutionContext.getPath().pushConstructorResolve(this, argument)) {
            return resolveStreamOfType(resolutionContext, argument, resolveEnvironmentArgument(context, genericType), qualifier);
        }
    }

    /**
     * Obtains all bean definitions for a constructor argument at the given index
     * <p>
     * Warning: this method is used by internal generated code and should not be called by user code.
     *
     * @param resolutionContext The resolution context
     * @param context           The context
     * @param argIndex          The argument index
     * @param genericType       The generic type
     * @param qualifier         The qualifier
     * @param <K>               The bean type
     * @return The resolved bean
     */
    @Internal
    @UsedByGeneratedCode
    protected final <K> Optional<K> findBeanForConstructorArgument(BeanResolutionContext resolutionContext, BeanContext context, int argIndex, Argument<K> genericType, Qualifier<K> qualifier) {
        MethodReference constructorMethodRef = (MethodReference) constructor;
        Argument<K> argument = resolveArgument(context, argIndex, constructorMethodRef.arguments);
        try (BeanResolutionContext.Path ignored = resolutionContext.getPath().pushConstructorResolve(this, argument)) {
            return resolveOptionalBean(resolutionContext, argument, resolveEnvironmentArgument(context, genericType), qualifier);
        }
    }

    /**
     * Obtains a bean definition for the field at the given index and the argument at the given index
     * <p>
     * Warning: this method is used by internal generated code and should not be called by user code.
     *
     * @param resolutionContext The resolution context
     * @param context           The context
     * @param fieldIndex        The field index
     * @param qualifier         The qualifier
     * @param <K>               The bean type
     * @return The resolved bean
     */
    @Internal
    @UsedByGeneratedCode
    protected final <K> K getBeanForField(BeanResolutionContext resolutionContext, BeanContext context, int fieldIndex, Qualifier<K> qualifier) {
        final Argument<K> argument = resolveEnvironmentArgument(context, fieldInjection[fieldIndex].argument);
        try (BeanResolutionContext.Path ignored = resolutionContext.getPath()
                .pushFieldResolve(this, argument, fieldInjection[fieldIndex].requiresReflection)) {
            return resolveBean(resolutionContext, argument, qualifier, true);
        }
    }

    @Internal
    @UsedByGeneratedCode
    protected final <K> K getBeanForAnnotation(BeanResolutionContext resolutionContext, BeanContext context, int annotationBeanIndex, Qualifier<K> qualifier) {
        final Argument<K> argument = resolveEnvironmentArgument(context, annotationInjection[annotationBeanIndex].argument);
        try (BeanResolutionContext.Path ignored = resolutionContext.getPath()
                .pushAnnotationResolve(this, argument)) {
            return resolveBean(resolutionContext, argument, qualifier);
        }
    }

    /**
     * Obtains a value for the given field from the bean context
     * <p>
     * Warning: this method is used by internal generated code and should not be called by user code.
     *
     * @param resolutionContext The resolution context
     * @param context           The context
     * @param fieldIndex        The index of the field
     * @param qualifier         The qualifier
     * @return The resolved bean
     */
    @Internal
    @UsedByGeneratedCode
    @Deprecated
    protected final Object getValueForField(BeanResolutionContext resolutionContext, BeanContext context, int fieldIndex, Qualifier qualifier) {
        FieldReference fieldRef = fieldInjection[fieldIndex];
        try (BeanResolutionContext.Path ignored = resolutionContext.getPath().pushFieldResolve(this, fieldRef.argument, fieldRef.requiresReflection)) {
            return resolveValue(resolutionContext, context, fieldRef.argument.getAnnotationMetadata(), fieldRef.argument, qualifier);
        }
    }

    /**
     * Obtains a property value for the given field from the bean context
     * <p>
     * Warning: this method is used by internal generated code and should not be called by user code.
     *
     * @param resolutionContext The resolution context
     * @param context           The context
     * @param argument          The argument
     * @param propertyValue     The property value
     * @param cliProperty       The clie property name
     * @return The resolved bean
     */
    @Internal
    @UsedByGeneratedCode
    @Deprecated
    protected final Object getPropertyValueForField(BeanResolutionContext resolutionContext, BeanContext context, Argument argument, String propertyValue, String cliProperty) {
        try (BeanResolutionContext.Path ignored = resolutionContext.getPath().pushFieldResolve(this, argument, false)) {
            return resolvePropertyValue(resolutionContext, context, argument, propertyValue, cliProperty, false);
        }
    }

    /**
     * Obtains a property placeholder value for the given field from the bean context
     * <p>
     * Warning: this method is used by internal generated code and should not be called by user code.
     *
     * @param resolutionContext The resolution context
     * @param context           The context
     * @param argument          The argument
     * @param placeholder       The placeholder
     * @return The resolved bean
     */
    @Internal
    @UsedByGeneratedCode
    @Deprecated
    protected final Object getPropertyPlaceholderValueForField(BeanResolutionContext resolutionContext, BeanContext context, Argument argument, String placeholder) {
        try (BeanResolutionContext.Path ignored = resolutionContext.getPath().pushFieldResolve(this, argument, false)) {
            return resolvePropertyValue(resolutionContext, context, argument, placeholder, null, true);
        }
    }

    /**
     * Resolve a value for the given field of the given type and path.
     *
     * @param resolutionContext The resolution context
     * @param context           The bean context
     * @param propertyType      The required property type
     * @param propertyPath      The property path
     * @param <T1>              The generic type
     * @return An optional value
     */
    @Internal
    @UsedByGeneratedCode
    protected final <T1> Optional<T1> getValueForPath(
            BeanResolutionContext resolutionContext,
            BeanContext context,
            Argument<T1> propertyType,
            String propertyPath) {
        if (context instanceof PropertyResolver) {
            PropertyResolver propertyResolver = (PropertyResolver) context;
            String valString = substituteWildCards(resolutionContext, propertyPath);

            return propertyResolver.getProperty(valString, ConversionContext.of(propertyType));
        }
        return Optional.empty();
    }

    /**
     * Obtains a value for the given field argument.
     *
     * @param resolutionContext The resolution context
     * @param context           The bean context
     * @param fieldIndex        The field index
     * @param isValuePrefix     Is value prefix in cases when beans are requested
     * @return True if it does
     */
    @Internal
    @UsedByGeneratedCode
    @Deprecated
    protected final boolean containsValueForField(BeanResolutionContext resolutionContext, BeanContext context, int fieldIndex, boolean isValuePrefix) {
        FieldReference fieldRef = fieldInjection[fieldIndex];
        return resolveContainsValue(resolutionContext, context, fieldRef.argument.getAnnotationMetadata(), fieldRef.argument, isValuePrefix);
    }

    /**
     * If this bean is a {@link ConfigurationProperties} bean return whether any properties for it are configured
     * within the context.
     *
     * @param resolutionContext the resolution context
     * @param context           The context
     * @return True if it does
     */
    @Internal
    @UsedByGeneratedCode
    protected final boolean containsProperties(BeanResolutionContext resolutionContext, BeanContext context) {
        return containsProperties(resolutionContext, context, null);
    }

    /**
     * If this bean is a {@link ConfigurationProperties} bean return whether any properties for it are configured
     * within the context.
     *
     * @param resolutionContext the resolution context
     * @param context           The context
     * @param subProperty       The subproperty to check
     * @return True if it does
     */
    @Internal
    @UsedByGeneratedCode
    protected final boolean containsProperties(@SuppressWarnings("unused") BeanResolutionContext resolutionContext, BeanContext context, String subProperty) {
        return isConfigurationProperties;
    }

    /**
     * Obtains all bean definitions for the field at the given index.
     *
     * @param resolutionContext The resolution context
     * @param context           The context
     * @param fieldIndex        The field index
     * @param genericType       The generic type
     * @param qualifier         The qualifier
     * @param <K>               The bean type
     * @param <R>               The result collection type
     * @return The resolved bean
     */
    @Internal
    @UsedByGeneratedCode
    protected final <K, R extends Collection<K>> Object getBeansOfTypeForField(BeanResolutionContext resolutionContext, BeanContext context, int fieldIndex, Argument<K> genericType, Qualifier<K> qualifier) {
        // Keep Object type for backwards compatibility
        final FieldReference fieldRef = fieldInjection[fieldIndex];
        final Argument<R> argument = resolveEnvironmentArgument(context, fieldRef.argument);
        try (BeanResolutionContext.Path ignored = resolutionContext.getPath().pushFieldResolve(this, argument, fieldRef.requiresReflection)) {
            return resolveBeansOfType(resolutionContext, context, argument, resolveEnvironmentArgument(context, genericType), qualifier);
        }
    }

    /**
     * Obtains all bean definitions for a field injection point.
     * <p>
     * Warning: this method is used by internal generated code and should not be called by user code.
     *
     * @param resolutionContext The resolution context
     * @param context           The context
     * @param fieldIndex        The field index
     * @param genericType       The generic type
     * @param qualifier         The qualifier
     * @param <K>               The bean type
     * @param <R>               The result collection type
     * @return The resolved bean
     */
    @Internal
    @UsedByGeneratedCode
    protected final <K, R extends Collection<BeanRegistration<K>>> R getBeanRegistrationsForField(BeanResolutionContext resolutionContext, BeanContext context, int fieldIndex, Argument<K> genericType, Qualifier<K> qualifier) {
        FieldReference fieldRef = fieldInjection[fieldIndex];
        Argument<R> argument = resolveEnvironmentArgument(context, fieldRef.argument);
        try (BeanResolutionContext.Path ignored = resolutionContext.getPath()
                .pushFieldResolve(this, argument, fieldRef.requiresReflection)) {
            return resolveBeanRegistrations(resolutionContext, argument, resolveEnvironmentArgument(context, genericType), qualifier);
        }
    }

    /**
     * Obtains a bean registration for a field injection point.
     * <p>
     * Warning: this method is used by internal generated code and should not be called by user code.
     *
     * @param resolutionContext The resolution context
     * @param context           The context
     * @param fieldIndex        The field index
     * @param genericType       The generic type
     * @param qualifier         The qualifier
     * @param <K>               The bean type
     * @return The resolved bean registration
     */
    @Internal
    @UsedByGeneratedCode
    protected final <K> BeanRegistration<K> getBeanRegistrationForField(BeanResolutionContext resolutionContext, BeanContext context, int fieldIndex, Argument<K> genericType, Qualifier<K> qualifier) {
        FieldReference fieldRef = fieldInjection[fieldIndex];
        Argument<K> argument = resolveEnvironmentArgument(context, fieldRef.argument);
        try (BeanResolutionContext.Path ignored = resolutionContext.getPath()
                .pushFieldResolve(this, argument, fieldRef.requiresReflection)) {
            return resolveBeanRegistration(resolutionContext, context, argument, resolveEnvironmentArgument(context, genericType), qualifier);
        }
    }

    /**
     * Obtains a an optional for the field at the given index and the argument at the given index
     * <p>
     * Warning: this method is used by internal generated code and should not be called by user code.
     *
     * @param resolutionContext The resolution context
     * @param context           The context
     * @param fieldIndex        The field index
     * @param genericType       The generic type
     * @param qualifier         The qualifier
     * @param <K>               The bean type
     * @return The resolved bean
     */
    @Internal
    @UsedByGeneratedCode
    protected final <K> Optional<K> findBeanForField(BeanResolutionContext resolutionContext, BeanContext context, int fieldIndex, Argument<K> genericType, Qualifier<K> qualifier) {
        FieldReference fieldRef = fieldInjection[fieldIndex];
        Argument<K> argument = resolveEnvironmentArgument(context, fieldRef.argument);
        try (BeanResolutionContext.Path ignored = resolutionContext.getPath()
                .pushFieldResolve(this, argument, fieldRef.requiresReflection)) {
            return resolveOptionalBean(resolutionContext, argument, resolveEnvironmentArgument(context, genericType), qualifier);
        }
    }

    /**
     * Obtains a bean definition for the field at the given index and the argument at the given index
     * <p>
     * Warning: this method is used by internal generated code and should not be called by user code.
     *
     * @param resolutionContext The resolution context
     * @param context           The context
     * @param fieldIndex        The field index
     * @param genericType       The generic type
     * @param qualifier         The qualifier
     * @param <K>               The bean type
     * @return The resolved bean
     */
    @Internal
    @UsedByGeneratedCode
    protected final <K> Stream<K> getStreamOfTypeForField(BeanResolutionContext resolutionContext, BeanContext context, int fieldIndex, Argument<K> genericType, Qualifier<K> qualifier) {
        FieldReference fieldRef = fieldInjection[fieldIndex];
        Argument<K> argument = resolveEnvironmentArgument(context, fieldRef.argument);
        try (BeanResolutionContext.Path ignored = resolutionContext.getPath()
                .pushFieldResolve(this, argument, fieldRef.requiresReflection)) {
            return resolveStreamOfType(resolutionContext, argument, resolveEnvironmentArgument(context, genericType), qualifier);
        }
    }

    @Internal
    @UsedByGeneratedCode
    protected final boolean containsPropertiesValue(BeanResolutionContext resolutionContext, BeanContext context, String value) {
        if (!(context instanceof ApplicationContext)) {
            return false;
        }
        value = substituteWildCards(resolutionContext, value);

        ApplicationContext applicationContext = (ApplicationContext) context;
        return applicationContext.containsProperties(value);
    }

    @Internal
    @UsedByGeneratedCode
    protected final boolean containsPropertyValue(BeanResolutionContext resolutionContext, BeanContext context, String value) {
        if (!(context instanceof ApplicationContext)) {
            return false;
        }
        value = substituteWildCards(resolutionContext, value);

        ApplicationContext applicationContext = (ApplicationContext) context;
        return applicationContext.containsProperty(value);
    }

    private boolean resolveContainsValue(BeanResolutionContext resolutionContext, BeanContext context, AnnotationMetadata parentAnnotationMetadata, Argument argument, boolean isValuePrefix) {
        if (!(context instanceof ApplicationContext)) {
            return false;
        }
        ApplicationContext applicationContext = (ApplicationContext) context;
        String valueAnnStr = argument.getAnnotationMetadata().stringValue(Value.class).orElse(null);
        String valString = resolvePropertyValueName(resolutionContext, parentAnnotationMetadata, argument, valueAnnStr);
        boolean result = isValuePrefix ? applicationContext.containsProperties(valString) : applicationContext.containsProperty(valString);
        if (!result && isConfigurationProperties) {
            String cliOption = resolveCliOption(argument.getName());
            if (cliOption != null) {
                result = applicationContext.containsProperty(cliOption);
            }
        }
        return result;
    }

    private Object resolveValue(BeanResolutionContext resolutionContext, BeanContext context, AnnotationMetadata parentAnnotationMetadata, Argument<?> argument, Qualifier qualifier) {
        if (!(context instanceof PropertyResolver)) {
            throw new DependencyInjectionException(resolutionContext, "@Value requires a BeanContext that implements PropertyResolver");
        }
        String valueAnnVal = argument.getAnnotationMetadata().stringValue(Value.class).orElse(null);
        Argument<?> argumentType;
        boolean isCollection = false;
        final boolean wrapperType = argument.isWrapperType();
        final Class<?> argumentJavaType = argument.getType();
        if (Collection.class.isAssignableFrom(argumentJavaType)) {
            argumentType = argument.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
            isCollection = true;
        } else if (wrapperType) {
            argumentType = argument.getWrappedType();
        } else {
            argumentType = argument;
        }
        if (isInnerConfiguration(argumentType.getType())) {
            qualifier = qualifier == null ? resolveQualifierWithInnerConfiguration(resolutionContext, argument, true) : qualifier;
            if (isCollection) {
                Collection<?> beans = resolutionContext.getBeansOfType(argumentType, qualifier);
                return coerceCollectionToCorrectType((Class) argumentJavaType, beans, resolutionContext, argument);
            } else {
                return resolutionContext.getBean(argumentType, qualifier);
            }
        } else {
            String valString = resolvePropertyValueName(resolutionContext, parentAnnotationMetadata, argument.getAnnotationMetadata(), valueAnnVal);
            ArgumentConversionContext conversionContext = wrapperType ? ConversionContext.of(argumentType) : ConversionContext.of(argument);
            Optional value = resolveValue((ApplicationContext) context, conversionContext, valueAnnVal != null, valString);
            if (argument.isOptional()) {
                if (!value.isPresent()) {
                    return value;
                } else {
                    Object convertedOptional = value.get();
                    if (convertedOptional instanceof Optional) {
                        return convertedOptional;
                    } else {
                        return value;
                    }
                }
            } else {
                if (wrapperType) {
                    final Object v = value.orElse(null);
                    if (OptionalInt.class == argumentJavaType) {
                        return v instanceof Integer ? OptionalInt.of((Integer) v) : OptionalInt.empty();
                    } else if (OptionalLong.class == argumentJavaType) {
                        return v instanceof Long ? OptionalLong.of((Long) v) : OptionalLong.empty();
                    } else if (OptionalDouble.class == argumentJavaType) {
                        return v instanceof Double ? OptionalDouble.of((Double) v) : OptionalDouble.empty();
                    }
                }
                if (value.isPresent()) {
                    return value.get();
                } else {
                    if (argument.isDeclaredNullable()) {
                        return null;
                    }
                    return argument.getAnnotationMetadata().getValue(Bindable.class, "defaultValue", argument)
                            .orElseThrow(() -> DependencyInjectionException.missingProperty(resolutionContext, conversionContext, valString));
                }
            }
        }
    }

    private Object resolvePropertyValue(BeanResolutionContext resolutionContext, BeanContext context, Argument<?> argument,
                                        String stringValue, String cliProperty, boolean isPlaceholder) {
        if (!(context instanceof PropertyResolver)) {
            throw new DependencyInjectionException(resolutionContext, "@Value requires a BeanContext that implements PropertyResolver");
        }
        ApplicationContext applicationContext = (ApplicationContext) context;

        Argument<?> argumentType = argument;
        Class<?> wrapperType = null;
        Class<?> type = argument.getType();
        if (type == Optional.class) {
            wrapperType = Optional.class;
            argumentType = argument.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
        } else if (type == OptionalInt.class) {
            wrapperType = OptionalInt.class;
            argumentType = Argument.INT;
        } else if (type == OptionalLong.class) {
            wrapperType = OptionalLong.class;
            argumentType = Argument.LONG;
        } else if (type == OptionalDouble.class) {
            wrapperType = OptionalDouble.class;
            argumentType = Argument.DOUBLE;
        }

        ArgumentConversionContext<?> conversionContext = wrapperType != null ? ConversionContext.of(argumentType) : ConversionContext.of(argument);

        Optional<?> value;
        if (isPlaceholder) {
            value = applicationContext.resolvePlaceholders(stringValue).flatMap(v -> applicationContext.getConversionService().convert(v, conversionContext));
        } else {
            stringValue = substituteWildCards(resolutionContext, stringValue);
            value = applicationContext.getProperty(stringValue, conversionContext);
            if (!value.isPresent() && cliProperty != null) {
                value = applicationContext.getProperty(cliProperty, conversionContext);
            }
        }

        if (argument.isOptional()) {
            if (!value.isPresent()) {
                return value;
            } else {
                Object convertedOptional = value.get();
                if (convertedOptional instanceof Optional) {
                    return convertedOptional;
                } else {
                    return value;
                }
            }
        } else {
            if (wrapperType != null) {
                final Object v = value.orElse(null);
                if (OptionalInt.class == wrapperType) {
                    return v instanceof Integer ? OptionalInt.of((Integer) v) : OptionalInt.empty();
                } else if (OptionalLong.class == wrapperType) {
                    return v instanceof Long ? OptionalLong.of((Long) v) : OptionalLong.empty();
                } else if (OptionalDouble.class == wrapperType) {
                    return v instanceof Double ? OptionalDouble.of((Double) v) : OptionalDouble.empty();
                }
            }
            if (value.isPresent()) {
                return value.get();
            } else {
                if (argument.isDeclaredNullable()) {
                    return null;
                }
                String finalStringValue = stringValue;
                return argument.getAnnotationMetadata().getValue(Bindable.class, "defaultValue", argument)
                        .orElseThrow(() -> DependencyInjectionException.missingProperty(resolutionContext, conversionContext, finalStringValue));
            }
        }
    }

    private <K> K resolveBean(BeanResolutionContext resolutionContext, Argument<K> argument, @Nullable Qualifier<K> qualifier) {
        return resolveBean(resolutionContext, argument, qualifier, false);
    }

    private <K> K resolveBean(
            BeanResolutionContext resolutionContext,
            Argument<K> argument,
            @Nullable Qualifier<K> qualifier,
            boolean resolveIsInnerConfiguration) {
        qualifier = qualifier == null ? resolveQualifier(resolutionContext, argument, resolveIsInnerConfiguration) : qualifier;
        if (Qualifier.class.isAssignableFrom(argument.getType())) {
            return (K) qualifier;
        }
        try {
            Object previous = !argument.isAnnotationPresent(Parameter.class) ? resolutionContext.removeAttribute(NAMED_ATTRIBUTE) : null;
            try {
                //noinspection unchecked
                return resolutionContext.getBean(argument, qualifier);
            } finally {
                if (previous != null) {
                    resolutionContext.setAttribute(NAMED_ATTRIBUTE, previous);
                }
            }
        } catch (DisabledBeanException e) {
            if (AbstractBeanContextConditional.LOG.isDebugEnabled()) {
                AbstractBeanContextConditional.LOG.debug("Bean of type [{}] disabled for reason: {}", argument.getTypeName(), e.getMessage());
            }
            if (isIterable() && getAnnotationMetadata().hasDeclaredAnnotation(EachBean.class)) {
                throw new DisabledBeanException("Bean [" + getBeanType().getSimpleName() + "] disabled by parent: " + e.getMessage());
            } else {
                if (argument.isDeclaredNullable()) {
                    return null;
                }
                throw new DependencyInjectionException(resolutionContext, e);
            }
        } catch (NoSuchBeanException e) {
            if (argument.isDeclaredNullable()) {
                return null;
            }
            throw new DependencyInjectionException(resolutionContext, e);
        }
    }

    private <K> Optional<K> resolveValue(
            ApplicationContext context,
            ArgumentConversionContext<K> argument,
            boolean hasValueAnnotation,
            String valString) {

        if (hasValueAnnotation) {
            return context.resolvePlaceholders(valString).flatMap(v -> context.getConversionService().convert(v, argument));
        } else {
            Optional<K> value = context.getProperty(valString, argument);
            if (!value.isPresent() && isConfigurationProperties) {
                String cliOption = resolveCliOption(argument.getArgument().getName());
                if (cliOption != null) {
                    return context.getProperty(cliOption, argument);
                }
            }
            return value;
        }
    }

    private String resolvePropertyValueName(BeanResolutionContext resolutionContext, AnnotationMetadata parentAnnotationMetadata, Argument argument, String valueAnnStr) {
        return resolvePropertyValueName(resolutionContext, parentAnnotationMetadata, argument.getAnnotationMetadata(), valueAnnStr);
    }

    private String resolvePropertyValueName(BeanResolutionContext resolutionContext, AnnotationMetadata parentAnnotationMetadata, AnnotationMetadata annotationMetadata, String valueAnnStr) {
        if (valueAnnStr != null) {
            return valueAnnStr;
        }
        String valString = getProperty(resolutionContext, parentAnnotationMetadata, annotationMetadata);
        return substituteWildCards(resolutionContext, valString);
    }

    private String getProperty(BeanResolutionContext resolutionContext, AnnotationMetadata parentAnnotationMetadata, AnnotationMetadata annotationMetadata) {
        Optional<String> property = parentAnnotationMetadata.stringValue(Property.class, "name");
        if (property.isPresent()) {
            return property.get();
        }
        if (parentAnnotationMetadata != annotationMetadata) {
            property = annotationMetadata.stringValue(Property.class, "name");
            if (property.isPresent()) {
                return property.get();
            }
        }
        throw new DependencyInjectionException(resolutionContext, "Value resolution attempted but @Value annotation is missing");
    }

    private String substituteWildCards(BeanResolutionContext resolutionContext, String valString) {
        if (valString.indexOf('*') > -1) {
            Optional<String> namedBean = resolutionContext.get(Named.class.getName(), ConversionContext.STRING);
            if (namedBean.isPresent()) {
                valString = valString.replace("*", namedBean.get());
            }
        }
        return valString;
    }

    private String resolveCliOption(String name) {
        String attr = "cliPrefix";
        AnnotationMetadata annotationMetadata = getAnnotationMetadata();
        if (annotationMetadata.isPresent(ConfigurationProperties.class, attr)) {
            return annotationMetadata.stringValue(ConfigurationProperties.class, attr).map(val -> val + name).orElse(null);
        }
        return null;
    }

    private <K, R extends Collection<K>> R resolveBeansOfType(BeanResolutionContext resolutionContext, BeanContext context, Argument<R> argument, Argument<K> resultGenericType, Qualifier<K> qualifier) {
        if (resultGenericType == null) {
            throw new DependencyInjectionException(resolutionContext, "Type " + argument.getType() + " has no generic argument");
        }
        qualifier = qualifier == null ? resolveQualifier(resolutionContext, (Argument<K>) argument, true) : qualifier;
        Collection<K> beansOfType = resolutionContext.getBeansOfType(resolveEnvironmentArgument(context, resultGenericType), qualifier);
        return coerceCollectionToCorrectType(argument.getType(), beansOfType, resolutionContext, argument);
    }

    private <K> Stream<K> resolveStreamOfType(BeanResolutionContext resolutionContext, Argument<K> argument, Argument<K> resultGenericType, Qualifier<K> qualifier) {
        if (resultGenericType == null) {
            throw new DependencyInjectionException(resolutionContext, "Type " + argument.getType() + " has no generic argument");
        }
        qualifier = qualifier == null ? resolveQualifier(resolutionContext, argument) : qualifier;
        return resolutionContext.streamOfType(resultGenericType, qualifier);
    }

    private <K> Optional<K> resolveOptionalBean(BeanResolutionContext resolutionContext, Argument<K> argument, Argument<K> resultGenericType, Qualifier<K> qualifier) {
        if (resultGenericType == null) {
            throw new DependencyInjectionException(resolutionContext, "Type " + argument.getType() + " has no generic argument");
        }
        qualifier = qualifier == null ? resolveQualifier(resolutionContext, argument) : qualifier;
        return resolutionContext.findBean(resultGenericType, qualifier);
    }

    private <I, K extends Collection<BeanRegistration<I>>> K resolveBeanRegistrations(BeanResolutionContext resolutionContext,
                                                                                      Argument<K> argument,
                                                                                      Argument<I> genericArgument,
                                                                                      Qualifier<I> qualifier) {
        try {
            if (genericArgument == null) {
                throw new DependencyInjectionException(resolutionContext, "Cannot resolve bean registrations. Argument [" + argument + "] missing generic type information.");
            }
            qualifier = qualifier == null ? resolveQualifier(resolutionContext, (Argument) argument) : qualifier;
            Collection<BeanRegistration<I>> beanRegistrations = resolutionContext.getBeanRegistrations(genericArgument, qualifier);
            return coerceCollectionToCorrectType(argument.getType(), beanRegistrations, resolutionContext, argument);
        } catch (NoSuchBeanException e) {
            if (argument.isNullable()) {
                return null;
            }
            throw new DependencyInjectionException(resolutionContext, e);
        }
    }

    private <K> Argument<K> resolveArgument(BeanContext context, int argIndex, Argument<?>[] arguments) {
        if (arguments == null) {
            return null;
        }
        return resolveEnvironmentArgument(context, (Argument<K>) arguments[argIndex]);
    }

    private <K> Argument<K> resolveEnvironmentArgument(BeanContext context, Argument<K> argument) {
        if (argument instanceof DefaultArgument) {
            if (argument.getAnnotationMetadata().hasPropertyExpressions()) {
                argument = new EnvironmentAwareArgument<>((DefaultArgument<K>) argument);
                instrumentAnnotationMetadata(context, argument);
            }
        }
        return argument;
    }

    private <B> BeanRegistration<B> resolveBeanRegistration(BeanResolutionContext resolutionContext, BeanContext context,
                                                            Argument<B> argument, Argument<B> genericArgument, Qualifier<B> qualifier) {
        try {
            if (genericArgument == null) {
                throw new DependencyInjectionException(resolutionContext, "Cannot resolve bean registration. Argument [" + argument + "] missing generic type information.");
            }
            qualifier = qualifier == null ? resolveQualifier(resolutionContext, argument) : qualifier;
            return context.getBeanRegistration(genericArgument, qualifier);
        } catch (NoSuchBeanException e) {
            if (argument.isNullable()) {
                return null;
            }
            throw new DependencyInjectionException(resolutionContext, argument, e);
        }
    }

    private <K> Qualifier<K> resolveQualifier(BeanResolutionContext resolutionContext, Argument<K> argument) {
        return resolveQualifier(resolutionContext, argument, false);
    }

    private <K> Qualifier<K> resolveQualifier(BeanResolutionContext resolutionContext, Argument<K> argument, boolean resolveIsInnerConfiguration) {
        boolean innerConfiguration = resolveIsInnerConfiguration && isInnerConfiguration(argument.getType());
        return resolveQualifierWithInnerConfiguration(resolutionContext, argument, innerConfiguration);
    }

    private <K> Qualifier<K> resolveQualifierWithInnerConfiguration(BeanResolutionContext resolutionContext, Argument<K> argument, boolean innerConfiguration) {
        boolean hasMetadata = argument.getAnnotationMetadata() != AnnotationMetadata.EMPTY_METADATA;
        Qualifier<K> qualifier = null;
        boolean isIterable = isIterable() || resolutionContext.get(EachProperty.class.getName(), Class.class).map(getBeanType()::equals).orElse(false);
        if (isIterable) {
            qualifier = resolutionContext.get(AnnotationUtil.QUALIFIER, Map.class)
                    .map(map -> (Qualifier<K>) map.get(argument))
                    .orElse(null);
        }
        if (qualifier == null) {
            if ((hasMetadata && argument.isAnnotationPresent(Parameter.class)) ||
                    (innerConfiguration && isIterable) ||
                    Qualifier.class == argument.getType()) {
                final Qualifier<K> currentQualifier = (Qualifier<K>) resolutionContext.getCurrentQualifier();
                if (currentQualifier != null &&
                        currentQualifier.getClass() != InterceptorBindingQualifier.class &&
                        currentQualifier.getClass() != TypeAnnotationQualifier.class) {
                    qualifier = currentQualifier;
                } else {
                    final Optional<String> n = resolutionContext.get(NAMED_ATTRIBUTE, ConversionContext.STRING);
                    qualifier = n.map(Qualifiers::<K>byName).orElse(null);
                }
            }
        }
        return qualifier;
    }

    @SuppressWarnings("unchecked")
    private <I, K extends Collection<I>> K coerceCollectionToCorrectType(Class<K> collectionType, Collection<I> beansOfType, BeanResolutionContext resolutionContext, Argument<?> argument) {
        if (argument.isArray() || collectionType.isInstance(beansOfType)) {
            // Arrays are converted by compile-time code
            return (K) beansOfType;
        } else {
            return (K) CollectionUtils.convertCollection(collectionType, beansOfType)
                    .orElseThrow(() -> new DependencyInjectionException(resolutionContext, "Cannot create a collection of type: " + collectionType.getName()));
        }
    }

    private void instrumentAnnotationMetadata(BeanContext context, Object object) {
        if (object instanceof EnvironmentConfigurable && context instanceof ApplicationContext) {
            final EnvironmentConfigurable ec = (EnvironmentConfigurable) object;
            if (ec.hasPropertyExpressions()) {
                ec.configure(((ApplicationContext) context).getEnvironment());
            }
        }
    }

    /**
     * Internal environment aware annotation metadata delegate.
     */
    private final class BeanAnnotationMetadata extends AbstractEnvironmentAnnotationMetadata {
        BeanAnnotationMetadata(AnnotationMetadata targetMetadata) {
            super(targetMetadata);
        }

        @Nullable
        @Override
        protected Environment getEnvironment() {
            return environment;
        }
    }

    /**
     * The data class containing all method reference information.
     */
    @Internal
    @SuppressWarnings("VisibilityModifier")
    public static final class MethodReference extends MethodOrFieldReference {
        public final String methodName;
        public final Argument[] arguments;
        public final AnnotationMetadata annotationMetadata;
        public final boolean isPreDestroyMethod;
        public final boolean isPostConstructMethod;

        public MethodReference(Class declaringType,
                               String methodName,
                               Argument[] arguments,
                               @Nullable AnnotationMetadata annotationMetadata,
                               boolean requiresReflection) {
            this(declaringType, methodName, arguments, annotationMetadata, requiresReflection, false, false);
        }

        public MethodReference(Class declaringType,
                               String methodName,
                               Argument[] arguments,
                               @Nullable AnnotationMetadata annotationMetadata,
                               boolean requiresReflection,
                               boolean isPostConstructMethod,
                               boolean isPreDestroyMethod) {
            super(declaringType, requiresReflection);
            this.methodName = methodName;
            this.arguments = arguments;
            this.annotationMetadata = annotationMetadata == null ? AnnotationMetadata.EMPTY_METADATA : annotationMetadata;
            this.isPostConstructMethod = isPostConstructMethod;
            this.isPreDestroyMethod = isPreDestroyMethod;
        }
    }

    /**
     * The data class containing all filed reference information.
     */
    @Internal
    @SuppressWarnings("VisibilityModifier")
    public static final class FieldReference extends MethodOrFieldReference {
        public final Argument argument;

        public FieldReference(Class declaringType, Argument argument, boolean requiresReflection) {
            super(declaringType, requiresReflection);
            this.argument = argument;
        }

    }

    /**
     * The shared data class between method and field reference.
     */
    @Internal
    public abstract static class MethodOrFieldReference {
        final Class declaringType;
        final boolean requiresReflection;

        public MethodOrFieldReference(Class declaringType, boolean requiresReflection) {
            this.declaringType = declaringType;
            this.requiresReflection = requiresReflection;
        }

    }

    /**
     * The data class containing annotation injection information.
     */
    @Internal
    @SuppressWarnings("VisibilityModifier")
    public static final class AnnotationReference {
        public final Argument argument;

        public AnnotationReference(Argument argument) {
            this.argument = argument;
        }

    }
}
