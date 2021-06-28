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
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Supplier;
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
    @Nullable
    private final MethodOrFieldReference constructor;
    @Nullable
    private final MethodReference[] methodInjection;
    @Nullable
    private final FieldReference[] fieldInjection;
    @Nullable
    private final ExecutableMethod<T, ?>[] executableMethods;
    @Nullable
    private final Map<String, Argument<?>[]> typeArgumentsMap;
    @Nullable
    private Environment environment;
    private Set<Class<?>> exposedTypes;
    private Optional<Argument<?>> containerElement;

    @Nullable
    private Map<MethodKey, ExecutableMethod<T, ?>> executableMethodMap;
    @Nullable
    private List<ExecutableMethod<T, ?>> executableMethodList;
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
            @Nullable ExecutableMethod<T, ?>[] executableMethods,
            @Nullable Map<String, Argument<?>[]> typeArgumentsMap,
            Optional<String> scope,
            boolean isAbstract,
            boolean isProvided,
            boolean isIterable,
            boolean isSingleton,
            boolean isPrimary) {
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
        // TODO: process method and fields annotation
        this.isProvided = isProvided;
        this.isIterable = isIterable;
        this.isSingleton = isSingleton;
        this.isPrimary = isPrimary;
        this.isAbstract = isAbstract;
        this.constructor = constructor;
        this.methodInjection = methodInjection;
        this.fieldInjection = fieldInjection;
        this.executableMethods = executableMethods;
        this.typeArgumentsMap = typeArgumentsMap;
        this.isConfigurationProperties = isIterable || hasStereotype(ConfigurationReader.class);
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
    public @NonNull List<Argument<?>> getTypeArguments(String type) {
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

    @SuppressWarnings("unchecked")
    @Override
    public <R> Optional<ExecutableMethod<T, R>> findMethod(String name, Class<?>... argumentTypes) {
        if (executableMethods != null) {
            // Iterate loop for the small `executableMethods` sizes instead of creating a map
            if (executableMethods.length < 10) {
                for (ExecutableMethod<T, ?> em : executableMethods) {
                    if (em.getMethodName().equals(name)
                            && Arrays.equals(argumentTypes, em.getArgumentTypes())) {
                        return Optional.of((ExecutableMethod<T, R>) em);
                    }
                }
                return Optional.empty();
            }
            MethodKey methodKey = new MethodKey(name, argumentTypes);
            ExecutableMethod<T, R> invocableMethod = (ExecutableMethod<T, R>) getExecutableMethodMap().get(methodKey);
            if (invocableMethod != null) {
                return Optional.of(invocableMethod);
            }
        }
        return Optional.empty();
    }

    @Override
    @SuppressWarnings({"unchecked"})
    public Stream<ExecutableMethod<T, ?>> findPossibleMethods(String name) {
        if (executableMethods == null) {
            return Stream.empty();
        }
        return Arrays.stream(executableMethods).filter(method -> method.getMethodName().equals(name));
    }

    private Map<MethodKey, ExecutableMethod<T, ?>> getExecutableMethodMap() {
        if (executableMethods == null) {
            return Collections.emptyMap();
        }
        if (executableMethodMap == null) {
            Map<MethodKey, ExecutableMethod<T, ?>> executableMethodMap = new HashMap<>(executableMethods.length, 1);
            for (ExecutableMethod<T, ?> executableMethod : executableMethods) {
                executableMethodMap.put(new MethodKey(executableMethod.getMethodName(), executableMethod.getArgumentTypes()), executableMethod);
            }
            this.executableMethodMap = executableMethodMap;
        }
        return executableMethodMap;
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

    // TODO: provide at runtime
    @Override
    @NonNull
    public final Set<Class<?>> getExposedTypes() {
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
        if (executableMethods == null) {
            return Collections.emptyList();
        }
        if (executableMethodList == null) {
            executableMethodList = Collections.unmodifiableList(Arrays.asList(executableMethods));
        }
        return executableMethodList;
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
            if (executableMethods != null) {
                for (ExecutableMethod<T, ?> executableMethod : executableMethods) {
                    if (executableMethod instanceof EnvironmentConfigurable) {
                        ((EnvironmentConfigurable) executableMethod).configure(environment);
                    }
                }
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
        List<Integer> postConstructWithReflection = null;
        if (methodInjection != null) {
            for (int i = 0; i < methodInjection.length; i++) {
                MethodReference methodReference = methodInjection[i];
                if (methodReference.isPostConstructMethod && methodReference.requiresReflection) {
                    if (postConstructWithReflection == null) {
                        postConstructWithReflection = new LinkedList<>();
                    }
                    postConstructWithReflection.add(i);
                }
            }
        }

        boolean addInCreationHandling = isSingleton() && !CollectionUtils.isNotEmpty(postConstructMethods);
        DefaultBeanContext.BeanKey key = null;
        if (addInCreationHandling) {
            // ensure registration as an inflight bean if a post construct is present
            // this is to ensure that if the post construct method does anything funky to
            // cause recreation of this bean then we don't have a circular problem
            key = new DefaultBeanContext.BeanKey(this, resolutionContext.getCurrentQualifier());
            resolutionContext.addInFlightBean(key, bean);
        }

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

        DefaultBeanContext defaultContext = (DefaultBeanContext) context;
        if (postConstructWithReflection != null) {
            for (Integer i : postConstructWithReflection) {
                injectBeanMethodWithReflection(resolutionContext, defaultContext, i, bean);
            }
        }
        if (bean instanceof LifeCycle) {
            bean = ((LifeCycle) bean).start();
        }
        try {
            return bean;
        } finally {
            if (addInCreationHandling) {
                // ensure registration as an inflight bean if a post construct is present
                // this is to ensure that if the post construct method does anything funky to
                // cause recreation of this bean then we don't have a circular problem
                resolutionContext.removeInFlightBean(key);
            }
        }
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
    @UsedByGeneratedCode
    protected Object preDestroy(BeanResolutionContext resolutionContext, BeanContext context, Object bean) {
        DefaultBeanContext defaultContext = (DefaultBeanContext) context;
        if (methodInjection != null) {
            for (int i = 0; i < methodInjection.length; i++) {
                MethodReference methodReference = methodInjection[i];
                if (methodReference.isPreDestroyMethod && methodReference.requiresReflection) {
                    injectBeanMethodWithReflection(resolutionContext, defaultContext, i, bean);
                }
            }
        }
        if (bean instanceof LifeCycle) {
            bean = ((LifeCycle) bean).stop();
        }
        return bean;
    }

    /**
     * Inject a bean method that requires reflection.
     *
     * @param resolutionContext The resolution context
     * @param context           The bean context
     * @param methodIndex       The method index
     * @param bean              The bean
     */
    @Internal
    @SuppressWarnings("WeakerAccess")
    protected void injectBeanMethodWithReflection(BeanResolutionContext resolutionContext, DefaultBeanContext context, int methodIndex, Object bean) {
        MethodReference methodRef = methodInjection[methodIndex];
        Argument[] methodArgumentTypes = methodRef.arguments == null ? Argument.ZERO_ARGUMENTS : methodRef.arguments;
        Object[] methodArgs = new Object[methodArgumentTypes.length];
        for (int i = 0; i < methodArgumentTypes.length; i++) {
            methodArgs[i] = getBeanForMethodArgument(resolutionContext, context, methodIndex, i);
        }
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
            throw new BeanInstantiationException(this, e);
        }
    }

    /**
     * Injects the value of a field of a bean that requires reflection.
     *
     * @param resolutionContext The resolution context
     * @param context           The bean context
     * @param index             The index of the field
     * @param bean              The bean being injected
     */
    @SuppressWarnings("unused")
    @Internal
    protected final void injectBeanFieldWithReflection(BeanResolutionContext resolutionContext, DefaultBeanContext context, int index, Object bean) {
        FieldReference fieldRef = fieldInjection[index];
        boolean isInject = fieldRef.annotationMetadata != null && fieldRef.annotationMetadata.hasDeclaredAnnotation(AnnotationUtil.INJECT);
        try {
            Object value;
            if (isInject) {
                value = getBeanForField(resolutionContext, context, fieldRef);
            } else {
                value = getValueForField(resolutionContext, context, index);
            }
            if (value != null) {
                if (ClassUtils.REFLECTION_LOGGER.isDebugEnabled()) {
                    ClassUtils.REFLECTION_LOGGER.debug("Bean of type [" + getBeanType() + "] uses reflection to inject field: '" + fieldRef.getName() + "'");
                }
                Field field = ReflectionUtils.getRequiredField(fieldRef.declaringType, fieldRef.fieldName);
                field.setAccessible(true);
                field.set(bean, value);
            }
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
                        return coerceCollectionToCorrectType(argument.getType(), beans);
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
            if (!result && isConfigurationProperties()) {
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
     * Obtains all bean definitions for the method at the given index and the argument at the given index
     * <p>
     * Warning: this method is used by internal generated code and should not be called by user code.
     *
     * @param resolutionContext The resolution context
     * @param context           The context
     * @param methodRef         The method reference
     * @param argument          The argument
     * @return The resolved bean
     */
    @SuppressWarnings("WeakerAccess")
    @Internal
    protected final Collection getBeansOfTypeForMethodArgument(BeanResolutionContext resolutionContext, BeanContext context, MethodReference methodRef, Argument argument) {
        return resolveBeanWithGenericsFromMethodArgument(resolutionContext, methodRef, argument, (beanType, qualifier) -> {
                    boolean hasNoGenerics = !argument.getType().isArray() && argument.getTypeVariables().isEmpty();
                    if (hasNoGenerics) {
                        return ((DefaultBeanContext) context).getBean(
                                resolutionContext,
                                beanType,
                                qualifier
                        );
                    } else {
                        return ((DefaultBeanContext) context).getBeansOfType(
                                resolutionContext,
                                beanType,
                                qualifier
                        );
                    }

                }
        );
    }

    /**
     * Obtains an optional bean for the method at the given index and the argument at the given index
     * <p>
     * Warning: this method is used by internal generated code and should not be called by user code.
     *
     * @param resolutionContext The resolution context
     * @param context           The context
     * @param methodRef         The method reference
     * @param argument          The argument
     * @return The resolved bean
     */
    @SuppressWarnings("WeakerAccess")
    @Internal
    protected final Optional findBeanForMethodArgument(BeanResolutionContext resolutionContext, BeanContext context, MethodReference methodRef, Argument argument) {
        return resolveBeanWithGenericsFromMethodArgument(resolutionContext, methodRef, argument, (beanType, qualifier) ->
                ((DefaultBeanContext) context).findBean(resolutionContext, beanType, qualifier)
        );
    }

    /**
     * Obtains all bean definitions for the method at the given index and the argument at the given index
     * <p>
     * Warning: this method is used by internal generated code and should not be called by user code.
     *
     * @param resolutionContext The resolution context
     * @param context           The context
     * @param methodRef    The method injection point
     * @param argument          The argument
     * @return The resolved bean
     */
    @SuppressWarnings("WeakerAccess")
    @Internal
    protected final Stream streamOfTypeForMethodArgument(BeanResolutionContext resolutionContext, BeanContext context, MethodReference methodRef, Argument argument) {
        return resolveBeanWithGenericsFromMethodArgument(resolutionContext, methodRef, argument, (beanType, qualifier) ->
                ((DefaultBeanContext) context).streamOfType(resolutionContext, beanType, qualifier)
        );
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
        } else if (argument.isArray()) {
            Collection beansOfType = getBeansOfTypeForConstructorArgument(resolutionContext, context, argument);
            return beansOfType.toArray((Object[]) Array.newInstance(beanType.getComponentType(), beansOfType.size()));
        } else if (Collection.class.isAssignableFrom(beanType)) {
            Collection beansOfType = getBeansOfTypeForConstructorArgument(resolutionContext, context, argument);
            return coerceCollectionToCorrectType(beanType, beansOfType);
        } else if (Stream.class.isAssignableFrom(beanType)) {
            return streamOfTypeForConstructorArgument(resolutionContext, context, argument);
        } else if (argument.isOptional()) {
            return findBeanForConstructorArgument(resolutionContext, context, argument);
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
     * @param resolutionContext         The resolution context
     * @param context                   The context
     * @param argument                  The argument
     * @return The resolved bean
     */
    private Collection getBeansOfTypeForConstructorArgument(BeanResolutionContext resolutionContext, BeanContext context, Argument argument) {
        return resolveBeanWithGenericsFromConstructorArgument(resolutionContext, argument, (beanType, qualifier) -> {
                    boolean hasNoGenerics = !argument.getType().isArray() && argument.getTypeVariables().isEmpty();
                    if (hasNoGenerics) {
                        return ((DefaultBeanContext) context).getBean(resolutionContext, beanType, qualifier);
                    } else {
                        return ((DefaultBeanContext) context).getBeansOfType(resolutionContext, beanType, qualifier);
                    }
                }
        );
    }

    /**
     * Obtains all bean definitions for a constructor argument at the given index.
     * <p>
     * @param resolutionContext         The resolution context
     * @param context                   The context
     * @param argumentIndex             The argument index
     * @return The resolved bean
     */
    @SuppressWarnings("WeakerAccess")
    @Internal
    @UsedByGeneratedCode
    protected final Object getBeansOfTypeForConstructorArgument(BeanResolutionContext resolutionContext, BeanContext context, int argumentIndex) {
        final MethodReference constructorRef = (MethodReference) constructor;
        final Argument<?> argument = resolveArgument(context, argumentIndex, constructorRef.arguments);
        final Class<?> argumentType = argument.getType();
        Argument<?> genericType = resolveGenericType(argument, () ->
                new DependencyInjectionException(resolutionContext, argument, "Type " + argumentType + " has no generic argument")
        );
        final Qualifier qualifier = resolveQualifier(resolutionContext, argument);
        try (BeanResolutionContext.Path ignored = resolutionContext.getPath().pushConstructorResolve(this, argument)) {
            return doGetBeansOfType(resolutionContext, (DefaultBeanContext) context, argumentType, genericType, qualifier);
        }
    }

    /**
     * Obtains all bean definitions for a constructor argument at the given index.
     * <p>
     * @param resolutionContext         The resolution context
     * @param context                   The context
     * @param methodIndex               The method index
     * @param argumentIndex             The argument index
     * @return The resolved bean
     */
    @SuppressWarnings("WeakerAccess")
    @Internal
    @UsedByGeneratedCode
    protected final Object getBeansOfTypeForMethodArgument(BeanResolutionContext resolutionContext, BeanContext context, int methodIndex, int argumentIndex) {
        final MethodReference methodRef = methodInjection[methodIndex];
        Argument<?> argument = resolveArgument(context, argumentIndex, methodRef.arguments);
        final Class<?> argumentType = argument.getType();
        Argument<?> genericType = resolveGenericType(argument, () ->
                new DependencyInjectionException(resolutionContext, this, methodRef.methodName, argument, "Type " + argumentType + " has no generic argument")
        );
        try (BeanResolutionContext.Path ignored = resolutionContext.getPath()
                .pushMethodArgumentResolve(this, methodRef.methodName, argument, methodRef.arguments, methodRef.requiresReflection)) {
            final Qualifier qualifier = resolveQualifier(resolutionContext, argument);
            return doGetBeansOfType(resolutionContext, (DefaultBeanContext) context, argumentType, genericType, qualifier);
        }
    }

    /**
     * Obtains all bean definitions for the field at the given index.
     * <p>
     * @param resolutionContext         The resolution context
     * @param context                   The context
     * @param fieldIndex                The field index
     * @return The resolved bean
     */
    @SuppressWarnings("WeakerAccess")
    @Internal
    @UsedByGeneratedCode
    protected final Object getBeansOfTypeForField(BeanResolutionContext resolutionContext, BeanContext context, int fieldIndex) {
        final FieldReference fieldRef = fieldInjection[fieldIndex];
        final Argument<?> argument = fieldRef.asArgument(environment);
        final Class<?> argumentType = argument.getType();
        Argument<?> genericType = resolveGenericType(argument, () ->
                new DependencyInjectionException(resolutionContext, this, fieldRef.fieldName, "Type " + argumentType + " has no generic argument"));
        try (BeanResolutionContext.Path ignored = resolutionContext.getPath().pushFieldResolve(this, argument, fieldRef.requiresReflection)) {
            final Qualifier qualifier = resolveQualifier(resolutionContext, argument);
            return doGetBeansOfType(resolutionContext, (DefaultBeanContext) context, argumentType, genericType, qualifier);
        }
    }

    private Object doGetBeansOfType(BeanResolutionContext resolutionContext, DefaultBeanContext context, Class<?> argumentType, Argument<?> genericType, Qualifier qualifier) {
        final Collection<?> beansOfType = context.getBeansOfType(resolutionContext, genericType, qualifier);
        if (argumentType.isArray()) {
            return beansOfType.toArray((Object[]) Array.newInstance(genericType.getType(), beansOfType.size()));
        } else {
            return coerceCollectionToCorrectType(argumentType, beansOfType);
        }
    }

    private Argument<?> resolveGenericType(Argument<?> argument, Supplier<DependencyInjectionException> exceptionSupplier) {
        Argument<?> genericType;
        if (argument.isArray()) {
            genericType = Argument.of(argument.getType().getComponentType());
        } else {
            genericType = argument.getFirstTypeVariable().orElseThrow(exceptionSupplier);
        }
        return genericType;
    }

    /**
     * Obtains all bean definitions for a constructor argument at the given index
     * <p>
     * Warning: this method is used by internal generated code and should not be called by user code.
     *
     * @param resolutionContext         The resolution context
     * @param context                   The context
     * @param argumentIndex             The argument index
     * @return The resolved bean
     */
    @SuppressWarnings("WeakerAccess")
    @Internal
    @UsedByGeneratedCode
    protected final Object getBeanRegistrationsForConstructorArgument(
            BeanResolutionContext resolutionContext,
            BeanContext context,
            int argumentIndex) {
        Argument<?> argument = resolveArgument(context, argumentIndex, getConstructor().getArguments());
        try (BeanResolutionContext.Path ignored = resolutionContext.getPath().pushConstructorResolve(this, argument)) {
            return doResolveBeanRegistrations(resolutionContext, (DefaultBeanContext) context, argument);
        }
    }

    /**
     * Obtains a bean registration for a method injection point.
     * <p>
     * Warning: this method is used by internal generated code and should not be called by user code.
     *
     * @param resolutionContext         The resolution context
     * @param context                   The context
     * @param argIndex                  The arg index
     * @return The resolved bean registration
     */
    @SuppressWarnings("WeakerAccess")
    @Internal
    @UsedByGeneratedCode
    protected final BeanRegistration<?> getBeanRegistrationForConstructorArgument(
            BeanResolutionContext resolutionContext,
            BeanContext context,
            int argIndex) {
        Argument<?> argument = resolveArgument(context, argIndex, getConstructor().getArguments());
        try (BeanResolutionContext.Path ignored = resolutionContext.getPath().pushConstructorResolve(this, argument)) {
            return resolveBeanRegistrationWithGenericsFromArgument(resolutionContext, argument, (beanType, qualifier) ->
                    ((DefaultBeanContext) context).getBeanRegistration(resolutionContext, beanType, qualifier)
            );
        }
    }

    /**
     * Obtains all bean definitions for a field injection point.
     * <p>
     * Warning: this method is used by internal generated code and should not be called by user code.
     *
     * @param resolutionContext         The resolution context
     * @param context                   The context
     * @param fieldIndex                The field index
     * @return The resolved bean
     */
    @SuppressWarnings("WeakerAccess")
    @Internal
    @UsedByGeneratedCode
    protected final Object getBeanRegistrationsForField(
            BeanResolutionContext resolutionContext,
            BeanContext context,
            int fieldIndex) {
        FieldReference fieldRef = fieldInjection[fieldIndex];
        Argument fieldAsArgument = fieldRef.asArgument(environment);
        try (BeanResolutionContext.Path ignored = resolutionContext.getPath()
                .pushFieldResolve(this, fieldAsArgument, fieldRef.requiresReflection)) {
            return doResolveBeanRegistrations(resolutionContext, (DefaultBeanContext) context, fieldAsArgument);
        }
    }

    /**
     * Obtains a bean registration for a field injection point.
     * <p>
     * Warning: this method is used by internal generated code and should not be called by user code.
     *
     * @param resolutionContext         The resolution context
     * @param context                   The context
     * @param fieldIndex                The field index
     * @return The resolved bean registration
     */
    @SuppressWarnings("WeakerAccess")
    @Internal
    @UsedByGeneratedCode
    protected final BeanRegistration<?> getBeanRegistrationForField(
            BeanResolutionContext resolutionContext,
            BeanContext context,
            int fieldIndex) {
        FieldReference fieldRef = fieldInjection[fieldIndex];
        Argument fieldAsArgument = fieldRef.asArgument(environment);
        try (BeanResolutionContext.Path ignored = resolutionContext.getPath()
                .pushFieldResolve(this, fieldAsArgument, fieldRef.requiresReflection)) {
            return resolveBeanRegistrationWithGenericsFromArgument(resolutionContext, fieldAsArgument, (beanType, qualifier) ->
                    ((DefaultBeanContext) context).getBeanRegistration(resolutionContext, beanType, qualifier)
            );
        }
    }

    /**
     * Obtains all bean definitions for a method injection point.
     * <p>
     * Warning: this method is used by internal generated code and should not be called by user code.
     *
     * @param resolutionContext         The resolution context
     * @param context                   The context
     * @param methodIndex               The method index
     * @param argIndex                  The arg index
     * @return The resolved bean
     */
    @SuppressWarnings("WeakerAccess")
    @Internal
    @UsedByGeneratedCode
    protected final Object getBeanRegistrationsForMethodArgument(
            BeanResolutionContext resolutionContext,
            BeanContext context,
            int methodIndex,
            int argIndex) {
        MethodReference methodReference = methodInjection[methodIndex];
        Argument<?> argument = resolveArgument(context, argIndex, methodReference.arguments);
        try (BeanResolutionContext.Path ignored = resolutionContext.getPath()
                .pushMethodArgumentResolve(this, methodReference.methodName, argument, methodReference.arguments, methodReference.requiresReflection)) {
            return doResolveBeanRegistrations(resolutionContext, (DefaultBeanContext) context, argument);
        }
    }

    /**
     * Obtains a bean registration for a method injection point.
     * <p>
     * Warning: this method is used by internal generated code and should not be called by user code.
     *
     * @param resolutionContext         The resolution context
     * @param context                   The context
     * @param methodIndex               The method index
     * @param argIndex                  The arg index
     * @return The resolved bean registration
     */
    @SuppressWarnings("WeakerAccess")
    @Internal
    @UsedByGeneratedCode
    protected final BeanRegistration<?> getBeanRegistrationForMethodArgument(
            BeanResolutionContext resolutionContext,
            BeanContext context,
            int methodIndex,
            int argIndex) {
        MethodInjectionPoint<?, ?> methodInjectionPoint = methodInjectionPoints.get(methodIndex);
        Argument<?> argument = resolveArgument(context, argIndex, methodInjectionPoint.getArguments());
        try (BeanResolutionContext.Path ignored = resolutionContext.getPath()
                .pushMethodArgumentResolve(this, methodInjectionPoint, argument)) {
            return resolveBeanRegistrationWithGenericsFromArgument(resolutionContext, argument, (beanType, qualifier) ->
                    ((DefaultBeanContext) context).getBeanRegistration(resolutionContext, beanType, qualifier)
            );
        }
    }

    /**
     * Obtains all bean definitions for a constructor argument at the given index
     * <p>
     * Warning: this method is used by internal generated code and should not be called by user code.
     *
     * @param resolutionContext         The resolution context
     * @param context                   The context
     * @param argument                  The argument
     * @return The resolved bean
     */
    @SuppressWarnings("WeakerAccess")
    private Stream streamOfTypeForConstructorArgument(BeanResolutionContext resolutionContext, BeanContext context, Argument argument) {
        return resolveBeanWithGenericsFromConstructorArgument(resolutionContext, argument, (beanType, qualifier) ->
                ((DefaultBeanContext) context).streamOfType(resolutionContext, beanType, qualifier)
        );
    }

    /**
     * Obtains all bean definitions for a constructor argument at the given index
     * <p>
     * Warning: this method is used by internal generated code and should not be called by user code.
     *
     * @param resolutionContext         The resolution context
     * @param context                   The context
     * @param argument                  The argument
     * @return The resolved bean
     */
    private Optional findBeanForConstructorArgument(BeanResolutionContext resolutionContext, BeanContext context, Argument argument) {
        return resolveBeanWithGenericsFromConstructorArgument(resolutionContext, argument, (beanType, qualifier) ->
                ((DefaultBeanContext) context).findBean(resolutionContext, beanType, qualifier)
        );
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
        return getBeanForField(resolutionContext, context, fieldInjection[fieldIndex]);
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
                        return coerceCollectionToCorrectType(argument.getType(), beans);
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
            if (!result && isConfigurationProperties()) {
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
//        // todo
//        boolean isSubProperty = StringUtils.isNotEmpty(subProperty);
//        if (!isSubProperty && false && !requiredComponents.isEmpty()) { // TODO
//            // if the bean requires dependency injection we disable this optimization
//            return true;
//        }
        if (isConfigurationProperties) {
            AnnotationMetadata annotationMetadata = getAnnotationMetadata();
            Optional<Object> cliPrefix = annotationMetadata.getValue(ConfigurationProperties.class, "cliPrefix");
            if (cliPrefix.isPresent()) {
                return true;
            } else if (context instanceof ApplicationContext) {
                ApplicationContext appCtx = (ApplicationContext) context;
                String path = getConfigurationPropertiesPath(resolutionContext);
                return appCtx.containsProperties(path);
            }

        }
        return false;
    }

    /**
     * Resolves a bean for the given {@link FieldInjectionPoint}.
     *
     * @param resolutionContext The {@link BeanResolutionContext}
     * @param context           The {@link BeanContext}
     * @param injectionPoint    The {@link FieldReference}
     * @return The resolved bean
     * @throws DependencyInjectionException If the bean cannot be resolved
     */
    @SuppressWarnings("WeakerAccess")
    @Internal
    @UsedByGeneratedCode
    protected final Object getBeanForField(BeanResolutionContext resolutionContext, BeanContext context, FieldReference injectionPoint) {
        final Class beanClass = injectionPoint.fieldType;
        if (beanClass.isArray()) {
            Collection beansOfType = getBeansOfTypeForField(resolutionContext, context, injectionPoint);
            return beansOfType.toArray((Object[]) Array.newInstance(beanClass.getComponentType(), beansOfType.size()));
        } else if (Collection.class.isAssignableFrom(beanClass)) {
            Collection beansOfType = getBeansOfTypeForField(resolutionContext, context, injectionPoint);
            if (beanClass.isInstance(beansOfType)) {
                return beansOfType;
            } else {
                //noinspection unchecked
                return CollectionUtils.convertCollection(beanClass, beansOfType).orElse(null);
            }
        } else if (Stream.class.isAssignableFrom(beanClass)) {
            return getStreamOfTypeForField(resolutionContext, context, injectionPoint);
        } else if (Optional.class.isAssignableFrom(beanClass)) {
            return findBeanForField(resolutionContext, context, injectionPoint);
        } else {
            final Argument argument = injectionPoint.asArgument(environment);
            try (BeanResolutionContext.Path ignored = resolutionContext.getPath()
                    .pushFieldResolve(this, argument, injectionPoint.requiresReflection)) {
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
                        if (injectionPoint.isDeclaredNullable()) {
                            return null;
                        }
                        throw new DependencyInjectionException(resolutionContext, this, injectionPoint.fieldName, e);
                    }
                } catch (NoSuchBeanException e) {
                    if (injectionPoint.isDeclaredNullable()) {
                        return null;
                    }
                    throw new DependencyInjectionException(resolutionContext, this, injectionPoint.fieldName, e);
                }
            }
        }
    }

    /**
     * Obtains a an optional for the field at the given index and the argument at the given index
     * <p>
     * Warning: this method is used by internal generated code and should not be called by user code.
     *
     * @param resolutionContext The resolution context
     * @param context           The context
     * @param injectionPoint    The field injection point
     * @return The resolved bean
     */
    @SuppressWarnings("WeakerAccess")
    @Internal
    protected final Optional findBeanForField(BeanResolutionContext resolutionContext, BeanContext context, FieldReference injectionPoint) {
        return resolveBeanWithGenericsForField(resolutionContext, injectionPoint, (beanType, qualifier) ->
                ((DefaultBeanContext) context).findBean(resolutionContext, beanType, qualifier)
        );
    }

    /**
     * Obtains a bean definition for the field at the given index and the argument at the given index
     * <p>
     * Warning: this method is used by internal generated code and should not be called by user code.
     *
     * @param resolutionContext The resolution context
     * @param context           The context
     * @param injectionPoint    The field injection point
     * @return The resolved bean
     */
    @SuppressWarnings("WeakerAccess")
    @Internal
    protected final Collection getBeansOfTypeForField(BeanResolutionContext resolutionContext, BeanContext context, FieldReference injectionPoint) {
        return resolveBeanWithGenericsForField(resolutionContext, injectionPoint, (beanType, qualifier) -> {
                    boolean hasNoGenerics = !injectionPoint.fieldType.isArray() && injectionPoint.asArgument(environment).getTypeVariables().isEmpty();
                    if (hasNoGenerics) {
                        return ((DefaultBeanContext) context).getBean(resolutionContext, beanType, qualifier);
                    } else {
                        return ((DefaultBeanContext) context).getBeansOfType(resolutionContext, beanType, qualifier);
                    }
                }
        );
    }

    /**
     * Obtains a bean definition for the field at the given index and the argument at the given index
     * <p>
     * Warning: this method is used by internal generated code and should not be called by user code.
     *
     * @param resolutionContext The resolution context
     * @param context           The context
     * @param fieldRef    The field injection point
     * @return The resolved bean
     */
    @SuppressWarnings("WeakerAccess")
    @Internal
    @UsedByGeneratedCode
    protected final Stream getStreamOfTypeForField(BeanResolutionContext resolutionContext, BeanContext context, FieldReference fieldRef) {
        return resolveBeanWithGenericsForField(resolutionContext, fieldRef, (beanType, qualifier) ->
                ((DefaultBeanContext) context).streamOfType(resolutionContext, beanType, qualifier)
        );
    }

    private Object getBeanForMethodArgument(BeanResolutionContext resolutionContext, BeanContext context, MethodReference methodRef, Argument argument) {
        Class argumentType = argument.getType();
        if (argumentType.isArray()) {
            Collection beansOfType = getBeansOfTypeForMethodArgument(resolutionContext, context, methodRef, argument);
            return beansOfType.toArray((Object[]) Array.newInstance(argumentType.getComponentType(), beansOfType.size()));
        } else if (Collection.class.isAssignableFrom(argumentType)) {
            Collection beansOfType = getBeansOfTypeForMethodArgument(resolutionContext, context, methodRef, argument);
            return coerceCollectionToCorrectType(argumentType, beansOfType);
        } else if (Stream.class.isAssignableFrom(argumentType)) {
            return streamOfTypeForMethodArgument(resolutionContext, context, methodRef, argument);
        } else if (Optional.class.isAssignableFrom(argumentType)) {
            return findBeanForMethodArgument(resolutionContext, context, methodRef, argument);
        } else {
            try (BeanResolutionContext.Path ignored = resolutionContext.getPath()
                    .pushMethodArgumentResolve(this, methodRef.methodName, argument, methodRef.arguments, methodRef.requiresReflection)) {
                try {
                    Qualifier qualifier = resolveQualifier(resolutionContext, argument);
                    return ((DefaultBeanContext) context).getBean(resolutionContext, argument, qualifier);
                } catch (DisabledBeanException e) {
                    if (AbstractBeanContextConditional.LOG.isDebugEnabled()) {
                        AbstractBeanContextConditional.LOG.debug("Bean of type [{}] disabled for reason: {}", argumentType.getSimpleName(), e.getMessage());
                    }
                    if (isIterable() && getAnnotationMetadata().hasDeclaredAnnotation(EachBean.class)) {
                        throw new DisabledBeanException("Bean [" + getBeanType().getSimpleName() + "] disabled by parent: " + e.getMessage());
                    } else {
                        if (argument.isDeclaredNullable()) {
                            return null;
                        }
                        throw new DependencyInjectionException(resolutionContext, argument, e);
                    }
                } catch (NoSuchBeanException e) {
                    if (argument.isDeclaredNullable()) {
                        return null;
                    }
                    throw new DependencyInjectionException(resolutionContext, argument, e);
                }
            }
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
            if (!value.isPresent() && isConfigurationProperties()) {
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
            valString = annotationMetadata.stringValue(Property.class, "name")
                    .orElseGet(() ->
                            argument.getAnnotationMetadata().stringValue(Property.class, "name")
                                    .orElseThrow(() ->
                                            new DependencyInjectionException(
                                                    resolutionContext,
                                                    argument,
                                                    "Value resolution attempted but @Value annotation is missing"
                                            )
                                    )
                    );

            valString = substituteWildCards(resolutionContext, valString);
        }
        return valString;
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

    private <B, X extends RuntimeException> B resolveBeanWithGenericsFromMethodArgument(BeanResolutionContext resolutionContext, MethodReference methodRef, Argument argument, BeanResolver<B> beanResolver) throws X {
        try (BeanResolutionContext.Path ignored = resolutionContext.getPath()
                .pushMethodArgumentResolve(this, methodRef.methodName, argument, methodRef.arguments, methodRef.requiresReflection)) {
            try {
                Qualifier qualifier = resolveQualifier(resolutionContext, argument);
                Class argumentType = argument.getType();
                Argument genericType = resolveGenericType(argument, argumentType);
                return (B) beanResolver.resolveBean(genericType != null ? genericType : argument, qualifier);
            } catch (NoSuchBeanException e) {
                throw new DependencyInjectionException(resolutionContext, this, methodRef.methodName, argument, e);
            }
        }
    }

    private Argument resolveGenericType(Argument argument, Class argumentType) {
        Argument genericType;
        if (argument.isArray()) {
            genericType = Argument.of(argumentType.getComponentType());
        } else {
            return argument.getFirstTypeVariable().orElse(null);
        }
        return genericType;
    }

    private <B> B resolveBeanWithGenericsFromConstructorArgument(BeanResolutionContext resolutionContext, Argument argument, BeanResolver<B> beanResolver) {
        try (BeanResolutionContext.Path ignored = resolutionContext.getPath().pushConstructorResolve(this, argument)) {
            try {
                Class argumentType = argument.getType();
                Argument genericType = resolveGenericType(argument, argumentType);
                Qualifier qualifier = resolveQualifier(resolutionContext, argument);
                return (B) beanResolver.resolveBean(genericType != null ? genericType : argument, qualifier);
            } catch (NoSuchBeanException e) {
                if (argument.isNullable()) {
                    return null;
                }
                throw new DependencyInjectionException(resolutionContext, argument, e);
            }
        }
    }

    private <B> Collection<BeanRegistration<B>> resolveBeanRegistrationsWithGenericsFromArgument(
            BeanResolutionContext resolutionContext,
            Argument<?> argument,
            BiFunction<Argument<B>, Qualifier<B>, Collection<BeanRegistration<B>>> beanResolver) {
        try {
            final Supplier<DependencyInjectionException> errorSupplier = () ->
                    new DependencyInjectionException(resolutionContext, argument, "Cannot resolve bean registrations. Argument [" + argument + "] missing generic type information.");
            Argument<?> genericType = argument.getFirstTypeVariable().orElseThrow(errorSupplier);
            Argument beanType = argument.isArray() ? genericType : genericType.getFirstTypeVariable().orElseThrow(errorSupplier);
            Qualifier qualifier = resolveQualifier(resolutionContext, argument);
            return beanResolver.apply(beanType, qualifier);
        } catch (NoSuchBeanException e) {
            if (argument.isNullable()) {
                return null;
            }
            throw new DependencyInjectionException(resolutionContext, argument, e);
        }
    }

    private Argument<?> resolveArgument(BeanContext context, int argIndex, Argument<?>[] arguments) {
        Argument<?> argument = arguments[argIndex];
        if (argument instanceof DefaultArgument) {
            if (argument.getAnnotationMetadata().hasPropertyExpressions()) {
                argument = new EnvironmentAwareArgument<>((DefaultArgument) argument);
                instrumentAnnotationMetadata(context, argument);
            }
        }
        return argument;
    }

    private <B> BeanRegistration<B> resolveBeanRegistrationWithGenericsFromArgument(
            BeanResolutionContext resolutionContext,
            Argument<?> argument,
            BiFunction<Argument<B>, Qualifier<B>, BeanRegistration<B>> beanResolver) {
        try {
            final Supplier<DependencyInjectionException> errorSupplier = () ->
                    new DependencyInjectionException(resolutionContext, argument, "Cannot resolve bean registration. Argument [" + argument + "] missing generic type information.");
            Argument genericType = argument.getFirstTypeVariable().orElseThrow(errorSupplier);
            Qualifier qualifier = resolveQualifier(resolutionContext, argument);
            return beanResolver.apply(genericType, qualifier);
        } catch (NoSuchBeanException e) {
            if (argument.isNullable()) {
                return null;
            }
            throw new DependencyInjectionException(resolutionContext, argument, e);
        }
    }

    private Object doResolveBeanRegistrations(BeanResolutionContext resolutionContext, DefaultBeanContext context, Argument<?> argument) {
        final Collection<BeanRegistration<Object>> beanRegistrations = resolveBeanRegistrationsWithGenericsFromArgument(resolutionContext, argument,
                (beanType, qualifier) -> context.getBeanRegistrations(resolutionContext, beanType, qualifier)
        );
        if (argument.isArray()) {
            return beanRegistrations.toArray(new BeanRegistration[beanRegistrations.size()]);
        } else {
            return coerceCollectionToCorrectType(argument.getType(), beanRegistrations);
        }
    }

    private <B> B resolveBeanWithGenericsForField(BeanResolutionContext resolutionContext, FieldReference injectionPoint, BeanResolver<B> beanResolver) {
        Argument argument = injectionPoint.asArgument(environment);
        try (BeanResolutionContext.Path ignored = resolutionContext.getPath()
                .pushFieldResolve(this, argument, injectionPoint.requiresReflection)) {
            try {
                Argument genericType = argument.isArray() ? Argument.of(argument.getType().getComponentType()) : argument.getFirstTypeVariable().orElse(argument);
                Qualifier qualifier = resolveQualifier(resolutionContext, argument);
                return (B) beanResolver.resolveBean(genericType, qualifier);
            } catch (NoSuchBeanException e) {
                if (argument.isNullable()) {
                    return null;
                }
                throw new DependencyInjectionException(resolutionContext, this, injectionPoint.fieldName, e);
            }
        }
    }

    private boolean isConfigurationProperties() {
        return isConfigurationProperties;
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

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
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
    private Object coerceCollectionToCorrectType(Class collectionType, Collection beansOfType) {
        if (collectionType.isInstance(beansOfType)) {
            return beansOfType;
        } else {
            return CollectionUtils.convertCollection(collectionType, beansOfType).orElse(null);
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

    public static final class MethodReference extends MethodOrFieldReference {
        final String methodName;
        @Nullable
        final Argument[] arguments;
        @Nullable
        final boolean isPreDestroyMethod;
        final boolean isPostConstructMethod;

        public MethodReference(Class declaringType,
                                  String methodName,
                                  @Nullable Argument[] arguments,
                                  @Nullable AnnotationMetadata annotationMetadata,
                                  boolean requiresReflection) {
            this(declaringType, methodName, arguments, annotationMetadata, requiresReflection, false, false);
        }

        public MethodReference(Class declaringType,
                                  String methodName,
                                  @Nullable Argument[] arguments,
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

    public static final class FieldReference extends MethodOrFieldReference {
        final Class fieldType;
        final String fieldName;
        final Argument[] typeArguments;

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

    /**
     * Class used as a method key.
     */
    public static final class MethodKey {
        final String name;
        final Class[] argumentTypes;

        MethodKey(String name, Class[] argumentTypes) {
            this.name = name;
            this.argumentTypes = argumentTypes;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            @SuppressWarnings("unchecked") AbstractBeanDefinition2.MethodKey methodKey = (AbstractBeanDefinition2.MethodKey) o;

            if (!name.equals(methodKey.name)) {
                return false;
            }
            return Arrays.equals(argumentTypes, methodKey.argumentTypes);
        }

        @Override
        public int hashCode() {
            int result = name.hashCode();
            result = 31 * result + Arrays.hashCode(argumentTypes);
            return result;
        }
    }


    /**
     * Bean resolver.
     *
     * @param <T> The type
     */
    private interface BeanResolver<T> {
        T resolveBean(Argument<T> beanType, Qualifier<T> qualifier);
    }
}
