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

import io.micronaut.context.annotation.*;
import io.micronaut.context.env.Environment;
import io.micronaut.context.event.BeanInitializedEventListener;
import io.micronaut.context.event.BeanInitializingEvent;
import io.micronaut.context.exceptions.*;
import io.micronaut.core.annotation.*;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.naming.Named;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.DefaultArgument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.value.PropertyResolver;
import io.micronaut.inject.*;
import io.micronaut.inject.annotation.AbstractEnvironmentAnnotationMetadata;
import io.micronaut.inject.qualifiers.InterceptorBindingQualifier;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.inject.qualifiers.TypeAnnotationQualifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.stream.Stream;

/**
 * <p>Default implementation of the {@link BeanDefinition} interface. This class is generally not used directly in user
 * code.
 * Instead a build time tool does analysis of source code and dynamically produces subclasses of this class containing
 * information about the available injection points for a given class.</p>
 * <p>
 * <p>For technical reasons the class has to be marked as public, but is regarded as internal and should be used by
 * compiler tools and plugins (such as AST transformation frameworks)</p>
 * <p>
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
public class AbstractBeanDefinition2<T> extends AbstractBeanContextConditional implements BeanDefinition<T>, EnvironmentConfigurable {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractBeanDefinition2.class);
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
    private final boolean hasExposedTypes;
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
    private Environment environment;
    private Set<Class<?>> exposedTypes;
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

    @SuppressWarnings("ParameterNumber")
    @Internal
    @UsedByGeneratedCode
    protected AbstractBeanDefinition2(
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
            boolean hasExposedTypes,
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
        this.hasExposedTypes = hasExposedTypes;
        this.requiresMethodProcessing = requiresMethodProcessing;
        Optional<Argument<?>> containerElement = Optional.empty();
        if (isContainerType()) {
            final List<Argument<?>> iterableArguments = getTypeArguments(Iterable.class);
            if (!iterableArguments.isEmpty()) {
                containerElement = Optional.of(iterableArguments.iterator().next());
            }
        }
        this.containerElement = containerElement;
    }

    @Override
    public Optional<Argument<?>> getContainerElement() {
        return containerElement;
    }

    @Override
    public final boolean hasPropertyExpressions() {
        return getAnnotationMetadata().hasPropertyExpressions();
    }

    @Override
    public @NonNull
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

    @SuppressWarnings("unchecked")
    @Override
    public <R> Optional<ExecutableMethod<T, R>> findMethod(String name, Class<?>... argumentTypes) {
        if (executableMethodsDefinition == null) {
            return Optional.empty();
        }
        return executableMethodsDefinition.findMethod(name, argumentTypes);
    }

    @Override
    @SuppressWarnings({"unchecked"})
    public <R> Stream<ExecutableMethod<T, R>> findPossibleMethods(String name) {
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

    @SuppressWarnings("deprecation")
    @Override
    public boolean isSingleton() {
        return isSingleton;
    }

    @Override
    public Optional<Class<? extends Annotation>> getScope() {
        return scope.flatMap(scopeClassName -> (Optional) ClassUtils.forName(scopeClassName, getClass().getClassLoader()));
    }

    @Override
    public Optional<String> getScopeName() {
        return scope;
    }

    @Override
    public final Class<T> getBeanType() {
        return type;
    }

    @Override
    @NonNull
    public final Set<Class<?>> getExposedTypes() {
        if (!hasExposedTypes) {
            return Collections.EMPTY_SET;
        }
        if (this.exposedTypes == null) {
            this.exposedTypes = BeanDefinition.super.getExposedTypes();
        }
        return this.exposedTypes;
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
                        fieldConstructor.fieldName,
                        fieldConstructor.annotationMetadata
                );
            }
            if (environment != null) {
                ((EnvironmentConfigurable) constructorInjectionPoint).configure(environment);
            }
        }
        return constructorInjectionPoint;
    }

    @Override
    public Collection<Class<?>> getRequiredComponents() {
        if (requiredComponents != null) {
            return requiredComponents;
        }
        Set<Class<?>> requiredComponents = new HashSet<>();
        if (constructor != null) {
            if (constructor instanceof MethodReference) {
                MethodReference methodConstructor = (MethodReference) constructor;
                if (methodConstructor.arguments != null && methodConstructor.arguments.length > 0) {
                    for (Argument<?> argument : methodConstructor.arguments) {
                        requiredComponents.add(argument.getType());
                    }
                }
            }
        }
        if (methodInjection != null) {
            for (MethodReference methodReference : methodInjection) {
                if (methodReference.arguments != null && methodReference.arguments.length > 0) {
                    for (Argument<?> argument : methodReference.arguments) {
                        requiredComponents.add(argument.getType());
                    }
                }
            }
        }
        if (fieldInjection != null) {
            for (FieldReference fieldReference : fieldInjection) {
                if (fieldReference.annotationMetadata != null && fieldReference.annotationMetadata.hasDeclaredAnnotation(AnnotationUtil.INJECT)) {
                    requiredComponents.add(fieldReference.fieldType);
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
                methodInjectionPoint = new DefaultMethodInjectionPoint(
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
                fieldInjectionPoint = new ReflectionFieldInjectionPoint<T, Object>(
                        this,
                        fieldReference.declaringType,
                        fieldReference.fieldType,
                        fieldReference.fieldName,
                        fieldReference.annotationMetadata,
                        fieldReference.typeArguments
                );
            } else {
                fieldInjectionPoint = new DefaultFieldInjectionPoint<T, Object>(
                        this,
                        fieldReference.declaringType,
                        fieldReference.fieldType,
                        fieldReference.fieldName,
                        fieldReference.annotationMetadata,
                        fieldReference.typeArguments
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
    public String getName() {
        return getBeanType().getName();
    }

    @SuppressWarnings("unchecked")
    @Override
    public T inject(BeanContext context, T bean) {
        return (T) injectBean(new DefaultBeanResolutionContext(context, this), context, bean);
    }

    @SuppressWarnings("unchecked")
    @Override
    public T inject(BeanResolutionContext resolutionContext, BeanContext context, T bean) {
        return (T) injectBean(resolutionContext, context, bean);
    }

    @Override
    public Collection<ExecutableMethod<T, ?>> getExecutableMethods() {
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
        DefaultBeanContext defaultContext = (DefaultBeanContext) context;
        return defaultContext.inject(resolutionContext, this, bean);
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
        final Set<Map.Entry<Class, List<BeanInitializedEventListener>>> beanInitializedEventListeners
                = ((DefaultBeanContext) context).beanInitializedEventListeners;
        if (CollectionUtils.isNotEmpty(beanInitializedEventListeners)) {
            for (Map.Entry<Class, List<BeanInitializedEventListener>> entry : beanInitializedEventListeners) {
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
    protected void invokeMethodWithReflection(BeanResolutionContext resolutionContext, BeanContext context, int methodIndex, Object bean, Object[] methodArgs) {
        MethodReference methodRef = methodInjection[methodIndex];
        Argument[] methodArgumentTypes = methodRef.arguments == null ? Argument.ZERO_ARGUMENTS : methodRef.arguments;
        if (ClassUtils.REFLECTION_LOGGER.isDebugEnabled()) {
            ClassUtils.REFLECTION_LOGGER.debug("Bean of type [" + getBeanType() + "] uses reflection to inject method: '" + methodRef.getName() + "'");
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
                ClassUtils.REFLECTION_LOGGER.debug("Bean of type [" + getBeanType() + "] uses reflection to inject field: '" + fieldRef.getName() + "'");
            }
            Field field = ReflectionUtils.getRequiredField(fieldRef.declaringType, fieldRef.fieldName);
            field.setAccessible(true);
            field.set(object, value);
        } catch (Throwable e) {
            if (e instanceof BeanContextException) {
                throw (BeanContextException) e;
            } else {
                throw new DependencyInjectionException(resolutionContext, this, fieldRef.getName(), "Error setting field value: " + e.getMessage(), e);
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
     * @return The value
     */
    @SuppressWarnings({"unused", "unchecked"})
    @Internal
    protected final Object getValueForMethodArgument(BeanResolutionContext resolutionContext, BeanContext context, int methodIndex, int argIndex) {
        MethodReference methodRef = methodInjection[methodIndex];
        Argument argument = methodRef.arguments[argIndex];
        try (BeanResolutionContext.Path ignored = resolutionContext.getPath()
                .pushMethodArgumentResolve(this, methodRef.methodName, argument, methodRef.arguments, methodRef.requiresReflection)) {
            if (context instanceof ApplicationContext) {
                // can't use orElseThrow here due to compiler bug
                String valueAnnStr = argument.getAnnotationMetadata().stringValue(Value.class).orElse(null);

                Argument<?> argumentType;
                boolean isCollection = false;
                if (Collection.class.isAssignableFrom(argument.getType())) {
                    argumentType = argument.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
                    isCollection = true;
                } else {
                    argumentType = argument;
                }

                if (isInnerConfiguration(argumentType, context)) {
                    Qualifier qualifier = resolveQualifier(resolutionContext, argument, true);
                    if (isCollection) {
                        Collection beans = ((DefaultBeanContext) context).getBeansOfType(resolutionContext, argumentType, qualifier);
                        return coerceCollectionToCorrectType(argument.getType(), beans, resolutionContext, argument);
                    } else {
                        return ((DefaultBeanContext) context).getBean(resolutionContext, argumentType, qualifier);
                    }
                } else {
                    String valString = resolvePropertyValueName(resolutionContext, methodRef.getAnnotationMetadata(), argument, valueAnnStr);

                    ApplicationContext applicationContext = (ApplicationContext) context;
                    ArgumentConversionContext conversionContext = ConversionContext.of(argument);
                    Optional value = resolveValue(applicationContext, conversionContext, valueAnnStr != null, valString);
                    if (argumentType.isOptional()) {
                        return resolveOptionalObject(value);
                    } else {
                        if (value.isPresent()) {
                            return value.get();
                        } else {
                            if (argument.isDeclaredNullable()) {
                                return null;
                            }
                            throw new DependencyInjectionException(resolutionContext, this, methodRef.methodName, conversionContext, valString);
                        }
                    }
                }
            } else {
                throw new DependencyInjectionException(resolutionContext, argument, "BeanContext must support property resolution");
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
     * @return The value
     */
    @Internal
    @UsedByGeneratedCode
    protected final boolean containsValueForMethodArgument(BeanResolutionContext resolutionContext, BeanContext context, int methodIndex, int argIndex) {
        if (context instanceof ApplicationContext) {
            MethodReference methodRef = methodInjection[methodIndex];
            Argument argument = methodRef.arguments[argIndex];
            String valueAnnStr = argument.getAnnotationMetadata().stringValue(Value.class).orElse(null);
            String valString = resolvePropertyValueName(resolutionContext, methodRef.annotationMetadata, argument, valueAnnStr);
            ApplicationContext applicationContext = (ApplicationContext) context;
            Class type = argument.getType();
            boolean isConfigProps = type.isAnnotationPresent(ConfigurationProperties.class);
            boolean result = isConfigProps || Map.class.isAssignableFrom(type) || Collection.class.isAssignableFrom(type) ? applicationContext.containsProperties(valString) : applicationContext.containsProperty(valString);
            if (!result && isConfigurationProperties) {
                String cliOption = resolveCliOption(argument.getName());
                if (cliOption != null) {
                    result = applicationContext.containsProperty(cliOption);
                }
            }
            return result;
        }
        return false;
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
     * @return The resolved bean
     */
    @Internal
    @SuppressWarnings("WeakerAccess")
    @UsedByGeneratedCode
    protected final Object getBeanForMethodArgument(BeanResolutionContext resolutionContext, BeanContext context, int methodIndex, int argIndex) {
        MethodReference methodRef = methodInjection[methodIndex];
        Argument argument = resolveArgument(context, argIndex, methodRef.arguments);
        return getBeanForMethodArgument(resolutionContext, context, methodRef, argument);
    }

    /**
     * Obtains all bean definitions for a method argument at the given index.
     * <p>
     *
     * @param resolutionContext The resolution context
     * @param context           The context
     * @param methodIndex       The method index
     * @param argumentIndex     The argument index
     * @param genericType       The generic type
     * @return The resolved bean
     */
    @SuppressWarnings("WeakerAccess")
    @Internal
    @UsedByGeneratedCode
    protected final Collection<Object> getBeansOfTypeForMethodArgument(BeanResolutionContext resolutionContext, BeanContext context, int methodIndex, int argumentIndex, Argument genericType) {
        MethodReference methodRef = methodInjection[methodIndex];
        Argument argument = resolveArgument(context, argumentIndex, methodRef.arguments);
        try (BeanResolutionContext.Path ignored =
                     resolutionContext.getPath().pushMethodArgumentResolve(this, methodRef.methodName, argument, methodRef.arguments, methodRef.requiresReflection)) {
            return resolveBeansOfType(resolutionContext, context, argument, resolveEnvironmentArgument(context, genericType));
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
     * @return The resolved bean
     */
    @SuppressWarnings("WeakerAccess")
    @Internal
    protected final Optional findBeanForMethodArgument(BeanResolutionContext resolutionContext, BeanContext context, int methodIndex, int argIndex, Argument genericType) {
        MethodReference methodRef = methodInjection[methodIndex];
        Argument<?> argument = resolveArgument(context, argIndex, methodRef.arguments);
        try (BeanResolutionContext.Path ignored =
                     resolutionContext.getPath().pushMethodArgumentResolve(this, methodRef.methodName, argument, methodRef.arguments, methodRef.requiresReflection)) {
            return resolveOptionalBean(resolutionContext, context, argument, resolveEnvironmentArgument(context, genericType));
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
     * @return The resolved bean
     */
    @Internal
    @UsedByGeneratedCode
    protected Stream<?> getStreamOfTypeForMethodArgument(BeanResolutionContext resolutionContext, BeanContext context, int methodIndex, int argIndex, Argument genericType) {
        MethodReference methodRef = methodInjection[methodIndex];
        Argument<?> argument = resolveArgument(context, argIndex, methodRef.arguments);
        try (BeanResolutionContext.Path ignored =
                     resolutionContext.getPath().pushMethodArgumentResolve(this, methodRef.methodName, argument, methodRef.arguments, methodRef.requiresReflection)) {
            return resolveStreamOfType(resolutionContext, context, argument, resolveEnvironmentArgument(context, genericType));
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
     * @return The resolved bean
     */
    @SuppressWarnings("unused")
    @Internal
    @UsedByGeneratedCode
    protected final Object getBeanForConstructorArgument(BeanResolutionContext resolutionContext, BeanContext context, int argIndex) {
        MethodReference constructorMethodRef = (MethodReference) constructor;
        Argument<?> argument = resolveArgument(context, argIndex, constructorMethodRef.arguments);
        final Class<?> beanType = argument.getType();
        if (beanType == BeanResolutionContext.class) {
            return resolutionContext;
//        } else if (argument.isArray()) {
//            Collection beansOfType = getBeansOfTypeForConstructorArgument(resolutionContext, context, argument);
//            return beansOfType.toArray((Object[]) Array.newInstance(beanType.getComponentType(), beansOfType.size()));
//        } else if (Collection.class.isAssignableFrom(beanType)) {
//            Collection beansOfType = getBeansOfTypeForConstructorArgument(resolutionContext, context, argument);
//            return coerceCollectionToCorrectType(beanType, beansOfType);
//        } else if (Stream.class.isAssignableFrom(beanType)) {
//            return getStreamOfTypeForConstructorArgument(resolutionContext, context, argument, constructorMethodRef.resultGenericType);
//        } else if (argument.isOptional()) {
//            return findBeanForConstructorArgument(resolutionContext, context, argument);
        } else {
            BeanResolutionContext.Segment current = resolutionContext.getPath().peek();
            boolean isNullable = argument.isDeclaredNullable();
            if (isNullable && current != null && current.getArgument().equals(argument)) {
                return null;
            } else {
                try (BeanResolutionContext.Path ignored = resolutionContext.getPath()
                        .pushConstructorResolve(this, argument)) {
                    try {
                        Object bean;
                        Qualifier qualifier = resolveQualifier(resolutionContext, argument, isInnerConfiguration(argument, context));
                        if (Qualifier.class.isAssignableFrom(beanType)) {
                            bean = qualifier;
                        } else {
                            Object previous = !argument.isAnnotationPresent(Parameter.class) ? resolutionContext.removeAttribute(NAMED_ATTRIBUTE) : null;
                            try {
                                //noinspection unchecked
                                bean = ((DefaultBeanContext) context).getBean(resolutionContext, argument, qualifier);
                            } finally {
                                if (previous != null) {
                                    resolutionContext.setAttribute(NAMED_ATTRIBUTE, previous);
                                }
                            }
                        }
                        return bean;
                    } catch (DisabledBeanException e) {
                        if (AbstractBeanContextConditional.LOG.isDebugEnabled()) {
                            AbstractBeanContextConditional.LOG.debug("Bean of type [{}] disabled for reason: {}", argument.getTypeName(), e.getMessage());
                        }
                        if (isIterable() && getAnnotationMetadata().hasDeclaredAnnotation(EachBean.class)) {
                            throw new DisabledBeanException("Bean [" + getBeanType().getSimpleName() + "] disabled by parent: " + e.getMessage());
                        } else {
                            if (isNullable) {
                                return null;
                            }
                            throw new DependencyInjectionException(resolutionContext, argument, e);
                        }
                    } catch (NoSuchBeanException e) {
                        if (isNullable) {
                            return null;
                        }
                        throw new DependencyInjectionException(resolutionContext, argument, e);
                    }
                }
            }
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
     * @return The resolved bean
     */
    @SuppressWarnings("unused")
    @Internal
    @UsedByGeneratedCode
    protected final Object getValueForConstructorArgument(BeanResolutionContext resolutionContext, BeanContext context, int argIndex) {
        MethodReference constructorRef = (MethodReference) constructor;
        Argument<?> argument = constructorRef.arguments[argIndex];
        try (BeanResolutionContext.Path ignored = resolutionContext.getPath().pushConstructorResolve(this, argument)) {
            try {
                Object result;
                if (context instanceof ApplicationContext) {
                    ApplicationContext propertyResolver = (ApplicationContext) context;
                    AnnotationMetadata argMetadata = argument.getAnnotationMetadata();
                    Optional<String> valAnn = argMetadata.stringValue(Value.class);
                    String prop = resolvePropertyValueName(resolutionContext, argMetadata, argument, valAnn.orElse(null));
                    ArgumentConversionContext<?> conversionContext = ConversionContext.of(argument);
                    Optional<?> value = resolveValue(propertyResolver, conversionContext, valAnn.isPresent(), prop);
                    if (argument.getType() == Optional.class) {
                        return resolveOptionalObject(value);
                    } else {
                        // can't use orElseThrow here due to compiler bug
                        if (value.isPresent()) {
                            result = value.get();
                        } else {
                            if (argument.isDeclaredNullable()) {
                                result = null;
                            } else {
                                result = argMetadata.getValue(Bindable.class, "defaultValue", argument)
                                        .orElseThrow(() -> new DependencyInjectionException(resolutionContext, conversionContext, prop));
                            }
                        }
                    }
                } else {
                    throw new DependencyInjectionException(resolutionContext, argument, "BeanContext must support property resolution");
                }

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
     * <p>
     *
     * @param resolutionContext The resolution context
     * @param context           The context
     * @param argumentIndex     The argument index
     * @param genericType       The generic type
     * @return The resolved bean
     */
    @SuppressWarnings("WeakerAccess")
    @Internal
    @UsedByGeneratedCode
    protected final Collection<Object> getBeansOfTypeForConstructorArgument(BeanResolutionContext resolutionContext, BeanContext context, int argumentIndex, Argument genericType) {
        MethodReference constructorMethodRef = (MethodReference) constructor;
        Argument argument = resolveArgument(context, argumentIndex, constructorMethodRef.arguments);
        try (BeanResolutionContext.Path ignored = resolutionContext.getPath().pushConstructorResolve(this, argument)) {
            return resolveBeansOfType(resolutionContext, context, argument, resolveEnvironmentArgument(context, genericType));
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
     * @return The resolved bean
     */
    @SuppressWarnings("WeakerAccess")
    @Internal
    @UsedByGeneratedCode
    protected final Collection<BeanRegistration<Object>> getBeanRegistrationsForConstructorArgument(BeanResolutionContext resolutionContext, BeanContext context, int argumentIndex, Argument genericType) {
        MethodReference constructorMethodRef = (MethodReference) constructor;
        Argument<?> argument = resolveArgument(context, argumentIndex, constructorMethodRef.arguments);
        try (BeanResolutionContext.Path ignored = resolutionContext.getPath().pushConstructorResolve(this, argument)) {
            return resolveBeanRegistrations(resolutionContext, context, argument, resolveEnvironmentArgument(context, genericType));
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
     * @return The resolved bean registration
     */
    @SuppressWarnings("WeakerAccess")
    @Internal
    @UsedByGeneratedCode
    protected final BeanRegistration<?> getBeanRegistrationForConstructorArgument(BeanResolutionContext resolutionContext, BeanContext context, int argumentIndex, Argument genericType) {
        MethodReference constructorMethodRef = (MethodReference) constructor;
        Argument<?> argument = resolveArgument(context, argumentIndex, constructorMethodRef.arguments);
        try (BeanResolutionContext.Path ignored = resolutionContext.getPath().pushConstructorResolve(this, argument)) {
            return resolveBeanRegistration(resolutionContext, context, argument, resolveEnvironmentArgument(context, genericType));
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
     * @return The resolved bean
     */
    @SuppressWarnings("WeakerAccess")
    @Internal
    @UsedByGeneratedCode
    protected final Collection<BeanRegistration<Object>> getBeanRegistrationsForMethodArgument(BeanResolutionContext resolutionContext, BeanContext context, int methodIndex, int argIndex, Argument genericType) {
        MethodReference methodReference = methodInjection[methodIndex];
        Argument<?> argument = resolveArgument(context, argIndex, methodReference.arguments);
        try (BeanResolutionContext.Path ignored = resolutionContext.getPath()
                .pushMethodArgumentResolve(this, methodReference.methodName, argument, methodReference.arguments, methodReference.requiresReflection)) {
            return resolveBeanRegistrations(resolutionContext, context, argument, resolveEnvironmentArgument(context, genericType));
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
     * @return The resolved bean registration
     */
    @SuppressWarnings("WeakerAccess")
    @Internal
    @UsedByGeneratedCode
    protected final BeanRegistration<?> getBeanRegistrationForMethodArgument(BeanResolutionContext resolutionContext, BeanContext context, int methodIndex, int argIndex, Argument genericType) {
        MethodReference methodRef = methodInjection[methodIndex];
        Argument<?> argument = resolveArgument(context, argIndex, methodRef.arguments);
        try (BeanResolutionContext.Path ignored = resolutionContext.getPath()
                .pushMethodArgumentResolve(this, methodRef.methodName, argument, methodRef.arguments, methodRef.requiresReflection)) {
            return resolveBeanRegistration(resolutionContext, context, argument, resolveEnvironmentArgument(context, genericType));
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
     * @return The resolved bean
     */
    @SuppressWarnings("WeakerAccess")
    @Internal
    @UsedByGeneratedCode
    protected Stream<?> getStreamOfTypeForConstructorArgument(BeanResolutionContext resolutionContext, BeanContext context, int argIndex, Argument genericType) {
        MethodReference constructorMethodRef = (MethodReference) constructor;
        Argument<?> argument = resolveArgument(context, argIndex, constructorMethodRef.arguments);
        try (BeanResolutionContext.Path ignored = resolutionContext.getPath().pushConstructorResolve(this, argument)) {
            return resolveStreamOfType(resolutionContext, context, argument, resolveEnvironmentArgument(context, genericType));
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
     * @return The resolved bean
     */
    @SuppressWarnings("unused")
    @Internal
    @UsedByGeneratedCode
    protected final Optional<?> findBeanForConstructorArgument(BeanResolutionContext resolutionContext, BeanContext context, int argIndex, Argument genericType) {
        MethodReference constructorMethodRef = (MethodReference) constructor;
        Argument<?> argument = resolveArgument(context, argIndex, constructorMethodRef.arguments);
        try (BeanResolutionContext.Path ignored = resolutionContext.getPath().pushConstructorResolve(this, argument)) {
            return resolveOptionalBean(resolutionContext, context, argument, resolveEnvironmentArgument(context, genericType));
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
     * @return The resolved bean
     */
    @SuppressWarnings("unused")
    @Internal
    @UsedByGeneratedCode
    protected final Object getBeanForField(BeanResolutionContext resolutionContext, BeanContext context, int fieldIndex) {
        final Argument argument = fieldInjection[fieldIndex].asArgument(environment);
        try (BeanResolutionContext.Path ignored = resolutionContext.getPath()
                .pushFieldResolve(this, argument, fieldInjection[fieldIndex].requiresReflection)) {
            return resolveBean(resolutionContext, context, argument);
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
     * @return The resolved bean
     */
    @SuppressWarnings("WeakerAccess")
    @Internal
    @UsedByGeneratedCode
    protected final Object getValueForField(BeanResolutionContext resolutionContext, BeanContext context, int fieldIndex) {
        FieldReference fieldRef = fieldInjection[fieldIndex];
        Argument argument = fieldRef.asArgument(environment);
        try (BeanResolutionContext.Path ignored = resolutionContext.getPath().pushFieldResolve(this, argument, fieldRef.requiresReflection)) {
            if (context instanceof PropertyResolver) {
                final AnnotationMetadata annotationMetadata = fieldRef.getAnnotationMetadata();
                String valueAnnVal = annotationMetadata.stringValue(Value.class).orElse(null);
                Argument<?> argumentType;
                boolean isCollection = false;
                if (Collection.class.isAssignableFrom(fieldRef.fieldType)) {
                    argumentType = argument.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
                    isCollection = true;
                } else {
                    argumentType = argument;
                }
                if (isInnerConfiguration(argumentType, context)) {
                    Qualifier qualifier = resolveQualifier(resolutionContext, argument, true);
                    if (isCollection) {
                        Collection beans = ((DefaultBeanContext) context).getBeansOfType(resolutionContext, argumentType, qualifier);
                        return coerceCollectionToCorrectType(argument.getType(), beans, resolutionContext, argument);
                    } else {
                        return ((DefaultBeanContext) context).getBean(resolutionContext, argumentType, qualifier);
                    }
                } else {
                    String valString = resolvePropertyValueName(resolutionContext, fieldRef, valueAnnVal, annotationMetadata);
                    ArgumentConversionContext conversionContext = ConversionContext.of(argument);
                    Optional value = resolveValue((ApplicationContext) context, conversionContext, valueAnnVal != null, valString);
                    if (argumentType.isOptional()) {
                        return resolveOptionalObject(value);
                    } else {
                        if (value.isPresent()) {
                            return value.get();
                        } else {
                            if (argument.isDeclaredNullable()) {
                                return null;
                            }
                            throw new DependencyInjectionException(resolutionContext, this, fieldRef.fieldName, "Error resolving field value [" + valString + "]. Property doesn't exist or cannot be converted");
                        }
                    }
                }
            } else {
                throw new DependencyInjectionException(resolutionContext, this, fieldRef.fieldName, "@Value requires a BeanContext that implements PropertyResolver");
            }
        }
    }

    /**
     * Resolve a value for the given field of the given type and path. Only
     * used by applications compiled with versions of Micronaut prior to 1.2.0.
     *
     * @param resolutionContext The resolution context
     * @param context           The bean context
     * @param propertyType      The required property type
     * @param propertyPath      The property path
     * @param <T1>              The generic type
     * @return An optional value
     */
    @SuppressWarnings("unused")
    @Internal
    @UsedByGeneratedCode
    protected final <T1> Optional<T1> getValueForPath(
            BeanResolutionContext resolutionContext,
            BeanContext context,
            Argument<T1> propertyType,
            String... propertyPath) {
        if (context instanceof PropertyResolver) {
            PropertyResolver propertyResolver = (PropertyResolver) context;
            String pathString = propertyPath.length > 1 ? String.join(".", propertyPath) : propertyPath[0];
            String valString = resolvePropertyPath(resolutionContext, pathString);

            return propertyResolver.getProperty(valString, ConversionContext.of(propertyType));
        }
        return Optional.empty();
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
    @SuppressWarnings("unused")
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
     * @return True if it does
     */
    @Internal
    @UsedByGeneratedCode
    protected final boolean containsValueForField(BeanResolutionContext resolutionContext, BeanContext context, int fieldIndex) {
        if (context instanceof ApplicationContext) {
            FieldReference fieldRef = fieldInjection[fieldIndex];
            final AnnotationMetadata annotationMetadata = fieldRef.annotationMetadata;
            String valueAnnVal = annotationMetadata.stringValue(Value.class).orElse(null);
            String valString = resolvePropertyValueName(resolutionContext, fieldRef, valueAnnVal, annotationMetadata);
            ApplicationContext applicationContext = (ApplicationContext) context;
            Class fieldType = fieldRef.fieldType;
            boolean isConfigProps = fieldType.isAnnotationPresent(ConfigurationProperties.class);
            boolean result = isConfigProps || Map.class.isAssignableFrom(fieldType) || Collection.class.isAssignableFrom(fieldType) ? applicationContext.containsProperties(valString) : applicationContext.containsProperty(valString);
            if (!result && isConfigurationProperties) {
                String cliOption = resolveCliOption(fieldRef.getName());
                if (cliOption != null) {
                    return applicationContext.containsProperty(cliOption);
                }
            }
            return result;
        }
        return false;
    }

    /**
     * If this bean is a {@link ConfigurationProperties} bean return whether any properties for it are configured
     * within the context.
     *
     * @param resolutionContext the resolution context
     * @param context           The context
     * @return True if it does
     */
    @SuppressWarnings("unused")
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
    @SuppressWarnings({"WeakerAccess", "SameParameterValue"})
    @Internal
    @UsedByGeneratedCode
    protected final boolean containsProperties(@SuppressWarnings("unused") BeanResolutionContext resolutionContext, BeanContext context, String subProperty) {
        return isConfigurationProperties;
    }

    /**
     * Obtains all bean definitions for the field at the given index.
     * <p>
     *
     * @param resolutionContext The resolution context
     * @param context           The context
     * @param fieldIndex        The field index
     * @param genericType       The generic type
     * @return The resolved bean
     */
    @SuppressWarnings("WeakerAccess")
    @Internal
    @UsedByGeneratedCode
    protected final Object getBeansOfTypeForField(BeanResolutionContext resolutionContext, BeanContext context, int fieldIndex, Argument genericType) {
        final FieldReference fieldRef = fieldInjection[fieldIndex];
        final Argument<?> argument = fieldRef.asArgument(environment);
        try (BeanResolutionContext.Path ignored = resolutionContext.getPath().pushFieldResolve(this, argument, fieldRef.requiresReflection)) {
            return resolveBeansOfType(resolutionContext, context, argument, resolveEnvironmentArgument(context, genericType));
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
     * @return The resolved bean
     */
    @SuppressWarnings("WeakerAccess")
    @Internal
    @UsedByGeneratedCode
    protected final Object getBeanRegistrationsForField(BeanResolutionContext resolutionContext, BeanContext context, int fieldIndex, Argument genericType) {
        FieldReference fieldRef = fieldInjection[fieldIndex];
        Argument argument = fieldRef.asArgument(environment);
        try (BeanResolutionContext.Path ignored = resolutionContext.getPath()
                .pushFieldResolve(this, argument, fieldRef.requiresReflection)) {
            return resolveBeanRegistrations(resolutionContext, context, argument, resolveEnvironmentArgument(context, genericType));
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
     * @return The resolved bean registration
     */
    @SuppressWarnings("WeakerAccess")
    @Internal
    @UsedByGeneratedCode
    protected final BeanRegistration<?> getBeanRegistrationForField(BeanResolutionContext resolutionContext, BeanContext context, int fieldIndex, Argument genericType) {
        FieldReference fieldRef = fieldInjection[fieldIndex];
        Argument argument = fieldRef.asArgument(environment);
        if (genericType == null) {
            throw new DependencyInjectionException(resolutionContext, this, fieldRef.fieldName, "Type " + argument.getType() + " has no generic argument");
        }
        try (BeanResolutionContext.Path ignored = resolutionContext.getPath()
                .pushFieldResolve(this, argument, fieldRef.requiresReflection)) {
            return resolveBeanRegistration(resolutionContext, context, argument, resolveEnvironmentArgument(context, genericType));
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
     * @return The resolved bean
     */
    @SuppressWarnings("WeakerAccess")
    @Internal
    protected final Optional findBeanForField(BeanResolutionContext resolutionContext, BeanContext context, int fieldIndex, Argument genericType) {
        FieldReference fieldRef = fieldInjection[fieldIndex];
        Argument argument = fieldRef.asArgument(environment);
        try (BeanResolutionContext.Path ignored = resolutionContext.getPath()
                .pushFieldResolve(this, argument, fieldRef.requiresReflection)) {
            return resolveOptionalBean(resolutionContext, context, argument, resolveEnvironmentArgument(context, genericType));
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
     * @return The resolved bean
     */
    @SuppressWarnings("WeakerAccess")
    @Internal
    @UsedByGeneratedCode
    protected final Stream getStreamOfTypeForField(BeanResolutionContext resolutionContext, BeanContext context, int fieldIndex, Argument genericType) {
        FieldReference fieldRef = fieldInjection[fieldIndex];
        Argument argument = fieldRef.asArgument(environment);
        try (BeanResolutionContext.Path ignored = resolutionContext.getPath()
                .pushFieldResolve(this, argument, fieldRef.requiresReflection)) {
            return resolveStreamOfType(resolutionContext, context, argument, resolveEnvironmentArgument(context, genericType));
        }
    }

    private Object getBeanForMethodArgument(BeanResolutionContext resolutionContext, BeanContext context, MethodReference methodRef, Argument argument) {
        try (BeanResolutionContext.Path ignored = resolutionContext.getPath()
                .pushMethodArgumentResolve(this, methodRef.methodName, argument, methodRef.arguments, methodRef.requiresReflection)) {
            return resolveBean(resolutionContext, context, argument);
        }
    }

    private Object resolveBean(BeanResolutionContext resolutionContext, BeanContext context, Argument argument) {
        Qualifier qualifier = resolveQualifier(resolutionContext, argument);
        try {
            return ((DefaultBeanContext) context).getBean(resolutionContext, argument, qualifier);
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

    private Optional resolveValue(
            ApplicationContext context,
            ArgumentConversionContext<?> argument,
            boolean hasValueAnnotation,
            String valString) {

        if (hasValueAnnotation) {
            return context.resolvePlaceholders(valString).flatMap(v -> context.getConversionService().convert(v, argument));
        } else {
            Optional<?> value = context.getProperty(valString, argument);
            if (!value.isPresent() && isConfigurationProperties) {
                String cliOption = resolveCliOption(argument.getArgument().getName());
                if (cliOption != null) {
                    return context.getProperty(cliOption, argument);
                }
            }
            return value;
        }
    }

    private String resolvePropertyValueName(
            BeanResolutionContext resolutionContext,
            AnnotationMetadata annotationMetadata,
            Argument argument,
            String valueAnnStr) {
        String valString;
        if (valueAnnStr != null) {
            valString = valueAnnStr;
        } else {
            valString = getProperty(resolutionContext, annotationMetadata, argument);
            valString = substituteWildCards(resolutionContext, valString);
        }
        return valString;
    }

    private String getProperty(BeanResolutionContext resolutionContext, AnnotationMetadata annotationMetadata, Argument argument) {
        Optional<String> property = annotationMetadata.stringValue(Property.class, "name");
        if (property.isPresent()) {
            return property.get();
        }
        property = argument.getAnnotationMetadata().stringValue(Property.class, "name");
        if (property.isPresent()) {
            return property.get();
        }
        throw new DependencyInjectionException(
                resolutionContext,
                argument,
                "Value resolution attempted but @Value annotation is missing"
        );
    }

    private String resolvePropertyValueName(
            BeanResolutionContext resolutionContext,
            FieldReference fieldRef,
            String valueAnn,
            AnnotationMetadata annotationMetadata) {
        String valString;
        if (valueAnn != null) {
            valString = valueAnn;
        } else {
            valString = annotationMetadata.stringValue(Property.class, "name")
                    .orElseThrow(() -> new DependencyInjectionException(resolutionContext, this, fieldRef.fieldName, "Value resolution attempted but @Value annotation is missing"));

            valString = substituteWildCards(resolutionContext, valString);
        }
        return valString;
    }

    private String resolvePropertyPath(
            BeanResolutionContext resolutionContext,
            String path) {

        String valString = getConfigurationPropertiesPath(resolutionContext);
        return valString + "." + path;
    }

    private String getConfigurationPropertiesPath(BeanResolutionContext resolutionContext) {
        String valString = getAnnotationMetadata()
                .stringValue(ConfigurationReader.class, "prefix")
                .orElseThrow(() -> new IllegalStateException("Resolve property path called for non @ConfigurationProperties bean"));
        valString = substituteWildCards(
                resolutionContext,
                valString
        );
        return valString;
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

    private boolean isInnerConfiguration(Argument<?> argumentType, BeanContext beanContext) {
        final Class<?> type = argumentType.getType();
        return isConfigurationProperties &&
                type.getName().indexOf('$') > -1 &&
                !type.isEnum() &&
                !type.isPrimitive() &&
                Modifier.isPublic(type.getModifiers()) && Modifier.isStatic(type.getModifiers()) &&
                isInnerOfAnySuperclass(type) &&
                beanContext.findBeanDefinition(argumentType).map(bd -> bd.hasStereotype(ConfigurationReader.class) || bd.isIterable()).isPresent();
    }

    private boolean isInnerOfAnySuperclass(Class argumentType) {
        Class beanType = getBeanType();
        while (beanType != null) {
            if ((beanType.getName() + "$" + argumentType.getSimpleName()).equals(argumentType.getName())) {
                return true;
            }
            beanType = beanType.getSuperclass();
        }
        return false;
    }

    private Collection<Object> resolveBeansOfType(BeanResolutionContext resolutionContext, BeanContext context, Argument argument, Argument resultGenericType) {
        DefaultBeanContext beanContext = (DefaultBeanContext) context;
        if (resultGenericType == null) {
            throw new DependencyInjectionException(resolutionContext, "Type " + argument.getType() + " has no generic argument");
        }
        Qualifier qualifier = resolveQualifier(resolutionContext, argument);
        Collection beansOfType = beanContext.getBeansOfType(resolutionContext, resolveEnvironmentArgument(context, resultGenericType), qualifier);
        return coerceCollectionToCorrectType(argument.getType(), beansOfType, resolutionContext, argument);
    }

    private Stream<?> resolveStreamOfType(BeanResolutionContext resolutionContext, BeanContext context, Argument<?> argument, Argument<?> resultGenericType) {
        if (resultGenericType == null) {
            throw new DependencyInjectionException(resolutionContext, "Type " + argument.getType() + " has no generic argument");
        }
        Qualifier qualifier = resolveQualifier(resolutionContext, argument);
        return ((DefaultBeanContext) context).streamOfType(resolutionContext, resultGenericType, qualifier);
    }

    private Optional<?> resolveOptionalBean(BeanResolutionContext resolutionContext, BeanContext context, Argument<?> argument, Argument<?> resultGenericType) {
        if (resultGenericType == null) {
            throw new DependencyInjectionException(resolutionContext, "Type " + argument.getType() + " has no generic argument");
        }
        Qualifier qualifier = resolveQualifier(resolutionContext, argument);
        return ((DefaultBeanContext) context).findBean(resolutionContext, resultGenericType, qualifier);
    }

    private <B> Collection<BeanRegistration<B>> resolveBeanRegistrations(BeanResolutionContext resolutionContext, BeanContext beanContext, Argument argument, Argument genericArgument) {
        try {
            if (genericArgument == null) {
                throw new DependencyInjectionException(resolutionContext, "Cannot resolve bean registrations. Argument [" + argument + "] missing generic type information.");
            }
            Qualifier qualifier = resolveQualifier(resolutionContext, argument);
            Collection beanRegistrations = ((DefaultBeanContext) beanContext).getBeanRegistrations(resolutionContext, genericArgument, qualifier);
            return coerceCollectionToCorrectType(argument.getType(), beanRegistrations, resolutionContext, argument);
        } catch (NoSuchBeanException e) {
            if (argument.isNullable()) {
                return null;
            }
            throw new DependencyInjectionException(resolutionContext, e);
        }
    }

    private Argument<?> resolveArgument(BeanContext context, int argIndex, Argument<?>[] arguments) {
        if (arguments == null) {
            return null;
        }
        return resolveEnvironmentArgument(context, arguments[argIndex]);
    }

    private Argument<?> resolveEnvironmentArgument(BeanContext context, Argument<?> argument) {
        if (argument instanceof DefaultArgument) {
            if (argument.getAnnotationMetadata().hasPropertyExpressions()) {
                argument = new EnvironmentAwareArgument<>((DefaultArgument) argument);
                instrumentAnnotationMetadata(context, argument);
            }
        }
        return argument;
    }

    private <B> BeanRegistration<B> resolveBeanRegistration(BeanResolutionContext resolutionContext, BeanContext context,
                                                            Argument<?> argument, Argument<?> genericArgument) {
        try {
            if (genericArgument == null) {
                throw new DependencyInjectionException(resolutionContext, argument, "Cannot resolve bean registration. Argument [" + argument + "] missing generic type information.");
            }
            Qualifier qualifier = resolveQualifier(resolutionContext, argument);
            return context.getBeanRegistration(genericArgument, qualifier);
        } catch (NoSuchBeanException e) {
            if (argument.isNullable()) {
                return null;
            }
            throw new DependencyInjectionException(resolutionContext, argument, e);
        }
    }

    private Qualifier resolveQualifier(BeanResolutionContext resolutionContext, Argument argument) {
        return resolveQualifier(resolutionContext, argument, false);
    }

    private Qualifier resolveQualifier(BeanResolutionContext resolutionContext, Argument argument, boolean innerConfiguration) {
        final Qualifier<Object> argumentQualifier = Qualifiers.forArgument(argument);
        if (argumentQualifier != null) {
            return argumentQualifier;
        } else {
            AnnotationMetadata annotationMetadata = argument.getAnnotationMetadata();
            boolean hasMetadata = annotationMetadata != AnnotationMetadata.EMPTY_METADATA;
            if (hasMetadata) {
                if (annotationMetadata.hasPropertyExpressions()) {
                    annotationMetadata = new BeanAnnotationMetadata(annotationMetadata);
                }
                if (annotationMetadata.hasAnnotation(AnnotationUtil.ANN_INTERCEPTOR_BINDING_QUALIFIER)) {
                    return Qualifiers.byInterceptorBinding(annotationMetadata);
                }
                Class<?>[] byType = annotationMetadata.hasDeclaredAnnotation(Type.class) ? annotationMetadata.classValues(Type.class) : null;
                if (byType != null) {
                    return Qualifiers.byType(byType);
                }
            }
            Qualifier qualifier = null;
            boolean isIterable = isIterable() || resolutionContext.get(EachProperty.class.getName(), Class.class).map(getBeanType()::equals).orElse(false);
            if (isIterable) {
                Optional<Qualifier> optional = resolutionContext.get(AnnotationUtil.QUALIFIER, Map.class)
                        .map(map -> (Qualifier) map.get(argument));
                qualifier = optional.orElse(null);
            }
            if (qualifier == null) {
                if ((hasMetadata && argument.isAnnotationPresent(Parameter.class)) ||
                        (innerConfiguration && isIterable) ||
                        Qualifier.class == argument.getType()) {
                    final Qualifier<?> currentQualifier = resolutionContext.getCurrentQualifier();
                    if (currentQualifier != null &&
                            currentQualifier.getClass() != InterceptorBindingQualifier.class &&
                            currentQualifier.getClass() != TypeAnnotationQualifier.class) {
                        qualifier = currentQualifier;
                    } else {
                        final Optional<String> n = resolutionContext.get(NAMED_ATTRIBUTE, ConversionContext.STRING);
                        qualifier = n.map(Qualifiers::byName).orElse(null);
                    }
                }
            }
            return qualifier;
        }
    }

    private Object resolveOptionalObject(Optional value) {
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
    }

    @SuppressWarnings("unchecked")
    private <I, K extends Collection<I>> Collection coerceCollectionToCorrectType(Class<K> collectionType, Collection<I> beansOfType, BeanResolutionContext resolutionContext, Argument argument) {
        if (argument.isArray() || collectionType.isInstance(beansOfType)) {
            // Arrays are converted by compile-time code
            return beansOfType;
        } else {
            return (Collection) CollectionUtils.convertCollection(collectionType, beansOfType)
                    .orElseThrow(() -> new DependencyInjectionException(resolutionContext, argument, "Cannot create a collection of type: " + collectionType.getName()));
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
     * Internal environment aware annotation metadata delegate.
     */
    private static final class EnvironmentAnnotationMetadata extends AbstractEnvironmentAnnotationMetadata {

        private final Environment environment;

        EnvironmentAnnotationMetadata(AnnotationMetadata targetMetadata, Environment environment) {
            super(targetMetadata);
            this.environment = environment;
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
            super(declaringType, annotationMetadata, requiresReflection);
            this.methodName = methodName;
            this.arguments = arguments;
            this.isPostConstructMethod = isPostConstructMethod;
            this.isPreDestroyMethod = isPreDestroyMethod;
        }

        @Override
        public String getName() {
            return methodName;
        }
    }

    /**
     * The data class containing all filed reference information.
     */
    @Internal
    @SuppressWarnings("VisibilityModifier")
    public static final class FieldReference extends MethodOrFieldReference {
        public final Class fieldType;
        public final String fieldName;
        public final Argument[] typeArguments;

        public FieldReference(Class declaringType, Class fieldType, String fieldName, AnnotationMetadata annotationMetadata, Argument[] typeArguments, boolean requiresReflection) {
            super(declaringType, annotationMetadata, requiresReflection);
            this.fieldType = fieldType;
            this.fieldName = fieldName;
            this.typeArguments = typeArguments;
        }

        @Override
        public String getName() {
            return fieldName;
        }

        public Argument asArgument(Environment environment) {
            return Argument.of(
                    fieldType,
                    fieldName,
                    !annotationMetadata.isEmpty() && annotationMetadata.hasPropertyExpressions()
                            ? new EnvironmentAnnotationMetadata(annotationMetadata, environment) : annotationMetadata,
                    typeArguments
            );
        }
    }

    public abstract static class MethodOrFieldReference implements AnnotatedElement {
        final Class declaringType;
        final AnnotationMetadata annotationMetadata;
        final boolean requiresReflection;

        public MethodOrFieldReference(Class declaringType, AnnotationMetadata annotationMetadata, boolean requiresReflection) {
            this.declaringType = declaringType;
            this.annotationMetadata = annotationMetadata == null ? AnnotationMetadata.EMPTY_METADATA : annotationMetadata;
            this.requiresReflection = requiresReflection;
        }

        @Override
        public AnnotationMetadata getAnnotationMetadata() {
            return annotationMetadata;
        }
    }
}
